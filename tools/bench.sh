#!/usr/bin/env bash
# Compares Claude model settings on a scripted conversation. Spends a few cents.
# Takes the API key from the environment, or from a .env file in the project root (ANTHROPIC_API_KEY=...).
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -z "${ANTHROPIC_API_KEY:-}" ] && [ -f .env ]; then
  set -a; . ./.env; set +a
fi
[ -n "${ANTHROPIC_API_KEY:-}" ] || { echo "ANTHROPIC_API_KEY topilmadi: .env fayliga yozing" >&2; exit 1; }
exec ./gradlew -q :bench:run
