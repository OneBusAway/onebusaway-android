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
"""Compare the bundled regions file against the live regions directory (issue #2019).

`onebusaway-android/src/main/res/raw/regions_v3.json` is not a convenience copy of the regions
directory -- it is the app's **first-launch and offline** source of region config
(RegionsClient.parseBundledRegions). A device that plans a trip before the first directory fetch
lands reads the bundled values, so any field the app branches on that has drifted there is a live
behaviour difference for exactly those users, and it is invisible to every device that has already
refreshed its region cache. That is a nasty bug shape to debug from a report, so this check
compares the two and fails on drift.

It ran because Puget Sound's `otpBaseGraphqlUrl` had silently drifted: the bundled file pointed at
an `execute-api` host while the directory had moved on. Both happened to still be up, so nothing
was broken -- but that field decides whether trip planning speaks OTP2 at all (#1780) and gates
Puget Sound's bikeshare itineraries (#2017), so retiring the stale host would have quietly broken
first-launch trip planning in Seattle.

Run by the `regions-drift` leg of .github/workflows/android-nightly.yml. Needs network, so it is
deliberately kept off the PR/push workflow.

Usage (from the repo root):
    tools/check-regions-drift.py                       # fetch the live directory
    tools/check-regions-drift.py --live-file live.json # compare against a local copy instead

Exit status: 0 no drift, 1 drift found, 2 the check itself could not run.
"""

import argparse
import json
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ElementTree
from pathlib import Path

# Mirrors RegionsClient: the app reads the bundled file from res/raw and the directory URL from the
# `regions_api_url` string resource, so read the URL from that resource rather than restating it
# here -- otherwise a regions-v3 -> -v4 bump moves the app and leaves this check happily comparing
# against a directory nobody reads.
BUNDLED_REGIONS = "onebusaway-android/src/main/res/raw/regions_v3.json"
REGIONS_URL_RESOURCE = "onebusaway-android/src/main/res/values/donottranslate.xml"
REGIONS_URL_RESOURCE_NAME = "regions_api_url"
USER_AGENT = "onebusaway-android regions-drift-check (+https://github.com/OneBusAway/onebusaway-android)"

# The fields the app actually branches on, each with the value RegionDto decodes when the key is
# absent (or explicitly null -- the client decodes with coerceInputValues, so both land on the
# default). Comparing *effective* values this way keeps a merely-cosmetic difference, such as the
# directory stating `"supportsOtpGraphqlBikeshare": false` where the bundled file omits it, from
# being reported as drift.
#
# Adding a field here is the price of branching on a new one: if the app's behaviour depends on it,
# a bundled/live disagreement is a first-launch behaviour difference. Keep the defaults in sync with
# RegionDto in api/contract/ObaApiModels.kt. A name that no longer matches the wire -- a typo, or a
# field the directory renamed -- would otherwise read as the default on both sides and compare equal
# forever, so `check_fields_exist` fails the run instead of letting the check rot green.
#
# Deliberately *not* checked:
#   - `bounds` -- region geometry is retuned often and by tiny float amounts; diffing it would make
#     this check noisy enough to be ignored, which is worse than not running it.
#   - `open311Servers` -- a nested list whose bundled/live entries differ only in null-vs-"" for an
#     unread jurisdiction id.
#   - purely presentational fields (twitterUrl, contactEmail, payment warning copy, ...) -- stale
#     copy on first launch is not a broken app.
CHECKED_FIELDS = {
    "regionName": "",
    # Region usability gates -- RegionUtils.isRegionUsable. Drift here can hide a region from, or
    # wrongly offer it to, a first-launch user.
    "active": False,
    "experimental": False,
    "supportsObaDiscoveryApis": False,
    "supportsObaRealtimeApis": False,
    # Endpoints. A stale host is the failure this check exists for.
    "obaBaseUrl": None,
    "sidecarBaseUrl": None,
    "otpBaseUrl": None,
    "otpBaseGraphqlUrl": None,
    # Trip-planning capability flags, per protocol (see Region.supportsOtpGraphqlBikeshare).
    "supportsOtpBikeshare": False,
    "supportsOtpGraphqlBikeshare": False,
    # Analytics config -- AnalyticsProvider branches on both being present.
    "plausibleAnalyticsServerUrl": None,
    "umamiAnalytics": None,
    # Gates the fare-payment entry point.
    "paymentAndroidAppId": None,
}


class CheckError(Exception):
    """The check itself could not run (unreadable input, failed fetch, stale field list)."""


