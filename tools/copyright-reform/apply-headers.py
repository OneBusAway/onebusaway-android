#!/usr/bin/env python3
"""Apply the canonical Apache-2.0 license header to OneBusAway source files.

Part of the copyright-header reform (see tools/copyright-reform/CONSENT-REQUEST.md
and the repo NOTICE file). The end state: every git-tracked .java/.kt file carries
one identical header naming no individual copyright holder and no year -- the
convention used by the Apache Software Foundation and Kubernetes. Historical per-file
notices are preserved verbatim in NOTICE, not in the file.

TRANSITION GRADUALLY VIA THE EXCLUDED-HOLDERS LIST
--------------------------------------------------
Relocating a holder's copyright notice needs that holder's authorization (see
CONSENT-REQUEST.md). Rather than wait for every holder at once, we gate per holder:

  * EXCLUDED_HOLDERS lists holders who have NOT yet authorized relocation.
  * A file is migrated only when NONE of the holders named in its original header
    are still excluded. A file naming several holders waits for the LAST of them.
  * As each authorization arrives, delete that holder's entry and re-run. Files that
    were blocked solely by that holder now migrate. Already-migrated files are
    untouched (the rewrite is idempotent).

So you can commit this tooling now with everyone excluded (migrates nothing), then
lift holders one at a time. Removing the self-authorizing steward (OTSF) first
migrates the ~500 OTSF-only files immediately.

Third-party files (Android Open Source Project code) are handled separately by
EXCEPTIONS (path-based) and are never touched -- their notices stay in-file per
Apache-2.0 and ASF policy.

Usage:
    tools/copyright-reform/apply-headers.py            # migrate all now-eligible files
    tools/copyright-reform/apply-headers.py --check    # CI: fail if an eligible file drifts
    tools/copyright-reform/apply-headers.py --dry-run  # preview: what migrates, what's blocked
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

# The canonical copyright holder string. Confirm this exact wording with OTSF before
# running the migration; it also appears in NOTICE. Both the Linux Foundation and
# ASF endorse a generic "The <Project> Authors" holder for CLA-based projects.
HOLDER = "The OneBusAway Authors."

# Body of the Apache-2.0 header, sans comment markers. Rendered per-language below.
_HEADER_LINES = [
    f"Copyright {HOLDER}",
    "",
    'Licensed under the Apache License, Version 2.0 (the "License");',
    "you may not use this file except in compliance with the License.",
    "You may obtain a copy of the License at",
    "",
    "     http://www.apache.org/licenses/LICENSE-2.0",
    "",
    "Unless required by applicable law or agreed to in writing, software",
    'distributed under the License is distributed on an "AS IS" BASIS,',
    "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
    "See the License for the specific language governing permissions and",
    "limitations under the License.",
]


def canonical_header() -> str:
    """The exact C-style block-comment header for .java/.kt files."""
    out = ["/*"]
    for line in _HEADER_LINES:
        out.append(" *" if line == "" else f" * {line}")
    out.append(" */")
    return "\n".join(out)


CANON = canonical_header()

# ----------------------------------------------------------------------------------
# Holders who have NOT yet authorized relocating their notice to NOTICE.
#
# Each entry: (human label, regex tested against a file's ORIGINAL copyright block).
# A file is BLOCKED from migration while any of these patterns match its header.
# Remove an entry once that holder's authorization is on file (record it under
# tools/copyright-reform/consents/), then re-run this script.
#
# Patterns are case-insensitive and matched only against the copyright lines (the
# Apache boilerplate below them is not considered), so they never match the canonical
# header -- an already-migrated file names no holder and is never re-blocked.
# ----------------------------------------------------------------------------------
EXCLUDED_HOLDERS: list[tuple[str, str]] = [
    # OTSF is the project steward and self-authorizing. Remove this entry FIRST to
    # migrate the ~500 files whose only holder is OTSF. (Kept here initially so a bare
    # run migrates nothing until a human deliberately lifts a holder.)
    ("Open Transit Software Foundation (steward -- self-authorizing)",
     r"Open Transit Software Foundation"),

    ("Paul Watts",                    r"Paul Watts|paulcwatts"),
    # USF (institution) and Barbeau (individual) are intertwined -- USF notices carry
    # sjbarbeau@gmail.com, so USF-with-email files match both. Lift these two together
    # when the USF/Barbeau authorization arrives.
    ("University of South Florida",   r"University of South\s+Florida"),
    ("Sean J. Barbeau",               r"Barbeau|sjbarbeau"),
    ("Brian Ferris",                  r"Brian Ferris|bdferris"),
    ("Microsoft Corporation",         r"Microsoft"),
    ("Rodrigo Carvalho",              r"Rodrigo Carvalho|carvalhorr"),
    ("Colin McDonough",               r"Colin McDonough"),
    ("Cambridge Systematics, Inc.",   r"Cambridge Systematics"),
    ("Benjamin Du",                   r"Benjamin Du|bendu"),
    # The lone "Copyright (C) 2026 OneBusAway" notice. Anchored on "(C) <year>" so it
    # does NOT match the canonical "Copyright The OneBusAway Authors." header.
    ("OneBusAway (lone dated notice)", r"\(C\)\s*\d{4}\s+OneBusAway\b"),
    # Generic catch-all text with no identifiable external party. Likely safe to lift
    # early (covered by the project/ICLA), but left in by default -- a human decides.
    ("individual contributors (generic)", r"individual contributors"),
]

_EXCLUDED = [(label, re.compile(rx, re.IGNORECASE)) for label, rx in EXCLUDED_HOLDERS]

# Third-party files whose copyright notices must stay in-file (AOSP-derived). These
# are never rewritten and never checked. Repo-relative POSIX paths.
EXCEPTIONS = {
    "onebusaway-android/src/main/java/org/onebusaway/android/util/MathUtils.java",
    "onebusaway-android/src/main/java/org/onebusaway/android/util/PermissionUtils.java",
    "onebusaway-android/src/androidTest/java/org/onebusaway/android/api/test/LoaderTestCase.java",
}

# Matches a leading C-style block comment (optionally preceded by BOM/whitespace).
_LEADING_BLOCK = re.compile(r"\A(?:﻿)?\s*/\*.*?\*/", re.DOTALL)


def _copyright_region(text: str) -> str:
    """The copyright portion of a leading license comment (before the Apache text)."""
    m = _LEADING_BLOCK.match(text)
    if not m:
        return ""
    block = m.group(0)
    idx = block.find("Licensed under the Apache")
    return block[:idx] if idx != -1 else block


def blocking_holders(text: str) -> list[str]:
    """Labels of still-excluded holders named in this file's original header."""
    region = _copyright_region(text)
    return [label for label, rx in _EXCLUDED if rx.search(region)]


