#!/usr/bin/env bash
# Prints the section of CHANGELOG.md for one release, which becomes the text of its GitHub Release
# (ADR 0018). Fails when the changelog has no section for it, or an empty one.
#
#   .github/scripts/release-notes.sh 1.2.3
#   .github/scripts/release-notes.sh Unreleased
set -euo pipefail

version="${1:?Usage: release-notes.sh VERSION}"
changelog="$(dirname "$0")/../../CHANGELOG.md"

# A section runs from its heading, such as "## [1.2.3] - 2026-11-17", to the next heading or to the
# list of links at the end of the file.
notes="$(awk -v heading="## [$version]" '
  /^## \[/ || /^\[[^]]+\]: / { in_section = (index($0, heading) == 1); next }
  in_section
' "$changelog")"

# Without the empty lines before the first line of text. "$(...)" has already dropped those after
# the last one.
notes="$(sed '/./,$!d' <<<"$notes")"

if [[ -z "$notes" ]]; then
  echo "CHANGELOG.md has no section \"## [$version]\", or nothing in it." >&2
  exit 1
fi
printf '%s\n' "$notes"
