#!/usr/bin/env bash
# Whole-project static audit in a single pass: unused/missing imports, string
# resource usage both ways, hardcoded UI text, and icon availability.
set -uo pipefail
cd "$(dirname "$0")/.."
SRC=app/src/main/java
RES=app/src/main/res
ICONS_JAR=$(find "$HOME/.gradle/caches" -name 'material-icons-*-release-api.jar' 2>/dev/null | tr '\n' ' ')

echo "===== 1. imports referenced nowhere in the file ====="
for f in $(rg -l '^import ' "$SRC" -g '*.kt'); do
  rg -oN '^import .*' "$f" | while read -r imp; do
    name="${imp##*.}"
    [ "$name" = "*" ] && continue
    case "$name" in getValue|setValue) continue ;; esac   # required for `by` delegates
    if ! rg -qN "\b${name}\b" <(rg -vN '^import ' "$f"); then
      echo "$f :: $imp"
    fi
  done
done

echo
echo "===== 2. simple names used but never imported (candidate missing imports) ====="
for f in $(rg -l '^package ' "$SRC" -g '*.kt'); do
  body=$(rg -vN '^import |^package ' "$f")
  rg -oN '\bIcons\.(AutoMirrored\.)?Filled\.[A-Z][A-Za-z0-9]*' <<<"$body" | sed 's/.*\.//' | sort -u | while read -r icon; do
    rg -qN "^import .*\.${icon}$" "$f" || echo "$f :: icon Icons.Filled.${icon} used, no matching import"
  done
done

echo
echo "===== 3. R.string.<name> used in code but missing from values/strings.xml ====="
rg -oN 'R\.string\.[a-zA-Z0-9_]+' "$SRC" -g '*.kt' | sed 's/.*R\.string\.//' | sort -u > /tmp/used_strings.txt
rg -oN 'name="[a-zA-Z0-9_]+"' "$RES/values/strings.xml" | sed 's/name="//;s/"//' | sort -u > /tmp/defined_strings.txt
comm -23 /tmp/used_strings.txt /tmp/defined_strings.txt | grep -v '^ok$' | sed 's/^/MISSING in values: /'

echo
echo "===== 4. strings defined in values/ but missing in values-ar/ ====="
rg -oN 'name="[a-zA-Z0-9_]+"' "$RES/values-ar/strings.xml" | sed 's/name="//;s/"//' | sort -u > /tmp/ar_strings.txt
comm -23 /tmp/defined_strings.txt /tmp/ar_strings.txt | sed 's/^/MISSING in values-ar: /'

echo
echo "===== 5. strings defined but never referenced in code or layouts ====="
rg -oN 'name="[a-zA-Z0-9_]+"' "$RES/values/strings.xml" | sed 's/name="//;s/"//' | sort -u | while read -r s; do
  rg -qN "R\.string\.${s}\b|@string/${s}\b" "$SRC" "$RES" -g '!strings.xml' || echo "UNUSED: $s"
done

echo
echo "===== 6. hardcoded user-visible English text in Compose UI ====="
rg -nN 'Text\(\s*"[A-Z][^"]{2,}"|placeholder = \{ Text\("[^"]+"|contentDescription = "[^"]+"|label = \{[^}]*"[^"]+"' "$SRC" -g '*.kt' | rg -v 'R\.string|Text\("' || true

echo
echo "===== 7. every Compose icon referenced must exist in the icon jars ====="
for jar in $ICONS_JAR; do unzip -l "$jar" 2>/dev/null | rg -oN '(filled|automirrored/filled)/[A-Za-z0-9]+Kt\.class' | sed 's#.*/##;s/Kt.class//'; done | sort -u > /tmp/icons.txt
rg -oN '\bIcons\.(AutoMirrored\.)?Filled\.[A-Za-z0-9]+' "$SRC" -g '*.kt' | sed 's/.*\.//' | sort -u | while read -r icon; do
  rg -qxF "$icon" /tmp/icons.txt || echo "NO SUCH ICON: $icon"
done

echo
echo "===== 8. drawable references used in code but missing on disk ====="
rg -oN 'R\.drawable\.[a-zA-Z0-9_]+' "$SRC" -g '*.kt' | sed 's/.*R\.drawable\.//' | sort -u | while read -r d; do
  ls "$RES"/drawable*/"$d".* >/dev/null 2>&1 || echo "MISSING DRAWABLE: $d"
done
