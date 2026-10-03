#!/usr/bin/env bash
set -euo pipefail

repo="okonnu/skynet-android-tv"
latest_tag=$(gh release list --repo "$repo" --limit 100 \
  --json tagName,isPrerelease \
  --jq '.[] | select(.isPrerelease == false and (.tagName | test("^v[0-9]"))) | .tagName' \
  | sort -V | tail -n 1)
test -n "$latest_tag"

scratch=$(mktemp -d)
cleanup() {
  if [[ -f "$scratch/Skynet.apk" ]]; then
    rm -- "$scratch/Skynet.apk"
  fi
  rmdir -- "$scratch"
}
trap cleanup EXIT

gh release download "$latest_tag" --repo "$repo" --pattern Skynet.apk --dir "$scratch"
test -s "$scratch/Skynet.apk"

if gh release view skynet-current --repo "$repo" >/dev/null 2>&1; then
  gh release upload skynet-current "$scratch/Skynet.apk" --repo "$repo" --clobber
  gh release edit skynet-current --repo "$repo" \
    --title "Skynet current download" \
    --notes "Current Skynet APK: $latest_tag. For version history, see the numbered Skynet releases."
else
  gh release create skynet-current "$scratch/Skynet.apk" --repo "$repo" \
    --target main \
    --title "Skynet current download" \
    --notes "Current Skynet APK: $latest_tag. For version history, see the numbered Skynet releases." \
    --prerelease
fi

gh release view skynet-current --repo "$repo" --json assets \
  --jq '.assets[] | select(.name == "Skynet.apk") | .url'
