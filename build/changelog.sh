#!/usr/bin/env bash
# Writes CHANGELOG.md from the fork's issues: every issue closed as completed with `status: done`
# (that is, confirmed working by the owner), grouped by the day it was closed, newest first.
# Issue titles are the lines, verbatim (Standard 1); the `bug` / `feature` / `server` labels pick the section.
#
#   build/changelog.sh > CHANGELOG.md
set -euo pipefail

FORK=majkinetor/jellyfin-android

cat <<'EOF'
# Changelog

What changed in this fork, once confirmed working on the owner's phone. A day's changes are in the [releases](https://github.com/majkinetor/jellyfin-android/releases) from that day on.

Generated from the [closed issues](https://github.com/majkinetor/jellyfin-android/issues?q=is%3Aissue+is%3Aclosed+label%3A%22status%3A+done%22); don't edit by hand.
EOF

# One line per issue: day, section, number, title (tab separated, newest first)
gh issue list -R "$FORK" --state closed --label 'status: done' --limit 500 \
  --json number,title,closedAt,stateReason,labels \
  --jq '.[] | select((.stateReason // "" | ascii_upcase) != "NOT_PLANNED")
        | [ .closedAt[:10],
            (if any(.labels[]; .name == "bug") then "1 Fixes"
             elif any(.labels[]; .name == "feature" or .name == "server") then "2 Features"
             else "3 Other" end),
            (.number | tostring), .title ] | @tsv' \
  | sort -t $'\t' -k1,1r -k2,2 -k3,3n \
  | awk -F '\t' -v fork="$FORK" '
      $1 != day { day = $1; section = ""; printf "\n## %s\n", day }
      $2 != section { section = $2; n = 0; printf "\n### %s\n\n", substr(section, 3) }
      { n++; printf "%d. %s ([#%s](https://github.com/%s/issues/%s))\n", n, $4, $3, fork, $3 }'