def read_text(path):
    try:
        return Path(path).read_text(encoding="utf-8")
    except OSError as e:
        raise CheckError(f"could not read {path}: {e}")


def regions_directory_url(resource_path):
    """The `regions_api_url` string resource — the directory URL the app itself fetches."""
    try:
        resources = ElementTree.fromstring(read_text(resource_path))
    except ElementTree.ParseError as e:
        raise CheckError(f"could not parse {resource_path}: {e}")
    for string in resources.iter("string"):
        if string.get("name") == REGIONS_URL_RESOURCE_NAME:
            return string.text
    raise CheckError(f"no <string name=\"{REGIONS_URL_RESOURCE_NAME}\"> in {resource_path}")


def fetch(url):
    # An explicit User-Agent is required, not politeness: the directory host answers 403 to
    # urllib's default `Python-urllib/3.x`.
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.read().decode("utf-8")
    except (urllib.error.URLError, OSError) as e:
        raise CheckError(f"could not fetch {url}: {e}")


def load_regions(text, source):
    """Return {id: region} from a regions-vN.json envelope."""
    try:
        return {r["id"]: r for r in json.loads(text)["data"]["list"]}
    except (ValueError, KeyError, TypeError) as e:
        raise CheckError(f"{source} is not a regions directory envelope: {e}")


def check_fields_exist(regions, source):
    """Fail if a checked field appears on no region — the name has gone stale (see CHECKED_FIELDS)."""
    unknown = [f for f in CHECKED_FIELDS if not any(f in r for r in regions.values())]
    if unknown:
        raise CheckError(
            f"CHECKED_FIELDS names no region in {source} carries: {', '.join(unknown)}. "
            "Either the name is misspelled or the directory renamed the field; left as-is it would "
            "compare equal on both sides forever and this check would rot green."
        )


def effective(region, field):
    """The value RegionDto decodes for `field`: an absent or null key means the default."""
    value = region.get(field)
    return CHECKED_FIELDS[field] if value is None else value


def describe(region_id, region):
    return f"[{region_id}] {region.get('regionName', '?')}"


def find_drift(bundled, live):
    """Return a list of human-readable drift lines; empty means the two agree."""
    drift = []

    for region_id in sorted(set(live) - set(bundled)):
        drift.append(
            f"{describe(region_id, live[region_id])}: in the live directory but not bundled -- "
            "first-launch users in this region get no config at all"
        )
    for region_id in sorted(set(bundled) - set(live)):
        drift.append(
            f"{describe(region_id, bundled[region_id])}: bundled but gone from the live directory -- "
            "first-launch users may be sent to a retired deployment"
        )

    for region_id in sorted(set(bundled) & set(live)):
        for field in CHECKED_FIELDS:
            bundled_value = effective(bundled[region_id], field)
            live_value = effective(live[region_id], field)
            if bundled_value != live_value:
                drift.append(
                    f"{describe(region_id, live[region_id])}: {field}\n"
                    f"    bundled: {json.dumps(bundled_value)}\n"
                    f"    live:    {json.dumps(live_value)}"
                )
    return drift


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--bundled", default=BUNDLED_REGIONS, help="path to the bundled regions file")
    parser.add_argument("--live-file", help="compare against this local file instead of fetching")
    args = parser.parse_args(argv)

    live_source = args.live_file or regions_directory_url(REGIONS_URL_RESOURCE)
    live_text = read_text(args.live_file) if args.live_file else fetch(live_source)

    bundled = load_regions(read_text(args.bundled), args.bundled)
    live = load_regions(live_text, live_source)
    check_fields_exist(live, live_source)

    print(f"bundled: {args.bundled} ({len(bundled)} regions)")
    print(f"live:    {live_source} ({len(live)} regions)")
    print(f"fields:  {', '.join(CHECKED_FIELDS)}\n")

    drift = find_drift(bundled, live)
    if not drift:
        print("No drift: the bundled fail-safe file agrees with the live directory.")
        return 0

    print(f"Drift found in {len(drift)} place(s):\n")
    for line in drift:
        print(f"  * {line}")
    print(
        "\nThe bundled file is the first-launch and offline source, so these are real behaviour\n"
        "differences for users who have not yet refreshed their region cache. Sync the fields\n"
        "above into\n"
        f"    {args.bundled}\n"
        "and update any test that pins one of them (see RegionsDecodeTest). Issue #2019 has the\n"
        "background."
    )
    return 1


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except CheckError as e:
        print(f"error: {e}", file=sys.stderr)
        sys.exit(2)
