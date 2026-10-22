#!/usr/bin/env bash
# Fails on obvious placeholder AI comment markers in tracked source (low false-positive policy).
set -euo pipefail
PATTERN='(?i)(as an ai|todo: implement|placeholder implementation|fake success|lorem ipsum)'
if git grep -E "$PATTERN" -- 'services/api/src/main' 'apps/web/src' 2>/dev/null; then
  echo "Suspicious placeholder comment patterns detected."
  exit 1
fi
echo "No suspicious placeholder comment patterns in main sources."
