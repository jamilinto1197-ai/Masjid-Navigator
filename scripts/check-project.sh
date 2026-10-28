#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
test -s "$ROOT/www/index.html"
node --check "$ROOT/scripts/check.js"
echo "Basic project checks passed."
