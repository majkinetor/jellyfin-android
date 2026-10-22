#!/usr/bin/env bash
# Publishes the newest built APK as a GitHub release of the fork.
#
#   build/release.sh "what changed, one line per item"
#
# Pushes fix/*, own/* and build to origin first; the tag v<version> points at the build commit.
set -euo pipefail

JELLY="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$JELLY/jellyfin-android"
FORK=majkinetor/jellyfin-android
NOTES="${1:?usage: build/release.sh \"what changed\"}"

APK="$(ls -t "$JELLY"/out/jellyfin-*.apk | head -1)"
VERSION="$(basename "$APK" .apk)"
VERSION="${VERSION#jellyfin-}"
TAG="v$VERSION"

cd "$REPO"
# shellcheck disable=SC2046
git push -q --force-with-lease origin $(git for-each-ref --format='%(refname:short)' refs/heads/fix refs/heads/own) build
COMMIT="$(git rev-parse build)"

BODY="$(cat <<EOF
$NOTES

**Install:** this APK is signed with the fork's own key. Uninstall the official Jellyfin app first, the first time only (that removes its login and settings); later releases install over this one.

Built from [\`build\`](https://github.com/$FORK/tree/$COMMIT) @ \`${COMMIT:0:8}\`: upstream \`master\` + $(git for-each-ref --format='`%(refname:short)`' refs/heads/fix refs/heads/own | paste -sd ' ' -).
EOF
)"

gh release create "$TAG" "$APK" -R "$FORK" --target "$COMMIT" --title "$VERSION" --notes "$BODY"
