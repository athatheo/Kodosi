#!/bin/bash

set -euo pipefail

cd "$(dirname "$0")/.."

SOURCE_DIR=${1:-}
if [ -z "$SOURCE_DIR" ] || [ ! -f "$SOURCE_DIR/build.zig.zon" ]; then
    echo "Usage: $0 <patched_source_dir>"
    exit 1
fi

if ! command -v zig >/dev/null 2>&1; then
    echo "[!] zig not found"
    exit 1
fi

rm -rf "$SOURCE_DIR/zig-pkg"
# A --system build crashes on a global cache that holds package archives, so
# the fetch keeps its own cache apart from the build cache.
FETCH_CACHE_DIR="$PWD/build/cache/zig-fetch"
(
    cd "$SOURCE_DIR"
    ZIG_GLOBAL_CACHE_DIR="$FETCH_CACHE_DIR" zig build --fetch=all
)

if [ ! -d "$SOURCE_DIR/zig-pkg" ]; then
    echo "[!] Zig package store was not materialized"
    exit 1
fi

python3 "$PWD/scripts/digest.py" tree "$SOURCE_DIR/zig-pkg"
