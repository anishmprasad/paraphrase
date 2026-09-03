#!/usr/bin/env bash
# Renders every brand SVG to PNG at the sizes the stores and GitHub expect.
# Uses headless Chrome so the exported pixels match what a browser shows.
set -euo pipefail
cd "$(dirname "$0")"

CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
[ -x "$CHROME" ] || { echo "Chrome not found; set CHROME=/path/to/chrome"; exit 1; }
mkdir -p exports
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# render <svg> <width> <height> <out.png> [transparent]
render() {
  local svg=$1 w=$2 h=$3 out=$4 transparent=${5:-}
  local bg="#ffffff"
  local flag=(--hide-scrollbars)
  if [ -n "$transparent" ]; then bg="transparent"; flag+=(--default-background-color=00000000); fi
  cat > "$TMP/page.html" <<HTML
<!doctype html><meta charset="utf-8">
<style>html,body{margin:0;padding:0;width:${w}px;height:${h}px;overflow:hidden;background:$bg}
svg{display:block;width:${w}px;height:${h}px;color:#4F46E5}</style>
$(cat "$svg")
HTML
  "$CHROME" --headless --disable-gpu --force-device-scale-factor=1 \
    "${flag[@]}" --window-size="$w,$h" --screenshot="exports/$out" \
    "file://$TMP/page.html" >/dev/null 2>&1
  echo "  exports/$out  (${w}x${h})"
}

echo "rendering:"
render icon.svg                    512  512  icon-512.png
render icon.svg                   1024 1024  icon-1024.png
render icon-rounded.svg            512  512  icon-rounded-512.png transparent
render mark.svg                    512  512  mark-512.png          transparent
render lockup-horizontal.svg      1280  320  lockup-1280.png       transparent
render lockup-horizontal-light.svg 1280 320  lockup-light-1280.png transparent
render banner-1024x500.svg        1024  500  feature-1024x500.png
render social-1280x640.svg        1280  640  social-1280x640.png
echo "done."
