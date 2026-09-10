#!/usr/bin/env python3
#
# Copyright (C) 2026 Open Transit Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Ask every region's trip planner for a plan, the way the app would (issue #2264).

Trip planning is the one app feature whose server is *per region* and published by a third party,
so it can be missing or dead for one region while every other feature there works -- and neither
state is visible from the code. #2264 was exactly that: Washington, D.C. publishes no OTP server at
all, and the app answered a trip-plan attempt there with "No region selected" while D.C. sat
plainly selected.

This walks the region directory (bundled by default, since that is what a first-launch device
reads) and, for each region, reports one of:

  * no planner       -- the region publishes neither otpBaseUrl nor otpBaseGraphqlUrl. The app must
                        not offer trip planning there at all; see tripPlanningUnavailableMessage.
  * plans            -- the server answered a real plan request.
  * no route         -- the server answered, but found no itinerary between the probe points. Not a
                        failure of the *server*; the probe points are synthetic (see below).
  * unreachable/err  -- DNS, TLS, timeout, a non-2xx, or a 200 that isn't a planner's answer at
                        all. Trip planning is broken for that region.
  * not probed       -- the region publishes a planner but no bounds this tool can plan between, so
                        it has *no* verdict. Reported, never counted as an answer.

Endpoint construction deliberately mirrors the app: `otpPlanUrl` for OTP1 (a `/routers/default`
base gets only `/plan` appended, a server-root base gets the segment inserted, and a pre-1.0 server
is retried at the bare `/plan`) and `otp2GraphQlEndpoint` for OTP2 (`otpBaseGraphqlUrl` + `/gtfs/v1`,
which is also the signal that a region speaks OTP2 at all). Probing a URL the app would never build
would prove nothing about the app.

The probe points are the centre of the region's first bounding box and a point a short way
north-east of it. That is enough to tell a working planner from a dead host, which is what this
checks; it is not a coverage test, so "no route" is reported, not failed on.

Network-bound and dependent on servers this project does not run, so it is an **on-demand** tool
rather than a CI check: a third party's outage should not turn the nightly red. Run it when trip
planning is reported broken somewhere, and when a region is added or its OTP config changes.

Usage (from the repo root):
    tools/check-region-routing.py                 # the bundled regions file
    tools/check-region-routing.py --live          # the live regions directory instead
    tools/check-region-routing.py --regions f.json

Exit status: 0 every region with a planner answered, 1 at least one planner is broken, 2 nothing is
broken but the run is not a complete answer -- a region could not be probed, or the check itself
could not run at all.
"""

import argparse
import datetime
import json
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ElementTree
from pathlib import Path

# Mirrors RegionsClient (and check-regions-drift.py): the bundled file is what a first-launch device
# reads, and the directory URL comes from the string resource rather than being restated here.
BUNDLED_REGIONS = "onebusaway-android/src/main/res/raw/regions_v3.json"
REGIONS_URL_RESOURCE = "onebusaway-android/src/main/res/values/donottranslate.xml"
REGIONS_URL_RESOURCE_NAME = "regions_api_url"
USER_AGENT = "onebusaway-android region-routing-check (+https://github.com/OneBusAway/onebusaway-android)"

# TripPlanRepository.OTP_ROUTERS_SEGMENT / OTP_PLAN_LOCATION, and Otp2Planner.OTP2_GTFS_GRAPHQL_PATH.
OTP_ROUTERS_SEGMENT = "/routers/default"
OTP_PLAN_LOCATION = "/plan"
OTP2_GTFS_GRAPHQL_PATH = "/gtfs/v1"

TIMEOUT_SECONDS = 45
# Built once: create_default_context() reads the platform trust store from disk, and a run opens
# several connections -- one or two planner requests per region (see probe_otp1), plus the region
# directory itself under --live.
SSL_CONTEXT = ssl.create_default_context()
# How far north-east of a region's centre the destination probe point sits, in degrees -- roughly
# 2km, far enough to want transit and close enough to stay inside the smallest region's box.
PROBE_OFFSET_DEGREES = 0.02

# The OTP2 equivalent of the OTP1 /plan probe: the same planConnection root the app's Plan.graphql
# query uses, cut down to the one field needed to tell "it planned" from "it didn't".
OTP2_PROBE_QUERY = """
query Probe(
  $origin: PlanLabeledLocationInput!
  $destination: PlanLabeledLocationInput!
  $dateTime: PlanDateTimeInput
) {
  planConnection(origin: $origin, destination: $destination, dateTime: $dateTime, first: 3) {
    routingErrors { code description }
    edges { node { duration } }
  }
}
"""


class CheckError(Exception):
    """The check could not run (bad input, unreadable resource) -- distinct from a broken planner."""


class UnusableUrl(CheckError):
    """A URL this tool won't open. Reaching a *region's* planner this way is that region's verdict."""


