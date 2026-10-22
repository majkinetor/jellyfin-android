#!/usr/bin/env bash
# Builds the signed APK of the `build` branch in Docker.
#
#   build/build.sh          build what `build` has now
#   build/build.sh sync     first fetch upstream, rebase every fix/* and own/* branch onto upstream/master
#                           and recreate `build` from upstream/master + all of them
#
# Output: out/jellyfin-<version>.apk (proprietary flavor, release, app id org.jellyfin.mobile).
set -euo pipefail

JELLY="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$JELLY/jellyfin-android"
BUILD="$JELLY/build"
OUT="$JELLY/out"
IMAGE=jf-android-build
cd "$REPO"

if [ -n "$(git status --porcelain)" ]; then
  echo "jellyfin-android has uncommitted changes; commit or stash them first" >&2
  exit 1
fi

branches() { git for-each-ref --format='%(refname:short)' refs/heads/fix refs/heads/own; }

if [ "${1:-}" = sync ]; then
  git fetch -q upstream --tags
  git checkout -q -B master upstream/master
  for b in $(branches); do
    echo "rebase $b"
    git rebase -q upstream/master "$b" || { echo "conflict in $b: resolve, 'git rebase --continue', then rerun" >&2; exit 1; }
  done
  git checkout -q -B build upstream/master
  # shellcheck disable=SC2046
  git merge -q --no-edit $(branches)
fi

git checkout -q build

# Version: latest upstream tag + "-local.<n>"; n counts up so each build installs over the last
TAG="$(git tag --sort=-v:refname | grep -E '^v[0-9]+.[0-9]+.[0-9]+$' | head -1)"
COUNTER="$BUILD/build-number"
N=$(( $(cat "$COUNTER" 2>/dev/null || echo 0) + 1 ))
if [ "$N" -gt 98 ]; then echo "build number would pass 98 (versionCode limit): reset $COUNTER after a new upstream tag" >&2; exit 1; fi
VERSION="${TAG#v}-local.$N"

docker image inspect "$IMAGE" >/dev/null 2>&1 || docker build -t "$IMAGE" "$BUILD"

to_docker() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }

# Signing key, made once and kept: an APK signed with another key won't install over this one
if [ ! -f "$BUILD/jelly-local.keystore" ]; then
  MSYS_NO_PATHCONV=1 docker run --rm -v "$(to_docker "$BUILD"):/keys" "$IMAGE" \
    keytool -genkeypair -keystore /keys/jelly-local.keystore -storepass jellylocal -keypass jellylocal \
      -alias jelly -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Jellyfin local build"
fi

MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(to_docker "$REPO"):/src" -v "$(to_docker "$BUILD"):/keys" \
  -v jf-gradle-cache:/root/.gradle -v jf-sdk:/opt/android-sdk/platforms \
  -e KEYSTORE_FILE=/keys/jelly-local.keystore -e KEYSTORE_PASSWORD=jellylocal \
  -e SIGNING_KEY_ALIAS=jelly -e SIGNING_KEY_PASSWORD=jellylocal \
  -e JELLYFIN_VERSION="$VERSION" \
  "$IMAGE" ./gradlew --no-daemon -q assembleProprietaryRelease

mkdir -p "$OUT"
cp "$REPO"/app/build/outputs/apk/proprietary/release/*.apk "$OUT/jellyfin-$VERSION.apk"
echo "$N" > "$COUNTER"
echo "built $OUT/jellyfin-$VERSION.apk from build @ $(git rev-parse --short HEAD) ($(branches | tr '\n' ' '))"
