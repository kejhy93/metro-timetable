#!/usr/bin/env bash
set -euo pipefail

latest=$(git tag --sort=-v:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -1)

if [ -z "$latest" ]; then
  latest="v0.0.0"
fi

major=$(echo "$latest" | cut -d. -f1 | tr -d 'v')
minor=$(echo "$latest" | cut -d. -f2)
patch=$(echo "$latest" | cut -d. -f3)

echo "Current tag: $latest"
echo ""
echo "Release type?"
echo "  1) patch  → v$major.$minor.$((patch + 1))"
echo "  2) minor  → v$major.$((minor + 1)).0"
echo "  3) major  → v$((major + 1)).0.0"
echo ""
read -rp "Choice [1/2/3]: " choice

case "$choice" in
  1) next="v$major.$minor.$((patch + 1))" ;;
  2) next="v$major.$((minor + 1)).0" ;;
  3) next="v$((major + 1)).0.0" ;;
  *) echo "Invalid choice"; exit 1 ;;
esac

echo ""
read -rp "Tag and push $next? [y/N]: " confirm

if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
  echo "Aborted."
  exit 0
fi

git tag "$next"
git push origin "$next"
echo "Tagged and pushed $next"
