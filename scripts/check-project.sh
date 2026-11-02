#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
test -s "$ROOT/www/index.html"
node --check "$ROOT/scripts/check.js"
node "$ROOT/scripts/check.js"
echo "Static web/PWA sanity checks passed."
