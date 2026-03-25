#!/usr/bin/env bash
# Sync Assets.zip and HytaleServer.jar from Windows Hytale install to WSL2 ext4.
# Run this after a Hytale update to refresh local copies.

set -euo pipefail

HYTALE_WIN="/mnt/c/Users/User/AppData/Roaming/Hytale/install/release/package/game/latest"
ASSETS_SRC="$HYTALE_WIN/Assets.zip"
SERVER_SRC="$HYTALE_WIN/Server/HytaleServer.jar"

ASSETS_DST="$HOME/dev/Hytale/assets/Assets.zip"
SERVER_DST="$(dirname "$0")/libs/HytaleServer.jar"

for src in "$ASSETS_SRC" "$SERVER_SRC"; do
    if [[ ! -f "$src" ]]; then
        echo "ERROR: $src not found"
        exit 1
    fi
done

mkdir -p "$(dirname "$ASSETS_DST")"

echo "Copying Assets.zip..."
cp "$ASSETS_SRC" "$ASSETS_DST"
echo "  -> $ASSETS_DST ($(du -h "$ASSETS_DST" | cut -f1))"

echo "Copying HytaleServer.jar..."
cp "$SERVER_SRC" "$SERVER_DST"
echo "  -> $SERVER_DST ($(du -h "$SERVER_DST" | cut -f1))"

echo "Done."