def regions_directory_url(resource_path):
    """The live regions directory URL, read from the `regions_api_url` string resource."""
    path = Path(resource_path)
    if not path.is_file():
        raise CheckError(f"{resource_path} not found (run from the repo root)")
    for string in ElementTree.parse(path).getroot().iter("string"):
        if string.get("name") == REGIONS_URL_RESOURCE_NAME:
            # Not checked for a scheme here: fetch() is the one gate on what this tool opens.
            return (string.text or "").strip()
    raise CheckError(f'no <string name="{REGIONS_URL_RESOURCE_NAME}"> in {resource_path}')


def fetch(url, data=None, content_type=None):
    """GET (or POST, with `data`) `url`, returning (status, body-text). Raises on transport failure."""
    # urlopen also speaks file: and ftp:, and every URL that reaches here -- the planner endpoints
    # most of all -- comes from a third party's region directory rather than from this repo.
    scheme = urllib.parse.urlsplit(url).scheme
    if scheme not in ("http", "https"):
        raise UnusableUrl(f"refusing to open a {scheme or 'scheme-less'} URL: {url!r}")
    headers = {"User-Agent": USER_AGENT}
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS, context=SSL_CONTEXT) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def load_regions(source, live):
    """The region list from `source` -- a local path, or the live directory when `live`."""
    if live:
        status, text = fetch(source)
        # Report the status rather than letting an error page fall through to "not valid JSON",
        # which is what a 403 from the directory actually looks like.
        if status != 200:
            raise CheckError(f"{source} returned HTTP {status}")
    else:
        path = Path(source)
        if not path.is_file():
            raise CheckError(f"{source} not found (run from the repo root)")
        text = path.read_text(encoding="utf-8")
    try:
        payload = json.loads(text)
    except json.JSONDecodeError as e:
        raise CheckError(f"{source} is not valid JSON: {e}") from e
    regions = (payload.get("data") or {}).get("list")
    if not isinstance(regions, list):
        raise CheckError(f"{source} has no data.list array")
    return regions


def probe_points(region):
    """((lat, lon), (lat, lon)) to plan between, or None when the region's bounds can't be probed.

    Every field is required rather than defaulted: a missing span would clamp the offset to zero and
    ask the planner to route from a point to itself, which every planner answers "no route" to -- a
    verdict about this tool's arithmetic, not about the server. No points means no verdict.
    """
    bounds = region.get("bounds") or []
    if not bounds or not isinstance(bounds[0], dict):
        return None
    box = bounds[0]
    try:
        lat, lon, lat_span, lon_span = (float(box[key]) for key in ("lat", "lon", "latSpan", "lonSpan"))
    except (KeyError, TypeError, ValueError):
        return None
    if lat_span <= 0 or lon_span <= 0:
        return None
    # Clamped to a quarter of the box so the destination stays inside even a small region.
    offset_lat = min(PROBE_OFFSET_DEGREES, lat_span / 4)
    offset_lon = min(PROBE_OFFSET_DEGREES, lon_span / 4)
    return (lat, lon), (lat + offset_lat, lon + offset_lon)


def otp_plan_url(base_url, query, old_server):
    """TripPlanRepository.otpPlanUrl, verbatim: which /plan path this base URL's server exposes."""
    base = base_url.rstrip("/")
    if OTP_ROUTERS_SEGMENT in base or old_server:
        return base + OTP_PLAN_LOCATION + query
    return base + OTP_ROUTERS_SEGMENT + OTP_PLAN_LOCATION + query


