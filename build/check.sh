#!/usr/bin/env bash
# Runs upstream's CI checks (detekt, Android lint, unit tests) in Docker on a branch.
#
#   build/check.sh [branch]     default: build
#
# Reports land in jellyfin-android/build/reports (detekt) and app/build/reports (lint, tests).
set -euo pipefail

JELLY="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$JELLY/jellyfin-android"
IMAGE=jf-android-build
BRANCH="${1:-build}"
cd "$REPO"

if [ -n "$(git status --porcelain)" ]; then
  echo "jellyfin-android has uncommitted changes; commit or stash them first" >&2
  exit 1
fi
git checkout -q "$BRANCH"

to_docker() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }

MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(to_docker "$REPO"):/src" \
  -v jf-gradle-cache:/root/.gradle -v jf-sdk:/opt/android-sdk/platforms \
  "$IMAGE" ./gradlew --no-daemon --continue detekt lint test
