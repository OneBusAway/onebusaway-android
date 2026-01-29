#!/bin/bash
#
# missing_localizations.sh
#
# Shows the localization status for all languages in the OneBusAway Android app.
# Displays the number of translated strings and missing translations for each locale.
#

set -e

# Find the project root (where this script is in tools/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

RES_DIR="$PROJECT_ROOT/onebusaway-android/src/main/res"
EN_FILE="$RES_DIR/values/strings.xml"

if [ ! -f "$EN_FILE" ]; then
    echo "Error: English strings file not found at $EN_FILE"
    exit 1
fi

# Extract English string keys
grep '<string name=' "$EN_FILE" | sed 's/.*<string name="\([^"]*\)".*/\1/' | sort -u > /tmp/en_keys.txt
en_count=$(wc -l < /tmp/en_keys.txt | tr -d ' ')

echo "OneBusAway Android Localization Status"
echo "======================================="
echo ""
printf "%-20s %10s %10s %10s\n" "Language" "Strings" "Missing" "Complete"
printf "%-20s %10s %10s %10s\n" "--------" "-------" "-------" "--------"
printf "%-20s %10d %10s %10s\n" "English (source)" "$en_count" "-" "100%"

# Find all localized values directories
for dir in "$RES_DIR"/values-*/; do
    if [ -f "$dir/strings.xml" ]; then
        lang=$(basename "$dir" | sed 's/values-//')

        # Extract language string keys
        grep '<string name=' "$dir/strings.xml" | sed 's/.*<string name="\([^"]*\)".*/\1/' | sort -u > /tmp/lang_keys.txt
        lang_count=$(wc -l < /tmp/lang_keys.txt | tr -d ' ')

        # Count missing keys
        missing=$(comm -23 /tmp/en_keys.txt /tmp/lang_keys.txt | wc -l | tr -d ' ')

        # Calculate completion percentage
        if [ "$en_count" -gt 0 ]; then
            complete=$(( (en_count - missing) * 100 / en_count ))
        else
            complete=0
        fi

        # Format output with completion indicator
        if [ "$missing" -eq 0 ]; then
            status="$complete% ✓"
        else
            status="$complete%"
        fi

        # Get full language name
        case "$lang" in
            es) lang_name="Spanish (es)" ;;
            fi) lang_name="Finnish (fi)" ;;
            it) lang_name="Italian (it)" ;;
            pl) lang_name="Polish (pl)" ;;
            de) lang_name="German (de)" ;;
            fr) lang_name="French (fr)" ;;
            pt) lang_name="Portuguese (pt)" ;;
            zh) lang_name="Chinese (zh)" ;;
            ja) lang_name="Japanese (ja)" ;;
            ko) lang_name="Korean (ko)" ;;
            ru) lang_name="Russian (ru)" ;;
            ar) lang_name="Arabic (ar)" ;;
            *) lang_name="$lang" ;;
        esac

        printf "%-20s %10d %10d %10s\n" "$lang_name" "$lang_count" "$missing" "$status"
    fi
done | sort -t'%' -k4 -rn

# Cleanup
rm -f /tmp/en_keys.txt /tmp/lang_keys.txt

echo ""
echo "Run with -v or --verbose for details on missing keys"

# Verbose mode - show missing keys
if [ "${1:-}" = "-v" ] || [ "${1:-}" = "--verbose" ]; then
    echo ""
    echo "Missing Keys by Language"
    echo "========================"

    grep '<string name=' "$EN_FILE" | sed 's/.*<string name="\([^"]*\)".*/\1/' | sort -u > /tmp/en_keys.txt

    for dir in "$RES_DIR"/values-*/; do
        if [ -f "$dir/strings.xml" ]; then
            lang=$(basename "$dir" | sed 's/values-//')
            grep '<string name=' "$dir/strings.xml" | sed 's/.*<string name="\([^"]*\)".*/\1/' | sort -u > /tmp/lang_keys.txt
            missing_keys=$(comm -23 /tmp/en_keys.txt /tmp/lang_keys.txt)

            if [ -n "$missing_keys" ]; then
                echo ""
                echo "[$lang] Missing keys:"
                echo "$missing_keys" | sed 's/^/  /'
            fi
        fi
    done

    rm -f /tmp/en_keys.txt /tmp/lang_keys.txt
fi
