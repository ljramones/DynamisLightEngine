#!/usr/bin/env bash
set -euo pipefail

FILE_PATH="${1:-wish_list.md}"
SNAPSHOT_DATE="${2:-$(date +%F)}"

if [[ ! -f "$FILE_PATH" ]]; then
  echo "File not found: $FILE_PATH" >&2
  exit 1
fi

in_count="$(rg -N '— `In`$' "$FILE_PATH" | wc -l | tr -d ' ')"
partial_count="$(rg -N '— `Partial`$' "$FILE_PATH" | wc -l | tr -d ' ')"
not_in_yet_count="$(rg -N '— `Not In Yet`$' "$FILE_PATH" | wc -l | tr -d ' ')"

tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

awk \
  -v snapshot_date="$SNAPSHOT_DATE" \
  -v in_count="$in_count" \
  -v partial_count="$partial_count" \
  -v not_in_yet_count="$not_in_yet_count" '
BEGIN {
  replaced = 0;
  skipping_table = 0;
}
{
  if ($0 ~ /^Status summary snapshot \(/) {
    print "Status summary snapshot (" snapshot_date "):";
    print "";
    print "| Status | Count |";
    print "| --- | ---: |";
    printf("| `In` | %d |\n", in_count);
    printf("| `Partial` | %d |\n", partial_count);
    printf("| `Not In Yet` | %d |\n", not_in_yet_count);
    replaced = 1;
    skipping_table = 1;
    next;
  }

  if (skipping_table == 1) {
    if ($0 ~ /^\|/) {
      next;
    }
    if ($0 ~ /^[[:space:]]*$/) {
      print "";
      skipping_table = 0;
      next;
    }
    skipping_table = 0;
  }

  print;
}
END {
  if (replaced == 0) {
    print "Missing Status summary snapshot section in " FILENAME > "/dev/stderr";
    exit 2;
  }
}
' "$FILE_PATH" > "$tmp_file"

mv "$tmp_file" "$FILE_PATH"

echo "Updated wishlist summary in $FILE_PATH"
echo "In=$in_count Partial=$partial_count NotInYet=$not_in_yet_count"
