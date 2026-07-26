#!/bin/bash
#
# release-notes.sh
#
# Generates release notes from `Changelog:` commit-message trailers.
#
# Because every PR lands on main as a single squashed commit, one commit is one
# change, and the commit message is a usable place to record what that change
# means to a rider. A commit may carry an optional trailer:
#
#     Changelog: Links shared from iOS now open the right screen.
#
# The trailer is optional and most commits should not have one. A commit subject
# ("Fold report/ into ui/report/ and wire its ViewModels through Hilt (#2016)")
# is already a fine developer-facing changelog line, and every subject appears in
# the GitHub release body. The trailer exists only for the handful of changes per
# release that a rider would actually notice, because those need different words
# than the subject line gives them.
#
# Usage:
#   tools/release-notes.sh play              # print the Play Store "What's new" text
#   tools/release-notes.sh play --write      # ...and write it into the GPP tree
#   tools/release-notes.sh github            # print a GitHub release body
#
#   --since <ref>   start from <ref> instead of the most recent v* tag
#

set -e

# Find the project root (where this script is in tools/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# gradle-play-publisher reads release notes from this tree. "default.txt" applies
# to every track, so the same text carries from the beta upload through the
# promotion to production. See docs/BUILD.md.
PLAY_NOTES_FILE="$PROJECT_ROOT/onebusaway-android/src/obaGoogleRelease/play/release-notes/en-US/default.txt"

# Google Play rejects release notes longer than this, per locale.
PLAY_MAX_CHARS=500

REPO_URL="https://github.com/OneBusAway/onebusaway-android"

COMMAND=""
SINCE=""
WRITE=false

while [ $# -gt 0 ]; do
    case "$1" in
        play|github)
            COMMAND="$1"
            shift
            ;;
        --since)
            SINCE="$2"
            shift 2
            ;;
        --write)
            WRITE=true
            shift
            ;;
        *)
            echo "Error: unknown argument '$1'" >&2
            echo "Usage: tools/release-notes.sh {play|github} [--since <ref>] [--write]" >&2
            exit 1
            ;;
    esac
done

if [ -z "$COMMAND" ]; then
    echo "Usage: tools/release-notes.sh {play|github} [--since <ref>] [--write]" >&2
    exit 1
fi

if [ -z "$SINCE" ]; then
    SINCE="$(git -C "$PROJECT_ROOT" describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)"
    if [ -z "$SINCE" ]; then
        echo "Error: no v* tag found to measure from. Pass --since <ref> explicitly." >&2
        exit 1
    fi
fi

if ! git -C "$PROJECT_ROOT" rev-parse --verify --quiet "$SINCE" > /dev/null; then
    echo "Error: '$SINCE' is not a valid ref." >&2
    exit 1
fi

RANGE="$SINCE..HEAD"

# Collect the trailer values, oldest commit first. git only recognizes a trailer
# block at the very end of a commit message, which is exactly what we want: a
# "Changelog:" line quoted in the middle of a body is not an entry.
raw_highlights="$(git -C "$PROJECT_ROOT" log --no-merges --reverse \
    --format='%(trailers:key=Changelog,valueonly=true,unfold=true)' "$RANGE" \
    | sed '/^[[:space:]]*$/d')"

# Squashing a multi-commit branch through the GitHub merge box concatenates the
# individual commit messages, which can leave a "Changelog:" line stranded in the
# middle of the body where git will not read it as a trailer. Count both ways and
# say so, rather than silently dropping the entry.
written_count="$(git -C "$PROJECT_ROOT" log --no-merges --format='%B' "$RANGE" \
    | grep -c '^Changelog:' || true)"
recognized_count="$(printf '%s' "$raw_highlights" | grep -c . || true)"

if [ "$written_count" -gt "$recognized_count" ]; then
    echo "Warning: found $written_count 'Changelog:' lines but only $recognized_count parsed as trailers." >&2
    echo "         A trailer is only recognized in the trailer block at the end of a commit" >&2
    echo "         message. These commits have one that is not:" >&2
    git -C "$PROJECT_ROOT" log --no-merges --format='%H %s' "$RANGE" | while read -r sha subject; do
        in_body="$(git -C "$PROJECT_ROOT" show -s --format='%B' "$sha" | grep -c '^Changelog:' || true)"
        in_trailer="$(git -C "$PROJECT_ROOT" show -s --format='%(trailers:key=Changelog,valueonly=true,unfold=true)' "$sha" | grep -c . || true)"
        if [ "$in_body" -gt "$in_trailer" ]; then
            echo "           ${sha:0:9} $subject" >&2
        fi
    done
    echo "" >&2
fi

# Drop repeated values, keeping the first occurrence. The same concatenation can
# also duplicate an entry across the commits it swept together.
highlights="$(printf '%s\n' "$raw_highlights" | awk 'NF && !seen[$0]++')"

case "$COMMAND" in
    play)
        if [ -z "$highlights" ]; then
            echo "Error: no 'Changelog:' trailers in $RANGE, so there is nothing to tell riders." >&2
            echo "       Either add a trailer to the commits that changed something visible," >&2
            echo "       or write ${PLAY_NOTES_FILE#"$PROJECT_ROOT"/} by hand for a maintenance-only release." >&2
            exit 1
        fi

        notes="$(printf '%s\n' "$highlights" | sed 's/^/• /')"

        char_count="$(printf '%s' "$notes" | wc -m | tr -d ' ')"
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
            echo "Wrote $char_count/$PLAY_MAX_CHARS characters to:"
            echo "  ${PLAY_NOTES_FILE#"$PROJECT_ROOT"/}"
            echo ""
        fi

        printf '%s\n' "$notes"
        ;;

    github)
        if [ -n "$highlights" ]; then
            echo "## Highlights"
            echo ""
            printf '%s\n' "$highlights" | sed 's/^/- /'
            echo ""
        fi

        echo "## All changes"
        echo ""
        git -C "$PROJECT_ROOT" log --no-merges --reverse --format='- %s' "$RANGE"
        echo ""
        echo "**Full changelog**: $REPO_URL/compare/$SINCE...HEAD"
        ;;
esac
