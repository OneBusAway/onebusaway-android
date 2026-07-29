#!/usr/bin/env python3
"""Generate destination-reminder replay fixtures from real, current route geometry.

The 56 recorded `nav_trip*.csv` fixtures are real rider GPS but their trips have churned out of
every feed, so no shape can be fetched for them (see fetch-reminder-shapes.py) and they can only
exercise the engine's straight-line fallback. This produces fixtures for the other path: a
simulated vehicle traversing a **real, currently-published** route shape, with per-stop offsets
taken from the server's own `distanceAlongTrip`.

    python3 tools/generate-reminder-fixtures.py [--limit N] [--seed N]

WHAT IS REAL HERE AND WHAT IS NOT. The route geometry, the stop positions, the stop spacing and
the along-route offsets are all real and current — which is the point, because those are what the
hand-built straight-line test geometry cannot supply: genuine curves, loops, one-way pairs, and
stops that sit off the centreline. The vehicle's motion and the GPS error are simulated. So these
fixtures prove the along-the-route coordinate works on real transit geometry; they do not prove
anything about real GPS pathology in a specific place (tunnel dropouts, urban-canyon multipath),
which remains what the recorded traces are uniquely good for.

The noise model is measured, not invented. Every constant under MEASURED below was derived from
the 32,961 samples in the existing recorded fixtures; the derivation is noted per constant so it
can be re-checked.

Output goes to onebusaway-android/src/test/resources:
  * `nav_generated_<slug>.csv`         — the fixture, in the same 13-column format
  * `nav_generated_<slug>.shape.json`  — the shape sidecar, same format fetch-reminder-shapes.py writes
  * `generated-fixtures.txt`           — the manifest ReminderTraceReplayTest reads
"""
from __future__ import annotations

import argparse
import json
import math
import pathlib
import random
import sys
import urllib.error
import urllib.parse
import urllib.request

API_KEY = "v1_BktoDJ2gJlu6nLM6LsT9H8IUbWc=cGF1bGN3YXR0c0BnbWFpbC5jb20="
DEPLOYMENTS = [
    ("MTS", "https://realtime.sdmts.com/api/"),
    ("1", "https://api.tampa.onebusawaycloud.com/"),
]
FIXTURES = pathlib.Path("onebusaway-android/src/test/resources")
MANIFEST = FIXTURES / "generated-fixtures.txt"

# --- MEASURED from the 56 recorded fixtures (32,961 samples) ------------------------------------
# Sample cadence: median 1.00 s, p10 0.98, p90 1.01 — a tight 1 Hz.
SAMPLE_INTERVAL_SECONDS = 1.0
# 42.6% of consecutive samples repeat the previous timestamp verbatim (the platform re-delivering
# the same fix). The engine's stale-timestamp guard must keep eating them.
DUPLICATE_SAMPLE_RATE = 0.426
# Provider mix: gps 77%, fused 22%, network <1%.
PROVIDER_MIX = [("gps", 0.77), ("fused", 0.22), ("network", 0.01)]
# Reported accuracy per provider, as (p10, median, p90) in metres.
ACCURACY_BY_PROVIDER = {"gps": (3, 8, 14), "fused": (4, 5, 11), "network": (23, 36, 42)}
# Interruptions: 0.15% of intervals exceed 5 s, 0.05% exceed 30 s.
GAP_RATE = 0.0015
GAP_SECONDS = (6, 45)
# -------------------------------------------------------------------------------------------------

# Android reports accuracy as a 68th-percentile radius, so for a 2-D Gaussian error
# r68 = sigma * sqrt(-2 ln(1 - 0.68)) ~= 1.51 sigma. Invert that to place the simulated fix.
ACCURACY_TO_SIGMA = 1.0 / 1.51

DWELL_SECONDS = (8, 25)
CRUISE_SPEED_MPS = 11.0
ACCELERATION_MPS2 = 1.1
# Keeps a fixture comparable in size to the recorded ones (the largest is 2,765 rows) while still
# covering a realistic reminder session.
MAX_RIDE_METERS = 12_000
EARTH_RADIUS_METERS = 6371010.0


# --- geometry -----------------------------------------------------------------------------------

