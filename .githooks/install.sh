#!/usr/bin/env bash
# Activates repo-managed git hooks for this clone.
#
# Run once after cloning:
#   ./.githooks/install.sh
#
# This sets core.hooksPath to .githooks so every developer in the team
# uses the same enforced policies (currently: CHANGELOG.md must be
# updated for substantive commits — see .githooks/commit-msg).
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

git config core.hooksPath .githooks

# Make every file in .githooks executable except docs and this installer.
for f in .githooks/*; do
    base="$(basename "$f")"
    case "$base" in
        README.md|install.sh) continue ;;
    esac
    chmod +x "$f"
done

echo "[githooks] core.hooksPath = .githooks"
echo "[githooks] hooks habilitados:"
ls -1 .githooks | grep -v -E '^(install\.sh|README\.md)$' | sed 's/^/  - /'
