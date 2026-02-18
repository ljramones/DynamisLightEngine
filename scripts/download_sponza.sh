#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST_DIR="$ROOT_DIR/assets/scenes/Sponza"
MARKER_FILE="$DEST_DIR/.download-complete"

if [[ -f "$MARKER_FILE" ]]; then
  echo "Sponza already present at $DEST_DIR — skipping download."
  exit 0
fi

mkdir -p "$DEST_DIR"

BASE_URL="https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/Sponza/glTF"

echo "Downloading Sponza glTF scene (~30 MB total) ..."

# Download the main gltf and bin files
curl -fSL --retry 3 -o "$DEST_DIR/Sponza.gltf" "$BASE_URL/Sponza.gltf"
curl -fSL --retry 3 -o "$DEST_DIR/Sponza.bin"  "$BASE_URL/Sponza.bin"

# Download all texture files listed via GitHub API
echo "Downloading textures ..."
TEXTURE_LIST=$(curl -fsSL "https://api.github.com/repos/KhronosGroup/glTF-Sample-Assets/contents/Models/Sponza/glTF" \
  | python3 -c "
import sys, json
for e in json.load(sys.stdin):
    name = e['name']
    if name.endswith('.jpg') or name.endswith('.png'):
        print(name)
")

TOTAL=$(echo "$TEXTURE_LIST" | wc -l | tr -d ' ')
COUNT=0
while IFS= read -r name; do
  COUNT=$((COUNT + 1))
  printf "  [%d/%d] %s\n" "$COUNT" "$TOTAL" "$name"
  curl -fsSL --retry 3 -o "$DEST_DIR/$name" "$BASE_URL/$name"
done <<< "$TEXTURE_LIST"

touch "$MARKER_FILE"
echo "Sponza downloaded to $DEST_DIR ($COUNT textures)"
