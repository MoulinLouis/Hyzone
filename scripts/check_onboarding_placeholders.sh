#!/usr/bin/env bash
# Checks that no placeholder text remains in Ascend tutorial/welcome .ui files.
# Exit 0 = clean, exit 1 = placeholders found.

set -euo pipefail

SEARCH_DIR="hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages"
BANNED=(
  "Description placeholder"
  '"Feature 1"'
  '"Feature 2"'
  '"Feature 3"'
  '"Feature 4"'
  '"Tip text"'
  "Lorem ipsum"
)

found=0
for pattern in "${BANNED[@]}"; do
  if grep -rn "$pattern" "$SEARCH_DIR"/Ascend_*.ui 2>/dev/null; then
    echo "ERROR: Found banned placeholder: $pattern"
    found=1
  fi
done

if [ "$found" -eq 0 ]; then
  echo "OK: No placeholder text found."
  exit 0
else
  echo "FAIL: Placeholder text detected in .ui files."
  exit 1
fi
