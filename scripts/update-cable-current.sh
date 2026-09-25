#!/usr/bin/env bash
set -euo pipefail

repo="okonnu/skynet-android-tv"
latest_tag=$(gh release list --repo "$repo" --limit 100 \
  --json tagName,isPrerelease \
  --jq '.[] | select(.isPrerelease == false and (.tagName | startswith("cable-v"))) | .tagName' \
  | sort -V | tail -n 1)
test -n "$latest_tag"

scratch=$(mktemp -d)
cleanup() {
  if [[ -f "$scratch/Cable.apk" ]]; then
    rm -- "$scratch/Cable.apk"
  fi
  rmdir -- "$scratch"
}
trap cleanup EXIT

gh release download "$latest_tag" --repo "$repo" --pattern Cable.apk --dir "$scratch"
test -s "$scratch/Cable.apk"

if gh release view cable-current --repo "$repo" >/dev/null 2>&1; then
  gh release upload cable-current "$scratch/Cable.apk" --repo "$repo" --clobber
  gh release edit cable-current --repo "$repo" \
    --title "Cable current download" \
    --notes "Current Cable APK: $latest_tag. For version history, see the numbered Cable releases."
else
  gh release create cable-current "$scratch/Cable.apk" --repo "$repo" \
    --target main \
    --title "Cable current download" \
    --notes "Current Cable APK: $latest_tag. For version history, see the numbered Cable releases." \
    --prerelease
fi

gh release view cable-current --repo "$repo" --json assets \
  --jq '.assets[] | select(.name == "Cable.apk") | .url'
