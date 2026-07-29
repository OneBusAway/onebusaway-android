#!/usr/bin/env python3
"""Fetch real route shapes for the destination-reminder replay fixtures.

Each `nav_trip*.csv` fixture's first line names the trip it was recorded on, plus the
penultimate and destination stop ids. This asks the relevant OneBusAway deployment for that
trip's shape and its per-stop `distanceAlongTrip`, and writes a `nav_trip*.shape.json`
sidecar that `ReminderTraceReplayTest` picks up to replay the trace through the engine's
along-the-route coordinate instead of straight-line distances.

Run once, offline, and commit the output; the tests never touch the network.

    python3 tools/fetch-reminder-shapes.py [--force]

GTFS trip ids churn with every service change, so a fixture whose trip no longer exists simply
gets no sidecar and keeps replaying against straight-line distances. That is expected, not a
failure — the summary reports the split.

STATUS as of 2026-07-29: **0 of 56 fixtures resolve.** The endpoints themselves are healthy
(`agencies-with-coverage` returns 200 and live trip ids resolve), so this is the data, not the
tooling:

  * Tampa (38 HART + 17 USF Bull Runner fixtures) has since been re-keyed. Its agency ids are
    now numeric (`1`, `2`), whereas every fixture trip id is prefixed with the full agency name
    (`Hillsborough Area Regional Transit_144477`). Those ids cannot resolve on the current
    deployment, and the old host (api.tampa.onebusaway.org) no longer resolves in DNS.
  * San Diego (1 fixture, recorded April 2025) still uses the `MTS_` prefix, but that specific
    trip id has already churned out of the feed.

So the replay suite continues to exercise the straight-line fallback, which remains a real
shipped path. Coverage for the along-the-route coordinate comes from the synthetic geometry
tests in ReminderEngineTest instead — including a hooked route that establishes progress along
a shape and fails to on straight-line distances, which is the regression this all exists for.

Keep this script: it is correct and will work for any fixture recorded against a current feed.
"""
from __future__ import annotations

import argparse
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# OneBusAway's publicly documented shared Android client key — not a secret, but every run spends
# quota against the key all OBA Android users share. Override it with your own for repeated runs:
#   OBA_API_KEY=... python3 tools/fetch-reminder-shapes.py
API_KEY = os.environ.get("OBA_API_KEY", "v1_BktoDJ2gJlu6nLM6LsT9H8IUbWc=cGF1bGN3YXR0c0BnbWFpbC5jb20=")

# Agency id prefix (from the trip id) -> that agency's OBA deployment.
BASE_URLS = {
    "Hillsborough Area Regional Transit": "https://api.tampa.onebusawaycloud.com/",
    "USF Bull Runner": "https://api.tampa.onebusawaycloud.com/",
    "MTS": "https://realtime.sdmts.com/api/",
}

FIXTURES = pathlib.Path("onebusaway-android/src/test/resources")
REQUEST_TIMEOUT_SECONDS = 20
PAUSE_BETWEEN_REQUESTS_SECONDS = 0.2


def api_get(base_url: str, path: str) -> dict | None:
    url = f"{base_url.rstrip('/')}/{path}?{urllib.parse.urlencode({'key': API_KEY})}"
    try:
        with urllib.request.urlopen(url, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            payload = json.load(response)
    except (urllib.error.URLError, json.JSONDecodeError, TimeoutError) as error:
        print(f"    request failed: {error}", file=sys.stderr)
        return None
    if payload.get("code") != 200:
        print(f"    api returned code {payload.get('code')}", file=sys.stderr)
        return None
    return payload.get("data")


def base_url_for(trip_id: str) -> str | None:
    for prefix, base in BASE_URLS.items():
        if trip_id.startswith(prefix + "_"):
            return base
    return None


def shape_for(trip_id: str, penultimate_id: str, alight_id: str) -> dict | None:
    base = base_url_for(trip_id)
    if base is None:
        print(f"    no known deployment for {trip_id}", file=sys.stderr)
        return None

    details = api_get(base, f"api/where/trip-details/{urllib.parse.quote(trip_id)}.json")
    if details is None:
        return None
    entry = details.get("entry", {})
    references = details.get("references", {})

    trip = next((t for t in references.get("trips", []) if t.get("id") == trip_id), None)
    shape_id = (trip or {}).get("shapeId")
    if not shape_id:
        print("    trip carries no shapeId", file=sys.stderr)
        return None

    stop_times = entry.get("schedule", {}).get("stopTimes", [])
    offsets = {st["stopId"]: st.get("distanceAlongTrip") for st in stop_times}
    penultimate_offset = offsets.get(penultimate_id)
    alight_offset = offsets.get(alight_id)
    if penultimate_offset is None or alight_offset is None:
        print("    trip does not serve both recorded stops", file=sys.stderr)
        return None
    if alight_offset <= penultimate_offset:
        print("    recorded stops are not in trip order", file=sys.stderr)
        return None

    shape = api_get(base, f"api/where/shape/{urllib.parse.quote(shape_id)}.json")
    if shape is None:
        return None
    points = shape.get("entry", {}).get("points")
    length = shape.get("entry", {}).get("length")
    if not points or not length:
        print("    shape has no points", file=sys.stderr)
        return None

    return {
        "tripId": trip_id,
        "shapeId": shape_id,
        "encodedPoints": points,
        "pointCount": length,
        "penultimateOffsetMeters": penultimate_offset,
        "alightOffsetMeters": alight_offset,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--force", action="store_true", help="refetch fixtures that already have a sidecar")
    args = parser.parse_args()

    if not FIXTURES.is_dir():
        print(f"Run from the repository root; {FIXTURES} not found.", file=sys.stderr)
        return 2

    resolved, skipped, unavailable = [], [], []
    for csv in sorted(FIXTURES.glob("nav_trip*.csv")):
        sidecar = csv.with_suffix(".shape.json")
        if sidecar.exists() and not args.force:
            skipped.append(csv.name)
            continue

        header = csv.read_text(encoding="utf-8", errors="replace").splitlines()[0].split(",")
        trip_id, alight_id, penultimate_id = header[0], header[1], header[4]
        print(f"{csv.name}: {trip_id}")

        shape = shape_for(trip_id, penultimate_id, alight_id)
        time.sleep(PAUSE_BETWEEN_REQUESTS_SECONDS)
        if shape is None:
            unavailable.append(csv.name)
            continue

        sidecar.write_text(json.dumps(shape, indent=2) + "\n", encoding="utf-8")
        resolved.append(csv.name)
        print(f"    wrote {sidecar.name} ({shape['pointCount']} points)")

    total = len(resolved) + len(skipped) + len(unavailable)
    print(
        f"\n{len(resolved)} resolved, {len(skipped)} already present, "
        f"{len(unavailable)} unavailable, of {total} fixtures."
    )
    if unavailable:
        print("Unavailable (these keep replaying against straight-line distances):")
        for name in unavailable:
            print(f"  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
