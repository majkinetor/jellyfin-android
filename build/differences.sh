#!/usr/bin/env bash
# Rewrites the "What's different" part of README.md, between its two marker comments, from the fork's issues:
# every issue that is in a release (`status: testing` or `status: done`, not closed as not planned), by number,
# with its title as a heading and the first paragraph of its body (**Upstream:** / **This fork:**) below.
#
#   build/differences.sh [README.md]
set -euo pipefail

FORK=majkinetor/jellyfin-android
README="${1:-README.md}"
START='<!-- differences:start -->'
END='<!-- differences:end -->'

block="$(gh issue list -R "$FORK" --state all --limit 500 --json number,title,body,labels,stateReason \
  --jq 'map(select(any(.labels[]; .name == "status: testing" or .name == "status: done")
                   and (.stateReason // "" | ascii_upcase) != "NOT_PLANNED"))
        | sort_by(.number)[]
        | (.body | capture("^\\*\\*Upstream:\\*\\* (?<upstream>[^\r\n]*?) *\r?\n\\*\\*This fork:\\*\\* (?<fork>[^\r\n]*?) *(\r?\n|$)")) as $lead
        | "### [\(.title)](https://github.com/'"$FORK"'/issues/\(.number))\n\n"
          + (if any(.labels[]; .name == "status: testing") then "*Being tested.*\n\n" else "" end)
          + "**Upstream:** \($lead.upstream)  \n**This fork:** \($lead.fork)\n"')"

# Replace what is between the markers, keeping them
# (the block goes through the environment: awk -v would interpret its backslashes)
BLOCK="$block" awk -v start="$START" -v end="$END" '
  $0 == start { print; print ""; print ENVIRON["BLOCK"]; print ""; skip = 1; next }
  $0 == end   { skip = 0 }
  !skip       { print }' "$README" > "$README.tmp"
mv "$README.tmp" "$README"
