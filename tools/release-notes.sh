#!/bin/bash
#
# release-notes.sh
#
# Assembles release notes from the `Changelog:` trailers on the commits since
# the last release tag.
#
# See .github/CONTRIBUTING.md#changelog-entries for when a commit should carry a
# trailer, and docs/BUILD.md#release-notes for where this sits in a release.
#
# Usage:
#   tools/release-notes.sh play              # print the Play Store "What's new" text
#   tools/release-notes.sh play --write      # ...and write it into the GPP tree
#   tools/release-notes.sh github            # print the highlights for a release body
#
#   --since <ref>   measure from <ref> instead of the most recent v* tag
#

set -e

# Every path and git call below is repo-relative.
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# gradle-play-publisher reads release notes from this tree. "default.txt" applies
# to every track, so the same text carries from the beta upload through to the
# promotion to production. Only en-US is generated; the other Play locales keep
# whatever text they already have.
PLAY_NOTES_FILE="onebusaway-android/src/obaGoogleRelease/play/release-notes/en-US/default.txt"

# Google Play rejects release notes longer than this, per locale.
PLAY_MAX_CHARS=500

usage() {
    echo "Usage: tools/release-notes.sh {play|github} [--since <ref>] [--write]" >&2
    exit 1
}

case "${1-}" in
    play|github) COMMAND="$1"; shift ;;
    *) usage ;;
esac

SINCE=""
WRITE=false

while [ $# -gt 0 ]; do
    case "$1" in
        --since) SINCE="${2-}"; [ -n "$SINCE" ] || usage; shift 2 ;;
        --write) WRITE=true; shift ;;
        *) usage ;;
    esac
done

if [ -z "$SINCE" ]; then
    SINCE="$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)"
    if [ -z "$SINCE" ]; then
        echo "Error: no v* tag found to measure from. Pass --since <ref> explicitly." >&2
        exit 1
    fi
fi

if ! git rev-parse --verify --quiet "$SINCE" > /dev/null; then
    echo "Error: '$SINCE' is not a valid ref." >&2
    exit 1
fi

RANGE="$SINCE..HEAD"

# Oldest commit first. git only recognizes a trailer in the block at the very end
# of a message, which is what we want — a "Changelog:" line quoted mid-body is not
# an entry. Drop the blank lines commits without a trailer contribute, and repeated
# values, which a squashed branch can introduce.
highlights="$(git log --no-merges --reverse \
    --format='%(trailers:key=Changelog,valueonly=true,unfold=true)' "$RANGE" \
    | awk 'NF && !seen[$0]++')"

# Squashing a multi-commit branch through the GitHub merge box concatenates the
# individual commit messages, which can leave a "Changelog:" line stranded in the
# middle of the body where git will not read it as a trailer. Only commits that
# wrote one can be stranded, so ask git for those and compare the two readings.
stranded="$(git log --no-merges --reverse --grep='^Changelog:' --format='%H %s' "$RANGE" \
    | while read -r sha subject; do
        in_body="$(git show -s --format='%B' "$sha" | grep -c '^Changelog:' || true)"
        in_trailer="$(git show -s --format='%(trailers:key=Changelog,valueonly=true,unfold=true)' "$sha" | grep -c . || true)"
        if [ "$in_body" -gt "$in_trailer" ]; then
            echo "  ${sha:0:9} $subject"
        fi
    done)"

if [ -n "$stranded" ]; then
    echo "Warning: these commits have a 'Changelog:' line that git did not read as a" >&2
    echo "         trailer, so it is missing below. A trailer is only recognized in the" >&2
    echo "         trailer block at the very end of a commit message:" >&2
    echo "$stranded" >&2
    echo "" >&2
fi

case "$COMMAND" in
    play)
        if [ -z "$highlights" ]; then
            echo "Error: no 'Changelog:' trailers in $RANGE, so there is nothing to tell riders." >&2
            echo "       Either add a trailer to the commits that changed something visible," >&2
            echo "       or write $PLAY_NOTES_FILE by hand for a maintenance-only release." >&2
            exit 1
        fi

        notes="$(printf '%s\n' "$highlights" | sed 's/^/• /')"

        char_count=${#notes}
        if [ "$char_count" -gt "$PLAY_MAX_CHARS" ]; then
            over=$((char_count - PLAY_MAX_CHARS))
            echo "Error: release notes are $char_count characters, $over over the Play Store limit of $PLAY_MAX_CHARS." >&2
            echo "       Shorten or drop a 'Changelog:' trailer — do not let this be truncated," >&2
            echo "       because Play truncates from the end and would cut an entry mid-word." >&2
            exit 1
        fi

        if [ "$WRITE" = true ]; then
            mkdir -p "$(dirname "$PLAY_NOTES_FILE")"
            printf '%s\n' "$notes" > "$PLAY_NOTES_FILE"
            echo "Wrote $char_count/$PLAY_MAX_CHARS characters to $PLAY_NOTES_FILE"
            echo ""
        fi

        printf '%s\n' "$notes"
        ;;

    github)
        # Only the highlights. `gh release create --generate-notes` builds the rest
        # of the body — commit and PR list, authors, new contributors, the compare
        # link — and prepends whatever is passed as --notes. See docs/BUILD.md.
        if [ -z "$highlights" ]; then
            echo "No 'Changelog:' trailers in $RANGE; the release body will be generated notes only." >&2
            exit 0
        fi

        echo "## Highlights"
        echo ""
        printf '%s\n' "$highlights" | sed 's/^/- /'
        ;;
esac