def probe_otp1(base_url, origin, destination, when):
    """Plan over OTP1 REST. Returns (ok, summary)."""
    parameters = {
        # OTP1 places are "lat,lon" text; %g keeps them short without losing probe precision.
        "fromPlace": "%g,%g" % origin,
        "toPlace": "%g,%g" % destination,
        "optimize": "QUICK",
        "wheelchair": "false",
        "arriveBy": "false",
        "date": when.strftime("%m-%d-%Y"),
        "time": when.strftime("%I:%M%p"),
        "showIntermediateStops": "true",
        "mode": "TRANSIT,WALK",
    }
    query = "?" + "&".join(f"{k}={urllib.parse.quote(v)}" for k, v in parameters.items())

    base = base_url.rstrip("/")
    # A router-rooted base is unambiguously a modern server, so only a server-root base gets the
    # app's pre-1.0 retry at the bare /plan path.
    router_rooted = OTP_ROUTERS_SEGMENT in base
    attempts = [False] if router_rooted else [False, True]
    last = None
    for old_server in attempts:
        url = otp_plan_url(base, query, old_server)
        try:
            status, body = fetch(url)
        except Exception as e:  # noqa: BLE001 -- any transport failure is the same verdict here
            return False, f"unreachable: {type(e).__name__}: {e}"
        if status != 200:
            last = f"HTTP {status} from {url}"
            continue
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            return False, f"HTTP 200 but not JSON from {url}"
        # An OTP1 plan response is one of these two envelopes. Anything else that came back 200 --
        # a captive portal, a bare {}, some other service at that host -- is not this planner
        # answering, and reading it as "no route" would report a dead endpoint as a healthy one.
        plan = payload.get("plan") if isinstance(payload, dict) else None
        error = payload.get("error") if isinstance(payload, dict) else None
        if isinstance(error, dict) and error:
            # The planner answered; a planner error is a routing verdict, not a broken server.
            return True, f"no route: OTP error {error.get('id')} {error.get('msg')!r}"
        if not isinstance(plan, dict):
            return False, f"HTTP 200 but not an OTP plan response from {url}"
        itineraries = plan.get("itineraries") or []
        if not itineraries:
            return True, "no route: empty plan"
        return True, f"plans: {len(itineraries)} itineraries"
    return False, last or "no usable response"


def probe_otp2(graphql_base_url, origin, destination, when):
    """Plan over the OTP2 GraphQL endpoint. Returns (ok, summary)."""
    url = graphql_base_url.rstrip("/") + OTP2_GTFS_GRAPHQL_PATH

    def coordinate(point):
        lat, lon = point
        return {"location": {"coordinate": {"latitude": lat, "longitude": lon}}}

    body = json.dumps(
        {
            "query": OTP2_PROBE_QUERY,
            "variables": {
                "origin": coordinate(origin),
                "destination": coordinate(destination),
                # Otp2PlanRequestBuilder.build: an ISO-8601 offset instant in the local zone. Without
                # it OTP2 departs "now", so the two probes would ask different questions and this one
                # could answer "no route" simply for running outside the region's service hours.
                "dateTime": {"earliestDeparture": when.astimezone().isoformat(timespec="seconds")},
            },
        }
    ).encode()
    try:
        status, text = fetch(url, data=body, content_type="application/json")
    except Exception as e:  # noqa: BLE001
        return False, f"unreachable: {type(e).__name__}: {e}"
    if status != 200:
        return False, f"HTTP {status} from {url}"
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        return False, f"HTTP 200 but not JSON from {url}"
    if payload.get("errors"):
        # A GraphQL error is the server rejecting the query, which for this fixed probe query means
        # the endpoint is not the OTP2 gtfs mount the app would talk to.
        return False, f"GraphQL errors: {json.dumps(payload['errors'])[:200]}"
    data = payload.get("data") if isinstance(payload, dict) else None
    connection = data.get("planConnection") if isinstance(data, dict) else None
    # As in probe_otp1: only the envelope this query asked for counts as the planner answering.
    if not isinstance(connection, dict) or "edges" not in connection:
        return False, f"HTTP 200 but not an OTP2 plan response from {url}"
    if connection.get("routingErrors"):
        return True, f"no route: {json.dumps(connection['routingErrors'])[:160]}"
    edges = connection.get("edges") or []
    if not edges:
        return True, "no route: empty plan"
    return True, f"plans: {len(edges)} itineraries"