def is_canonical(text: str) -> bool:
    return text.startswith(CANON)


def rewritten(text: str) -> str:
    """Return `text` with its leading license block replaced by the canonical header.

    Only strips the existing leading block comment if it looks like a license/
    copyright header, so a genuine leading doc-comment is never clobbered. Idempotent.
    """
    m = _LEADING_BLOCK.match(text)
    if m and re.search(r"Copyright|Licensed under the Apache", m.group(0)):
        remainder = text[m.end():]
    else:
        remainder = text
    remainder = remainder.lstrip("\n")
    if remainder == "":
        return CANON + "\n"
    return CANON + "\n\n" + remainder


# Per-file classification, shared by run/check/dry-run.
EXEMPT, CANONICAL, BLOCKED, MIGRATE = "exempt", "canonical", "blocked", "migrate"


def classify(rel: str, text: str) -> tuple[str, list[str]]:
    if rel in EXCEPTIONS:
        return EXEMPT, []
    if is_canonical(text):
        return CANONICAL, []
    blockers = blocking_holders(text)
    if blockers:
        return BLOCKED, blockers
    return MIGRATE, []


def repo_root() -> Path:
    out = subprocess.run(["git", "rev-parse", "--show-toplevel"],
                         check=True, capture_output=True, text=True)
    return Path(out.stdout.strip())


def tracked_sources(root: Path) -> list[Path]:
    out = subprocess.run(["git", "ls-files", "*.java", "*.kt"],
                         cwd=root, check=True, capture_output=True, text=True)
    return [root / rel for rel in out.stdout.splitlines() if rel]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = ap.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true",
                      help="CI mode: exit non-zero if an eligible file lacks the canonical header")
    mode.add_argument("--dry-run", action="store_true",
                      help="Preview what would migrate and what is blocked, without writing")
    args = ap.parse_args()

    root = repo_root()
    migrated: list[str] = []
    drifted: list[str] = []          # eligible (unblocked) but not canonical -> must migrate
    blocked_by: Counter[str] = Counter()
    n_blocked = n_canonical = n_exempt = 0

    for path in tracked_sources(root):
        rel = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8")
        kind, blockers = classify(rel, text)

        if kind == EXEMPT:
            n_exempt += 1
        elif kind == CANONICAL:
            n_canonical += 1
        elif kind == BLOCKED:
            n_blocked += 1
            for label in blockers:
                blocked_by[label] += 1
        else:  # MIGRATE
            if args.check:
                drifted.append(rel)
            else:
                migrated.append(rel)
                if not args.dry_run:
                    path.write_text(rewritten(text), encoding="utf-8")

    if args.check:
        if drifted:
            print(f"{len(drifted)} eligible file(s) do NOT have the canonical header:",
                  file=sys.stderr)
            for rel in drifted:
                print(f"  {rel}", file=sys.stderr)
            print("\nRun tools/copyright-reform/apply-headers.py to fix.", file=sys.stderr)
            return 1
        print(f"OK: every eligible file carries the canonical header "
              f"({n_canonical} canonical, {n_blocked} awaiting consent, {n_exempt} third-party).")
        return 0

    verb = "Would migrate" if args.dry_run else "Migrated"
    print(f"{verb} {len(migrated)} file(s).")
    print(f"Already canonical: {n_canonical}. "
          f"Blocked (awaiting consent): {n_blocked}. Third-party exempt: {n_exempt}.")
    if blocked_by:
        print("\nBlocked by holder (remove from EXCLUDED_HOLDERS as consent arrives):")
        for label, n in sorted(blocked_by.items(), key=lambda kv: (-kv[1], kv[0])):
            print(f"  {n:4d}  {label}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