def haversine(a, b):
    lat1, lat2 = math.radians(a[0]), math.radians(b[0])
    dlon = math.radians(b[1] - a[1])
    y = math.sqrt((math.cos(lat2) * math.sin(dlon)) ** 2 +
                  (math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(dlon)) ** 2)
    x = math.sin(lat1) * math.sin(lat2) + math.cos(lat1) * math.cos(lat2) * math.cos(dlon)
    return EARTH_RADIUS_METERS * math.atan2(y, x)


def decode_polyline(encoded):
    points, lat, lon, i = [], 0, 0, 0
    while i < len(encoded):
        for axis in range(2):
            shift = result = 0
            while True:
                byte = ord(encoded[i]) - 63
                i += 1
                result |= (byte & 0x1F) << shift
                shift += 5
                if byte < 0x20:
                    break
            delta = ~(result >> 1) if result & 1 else result >> 1
            if axis == 0:
                lat += delta
            else:
                lon += delta
        points.append((lat / 1e5, lon / 1e5))
    return points


def cumulative(points):
    out = [0.0]
    for a, b in zip(points, points[1:]):
        out.append(out[-1] + haversine(a, b))
    return out


def interpolate(points, cumulative_distances, distance):
    """The position at `distance` metres along the polyline, clamped to its ends."""
    if distance <= 0:
        return points[0]
    if distance >= cumulative_distances[-1]:
        return points[-1]
    low, high = 0, len(cumulative_distances) - 1
    while low + 1 < high:
        mid = (low + high) // 2
        if cumulative_distances[mid] <= distance:
            low = mid
        else:
            high = mid
    span = cumulative_distances[low + 1] - cumulative_distances[low]
    t = 0.0 if span <= 0 else (distance - cumulative_distances[low]) / span
    a, b = points[low], points[low + 1]
    return (a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]))


# --- api ----------------------------------------------------------------------------------------

def api_get(base_url, path, **params):
    params["key"] = API_KEY
    url = f"{base_url.rstrip('/')}/{path}?{urllib.parse.urlencode(params)}"
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            payload = json.load(response)
    except (urllib.error.URLError, json.JSONDecodeError, TimeoutError) as error:
        print(f"    request failed: {error}", file=sys.stderr)
        return None
    return payload.get("data") if payload.get("code") == 200 else None


def candidate_trips(agency_id, base_url, wanted):
    """Real trips currently running, with their shape and per-stop offsets resolved."""
    routes = api_get(base_url, f"api/where/routes-for-agency/{urllib.parse.quote(agency_id)}.json")
    found = []
    for route in (routes or {}).get("list", []):
        if len(found) >= wanted:
            break
        listing = api_get(base_url, f"api/where/trips-for-route/{urllib.parse.quote(route['id'])}.json")
        trip_ids = [t.get("tripId") for t in (listing or {}).get("list", [])][:1]
        for trip_id in filter(None, trip_ids):
            resolved = resolve_trip(base_url, trip_id)
            if resolved:
                resolved["routeName"] = route.get("shortName") or route.get("id")
                found.append(resolved)
                print(f"    {trip_id} on route {resolved['routeName']}: "
                      f"{len(resolved['stops'])} stops, {resolved['length']:.0f} m, "
                      f"sinuosity {resolved['sinuosity']:.2f}"
                      f"{', loop' if resolved['isLoop'] else ''}")
                break
    return found


def resolve_trip(base_url, trip_id):
    details = api_get(base_url, f"api/where/trip-details/{urllib.parse.quote(trip_id)}.json")
    if not details:
        return None
    trip = next((t for t in details.get("references", {}).get("trips", []) if t.get("id") == trip_id), None)
    shape_id = (trip or {}).get("shapeId")
    stop_times = details.get("entry", {}).get("schedule", {}).get("stopTimes", [])
    if not shape_id or len(stop_times) < 3:
        return None

    shape = api_get(base_url, f"api/where/shape/{urllib.parse.quote(shape_id)}.json")
    encoded = (shape or {}).get("entry", {}).get("points")
    if not encoded:
        return None
    points = decode_polyline(encoded)
    if len(points) < 2:
        return None
    distances = cumulative(points)
    if distances[-1] < 1000:
        return None

    stops_by_id = {s["id"]: s for s in details.get("references", {}).get("stops", [])}
    stops = []
    for stop_time in stop_times:
        stop = stops_by_id.get(stop_time.get("stopId"))
        offset = stop_time.get("distanceAlongTrip")
        if stop and offset is not None and stop.get("lat") is not None:
            stops.append({"id": stop["id"], "lat": stop["lat"], "lon": stop["lon"], "offset": offset})
    if len(stops) < 3 or stops[-1]["offset"] <= stops[0]["offset"]:
        return None

    end_to_end = haversine(points[0], points[-1])
    return {
        "tripId": trip_id,
        "shapeId": shape_id,
        "encodedPoints": encoded,
        "pointCount": len(points),
        "points": points,
        "cumulative": distances,
        "length": distances[-1],
        "stops": stops,
        "sinuosity": distances[-1] / end_to_end if end_to_end > 1 else float("inf"),
        "isLoop": end_to_end < 500,
    }