def check(regions, when):
    """Probe every region; returns (regions whose planner is broken, regions left without a verdict)."""
    broken = []
    not_probed = []
    for region in sorted(regions, key=lambda r: r.get("id", 0)):
        name = f"{region.get('regionName')} (id {region.get('id')})"
        otp1 = (region.get("otpBaseUrl") or "").strip()
        otp2 = (region.get("otpBaseGraphqlUrl") or "").strip()
        if not otp1 and not otp2:
            # Region.usesOtp2 / OtpTarget: publishing neither URL is "this region has no planner".
            print(f"  {name}: no planner published")
            continue
        points = probe_points(region)
        if points is None:
            print(f"  {name}: not probed, region publishes no usable bounds to plan within")
            not_probed.append(name)
            continue
        origin, destination = points
        # OtpTarget.resolve: a published GraphQL endpoint is what routes a plan to OTP2, and the
        # OTP1 base is then not the server the app would ask.
        if otp2:
            ok, summary = probe_otp2(otp2, origin, destination, when)
            print(f"  {name}: OTP2 {summary}")
        else:
            ok, summary = probe_otp1(otp1, origin, destination, when)
            print(f"  {name}: OTP1 {summary}")
        if not ok:
            broken.append(f"{name}: {summary}")
    return broken, not_probed


def main(argv):
    """Run the check and return the process exit status (see this module's docstring)."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--regions", default=BUNDLED_REGIONS, help="path to a regions file to probe")
    parser.add_argument("--live", action="store_true", help="probe the live regions directory instead")
    args = parser.parse_args(argv)

    source = regions_directory_url(REGIONS_URL_RESOURCE) if args.live else args.regions
    regions = load_regions(source, live=args.live)
    # Tomorrow morning: a weekday-ish, mid-service time that no server has already run past.
    when = (datetime.datetime.now() + datetime.timedelta(days=1)).replace(hour=9, minute=0)

    print(f"regions: {source} ({len(regions)} regions)")
    print(f"planning at: {when:%Y-%m-%d %H:%M} local\n")
    broken, not_probed = check(regions, when)

    if broken:
        print(f"\nTrip planning is broken in {len(broken)} region(s):\n")
        for line in broken:
            print(f"  * {line}")
        print(
            "\nThe app can only report these as a connectivity failure -- the region directory says\n"
            "there is a server. Fixing one means fixing the server, or getting the region's otpBaseUrl\n"
            "corrected in the regions directory (https://github.com/OneBusAway/regions)."
        )
    if not_probed:
        print(f"\n{len(not_probed)} region(s) publish a planner this run could not ask:\n")
        for name in not_probed:
            print(f"  * {name}")
        print(
            "\nThat is a gap in this check, not a verdict on those servers: the region publishes no\n"
            "bounds to plan within, so there is nowhere to plan between. Getting the bounds into the\n"
            "regions directory is what would let this run answer for them."
        )
    # A broken planner is the actionable failure, so it takes the exit status when both happen; an
    # unasked region only has to stop the run being reported as a clean bill of health.
    if broken:
        return 1
    if not_probed:
        return 2
    print("\nEvery region that publishes a planner answered.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    # OSError covers urllib's URLError (a failed directory fetch); ParseError, a string resource that
    # isn't XML. Both are the check failing to get started, same as CheckError -- not a broken planner,
    # and not something to hand the operator as a traceback.
    except (CheckError, OSError, UnicodeError, ElementTree.ParseError) as e:
        print(f"error: {e}", file=sys.stderr)
        sys.exit(2)
