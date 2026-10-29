#!/usr/bin/env bash
# Verifies the citation lines in the guides, examples and module pages.
#
# A citation line has the form
#   Source: `<path>`, `<Symbol>` [, `<Symbol>` ...] (quoted|adapted)
# or, for a path alone,
#   Source: `<path>` (quoted|adapted)
# <path> is relative to the repository root. A <Symbol> is checked by its simple name: for
# `Type.member(args)` the name `member` must occur in the cited file. A symbol that is not a
# Java-style identifier path is searched for literally. Line numbers are not part of the format.
#
# Usage: check.sh [directory ...]   (default: docs/guides docs/examples docs/modules)
# Env:   CITATIONS_ROOT  repository root that cited paths resolve against (default: this repo)
# Exit:  0 when every citation resolves, 1 otherwise.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="${CITATIONS_ROOT:-$(cd "$script_dir/../.." && pwd)}"

if [ "$#" -gt 0 ]; then
  dirs=("$@")
else
  dirs=("$root/docs/guides" "$root/docs/examples" "$root/docs/modules")
fi

ident='[A-Za-z_$][A-Za-z0-9_$]*'
java_symbol="^${ident}([.#]${ident})*(\\(.*\\))?$"

errors=0
checked=0

check_file() {
  local md="$1" in_fence=0 line
  while IFS= read -r line || [ -n "$line" ]; do
    if [[ "$line" =~ ^[[:space:]]*(\`\`\`|~~~) ]]; then
      in_fence=$((1 - in_fence))
      continue
    fi
    [ "$in_fence" -eq 1 ] && continue
    [[ "$line" == Source:* ]] || continue

    local tokens=()
    mapfile -t tokens < <(printf '%s\n' "$line" | grep -o '`[^`]*`' | sed 's/^`//; s/`$//' || true)
    [ "${#tokens[@]}" -ge 1 ] || continue

    local path="${tokens[0]}"
    checked=$((checked + 1))
    if [ ! -e "$root/$path" ]; then
      echo "MISSING: $md: $path"
      errors=$((errors + 1))
      continue
    fi

    local i sym name
    for ((i = 1; i < ${#tokens[@]}; i++)); do
      sym="${tokens[$i]}"
      if [[ "$sym" =~ $java_symbol ]]; then
        name="${sym%%(*}"
        name="${name##*[.#]}"
      else
        name="$sym"
      fi
      if [ -d "$root/$path" ]; then
        grep -rqF -- "$name" "$root/$path" || { echo "STALE: $md: $path lacks $sym"; errors=$((errors + 1)); }
      else
        grep -qF -- "$name" "$root/$path" || { echo "STALE: $md: $path lacks $sym"; errors=$((errors + 1)); }
      fi
    done
  done < "$md"
}

for dir in "${dirs[@]}"; do
  [ -d "$dir" ] || continue
  while IFS= read -r -d '' md; do
    check_file "$md"
  done < <(find "$dir" -type f -name '*.md' -print0 | sort -z)
done

if [ "$errors" -gt 0 ]; then
  echo "docs-citations: $errors problem(s) in $checked citation line(s)" >&2
  exit 1
fi
echo "docs-citations: $checked citation line(s) resolve"