def pick_spread(trips):
    """A curvy one, a loop, and a long one — the geometries straight-line tests cannot express."""
    chosen, seen = [], set()

    def take(trip, label):
        if trip and trip["tripId"] not in seen:
            seen.add(trip["tripId"])
            chosen.append((label, trip))

    finite = [t for t in trips if math.isfinite(t["sinuosity"])]
    take(max(finite, key=lambda t: t["sinuosity"], default=None), "curvy")
    take(next((t for t in trips if t["isLoop"]), None), "loop")
    take(max(trips, key=lambda t: t["length"], default=None), "long")
    return chosen


# --- simulation ---------------------------------------------------------------------------------

def ride_window(stops):
    """A realistic ride within the trip: from its first stop up to [MAX_RIDE_METERS] along.

    Whole trips run to an hour, which would make a fixture an order of magnitude larger than any
    recorded one for no extra coverage — the geometry is just as real over a 20-minute window.
    """
    start = stops[0]["offset"]
    within = [s for s in stops if s["offset"] - start <= MAX_RIDE_METERS]
    return within if len(within) >= 3 else stops[:3]


def simulate(trip, rng):
    """Rows of (elapsed_nanos, lat, lon, speed, bearing, accuracy, provider)."""
    stops = ride_window(trip["stops"])
    board, alight = stops[0], stops[-1]
    upcoming = [s["offset"] for s in stops[1:]]

    rows = []
    distance = board["offset"]
    speed = 0.0
    elapsed = 0.0
    dwell_remaining = rng.uniform(*DWELL_SECONDS)
    served = set()

    while distance < alight["offset"] and elapsed < 2 * 3600:
        if dwell_remaining > 0:
            # Parked at a stop. Velocity is carried explicitly rather than derived from position,
            # so a vehicle halted *at* a stop can still pull away from it.
            speed = 0.0
            dwell_remaining -= SAMPLE_INTERVAL_SECONDS
        else:
            ahead = [offset for offset in upcoming if offset > distance]
            to_next = (min(ahead) if ahead else alight["offset"]) - distance
            # v = sqrt(2 a d) is the fastest one can still stop within d, which is the envelope the
            # vehicle has to stay under as it approaches the next stop.
            speed = min(CRUISE_SPEED_MPS,
                        speed + ACCELERATION_MPS2 * SAMPLE_INTERVAL_SECONDS,
                        math.sqrt(max(0.0, 2 * ACCELERATION_MPS2 * to_next)))
            distance += speed * SAMPLE_INTERVAL_SECONDS
            for offset in upcoming[:-1]:
                if offset not in served and distance >= offset:
                    served.add(offset)
                    dwell_remaining = rng.uniform(*DWELL_SECONDS)

        truth = interpolate(trip["points"], trip["cumulative"], distance)
        ahead = interpolate(trip["points"], trip["cumulative"], min(distance + 10, trip["length"]))
        bearing = (math.degrees(math.atan2(
            math.sin(math.radians(ahead[1] - truth[1])) * math.cos(math.radians(ahead[0])),
            math.cos(math.radians(truth[0])) * math.sin(math.radians(ahead[0])) -
            math.sin(math.radians(truth[0])) * math.cos(math.radians(ahead[0])) *
            math.cos(math.radians(ahead[1] - truth[1])))) + 360) % 360

        provider = rng.choices([p for p, _ in PROVIDER_MIX], [w for _, w in PROVIDER_MIX])[0]
        low, median, high = ACCURACY_BY_PROVIDER[provider]
        accuracy = max(1.0, rng.triangular(low, high, median))
        sigma = accuracy * ACCURACY_TO_SIGMA
        # Displace the true position by a 2-D Gaussian of that sigma.
        north = rng.gauss(0, sigma)
        east = rng.gauss(0, sigma)
        observed = (truth[0] + north / 111_320.0,
                    truth[1] + east / (111_320.0 * math.cos(math.radians(truth[0]))))

        rows.append((int(elapsed * 1e9), observed[0], observed[1], speed, bearing, accuracy, provider))

        # The platform re-delivers the same fix more often than not.
        if rng.random() < DUPLICATE_SAMPLE_RATE:
            rows.append(rows[-1])

        elapsed += SAMPLE_INTERVAL_SECONDS
        if rng.random() < GAP_RATE:
            elapsed += rng.uniform(*GAP_SECONDS)

    return rows


