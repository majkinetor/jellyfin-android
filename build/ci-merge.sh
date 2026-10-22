#!/usr/bin/env bash
# CI: recreates `build` as upstream/master + every fix/* and own/* branch of the fork, and works out the version.
# Run inside a full clone of the fork with `build` checked out. Writes GitHub step outputs:
#   changed=true|false   whether the merged tree differs from the current `build`
#   version=X.Y.Z-local.N
set -euo pipefail

FORK=majkinetor/jellyfin-android
out() { echo "$1" >> "${GITHUB_OUTPUT:-/dev/stdout}"; }

git config user.name 'github-actions[bot]'
git config user.email '41898282+github-actions[bot]@users.noreply.github.com'

git remote add upstream https://github.com/jellyfin/jellyfin-android.git 2>/dev/null || true
git fetch -q upstream master --tags
git fetch -q origin '+refs/heads/fix/*:refs/remotes/origin/fix/*' '+refs/heads/own/*:refs/remotes/origin/own/*'

old_tree="$(git rev-parse 'HEAD^{tree}')"
branches="$(git for-each-ref --format='%(refname:short)' refs/remotes/origin/fix refs/remotes/origin/own)"
echo "merging upstream/master + $(echo "$branches" | tr '\n' ' ')"

git checkout -q -B build upstream/master
# shellcheck disable=SC2086
if ! git merge -q --no-edit $branches; then
  echo "::error::A branch no longer merges with upstream/master; rebase it locally (build/build.sh sync)"
  git merge --abort || true
  exit 1
fi

if [ "$(git rev-parse 'HEAD^{tree}')" = "$old_tree" ]; then out changed=false; else out changed=true; fi

# Version: newest upstream release tag + "-local.<n>", n one above the newest release of that tag
tag="$(git tag --sort=-v:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -1)"
base="${tag#v}"
last="$(gh release list -R "$FORK" --limit 200 --json tagName --jq '.[].tagName' \
  | sed -n "s/^v${base//./\\.}-local\.\([0-9]*\)$/\1/p" | sort -n | tail -1)"
n=$(( ${last:-0} + 1 ))
if [ "$n" -gt 98 ]; then echo "::error::local.$n would pass the versionCode limit (98)"; exit 1; fi
out "version=$base-local.$n"