def write_fixture(slug, trip, rows):
    # The same window the simulation ran, so the sidecar's offsets describe the ride in the CSV.
    stops = ride_window(trip["stops"])
    board, penultimate, alight = stops[0], stops[-2], stops[-1]
    csv_path = FIXTURES / f"nav_generated_{slug}.csv"
    with csv_path.open("w", encoding="utf-8") as out:
        out.write(f"{trip['tripId']},{alight['id']},{alight['lat']},{alight['lon']},"
                  f"{penultimate['id']},{penultimate['lat']},{penultimate['lon']}\n")
        for index, (nanos, lat, lon, speed, bearing, accuracy, provider) in enumerate(rows):
            out.write(f"{index},FALSE,FALSE,{nanos},0,{lat:.6f},{lon:.6f},0,"
                      f"{speed:.6f},{bearing:.6f},{accuracy:.6f},0,{provider}\n")

    # Field names are exactly ReminderShape's, so the replay test deserialises straight into it
    # rather than maintaining a mirror of this schema. Provenance is nested under a key the test's
    # `ignoreUnknownKeys` drops.
    sidecar = {
        "encodedPoints": trip["encodedPoints"],
        "pointCount": trip["pointCount"],
        # These fixtures model a legacy single-ride session, which carries no boarding stop.
        "boardOffsetMeters": None,
        "penultimateOffsetMeters": penultimate["offset"],
        "alightOffsetMeters": alight["offset"],
        "provenance": {
            "tripId": trip["tripId"],
            "shapeId": trip["shapeId"],
            "routeName": trip.get("routeName"),
            "boardStopId": board["id"],
            "shapeLengthMeters": round(trip["length"], 1),
            "sinuosity": round(trip["sinuosity"], 3),
            "isLoop": trip["isLoop"],
            "note": "Real route geometry and stop offsets; simulated vehicle motion and GPS error.",
        },
    }
    (FIXTURES / f"nav_generated_{slug}.shape.json").write_text(
        json.dumps(sidecar, indent=2) + "\n", encoding="utf-8")
    return csv_path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit", type=int, default=12, help="candidate trips to inspect per deployment")
    parser.add_argument("--seed", type=int, default=20260729, help="seed, so output is reproducible")
    args = parser.parse_args()

    if not FIXTURES.is_dir():
        print(f"Run from the repository root; {FIXTURES} not found.", file=sys.stderr)
        return 2

    trips = []
    for agency_id, base_url in DEPLOYMENTS:
        print(f"{base_url} (agency {agency_id}):")
        trips.extend(candidate_trips(agency_id, base_url, args.limit))
    if not trips:
        print("No live trips resolved; nothing generated.", file=sys.stderr)
        return 1

    rng = random.Random(args.seed)
    written = []
    for label, trip in pick_spread(trips):
        rows = simulate(trip, rng)
        if len(rows) < 60:
            print(f"  {label}: too few samples ({len(rows)}); skipping", file=sys.stderr)
            continue
        path = write_fixture(label, trip, rows)
        written.append(f"nav_generated_{label}")
        print(f"  {label}: {path.name}, {len(rows)} samples over {trip['length']:.0f} m "
              f"({trip['tripId']})")

    MANIFEST.write_text("".join(name + "\n" for name in sorted(written)), encoding="utf-8")
    print(f"\nWrote {len(written)} fixtures and {MANIFEST.name}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
