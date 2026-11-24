#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Store screenshots on a running emulator (Android 13+, the debug app already installed by
# emulator-smoke.sh): for each language, a fresh app with the sample data, then the pantry,
# the detail of the tomato passata, the shopping list, the expiry dates and the pantry in
# the dark theme. The status bar is in demo mode (fixed time, full battery, no icons).
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/store-screenshots.sh [-h]

Run after scripts/emulator-smoke.sh, on Android 13 or later (per-app language).
Screenshots: screenshots/store/<locale>/1.png … 5.png, in the layout of
fastlane/metadata/android/<locale>/images/phoneScreenshots/.
USAGE
}

if [[ ${1:-} == -h ]]; then
  usage
  exit 0
fi
if [[ $# -gt 0 ]]; then
  usage >&2
  exit 2
fi

package=io.github.nidamanx.formica.dev
namespace=io.github.nidamanx.formica
# App language : resource folder suffix : fastlane folder
languages=("en::en-US" "it:-it:it-IT" "fr:-fr:fr-FR" "de:-de:de-DE" "es:-es:es-ES")
phase=store

mkdir -p screenshots/store
# shellcheck source=scripts/emulator-common.sh
source "$(dirname "$0")/emulator-common.sh"

if (( $(adb shell getprop ro.build.version.sdk | tr -d '\r') < 33 )); then
  echo "::error::Store screenshots need Android 13 or later (per-app language)"
  exit 1
fi

# One string of the app, as the user sees it (Android escapes \' and \" removed)
res_string() {  # file, name
  sed -n "s:.*<string name=\"$2\">\(.*\)</string>.*:\1:p" "$1" | head -n 1 | sed "s/\\\\\(['\"]\)/\1/g"
}

shot() {  # file
  sleep 2
  close_system_dialogs
  adb exec-out screencap -p > "$1"
}

start_app() {
  adb shell am start -S -W -n "$package/$namespace.MainActivity" >/dev/null
  sleep 4
}

# Scroll the list down until the text is on screen (the passata is not near the top)
scroll_to_text() {  # text
  local _
  for _ in 1 2 3 4 5 6; do
    [[ -n $(find_text "$1") ]] && return 0
    adb shell input swipe 540 1700 540 900 300
    sleep 1
  done
}

# Clean status bar: fixed time, full battery, Wi-Fi only, no notification icons
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 1000
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile hide
demo notifications -e visible false

for entry in "${languages[@]}"; do
  IFS=: read -r lang suffix locale <<<"$entry"
  strings=app/src/main/res/values$suffix/strings.xml
  demo_strings=app/src/debug/res/values$suffix/demo_strings.xml
  out=screenshots/store/$locale
  mkdir -p "$out"
  phase=store-$locale

  # A fresh app in this language: the sample data get the names of this language
  adb shell pm clear "$package" >/dev/null
  adb shell cmd locale set-app-locales "$package" --locales "$lang"
  adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
  start_app
  tap_text "$(res_string "$strings" action_start)"
  sleep 2
  adb shell am start -W -n "$package/$namespace.demo.DemoDataActivity" >/dev/null
  sleep 5

  shot "$out/1.png"
  passata=$(res_string "$demo_strings" demo_passata)
  scroll_to_text "$passata"
  tap_text "$passata"
  shot "$out/2.png"
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  tap_text "$(res_string "$strings" nav_shopping_list)"
  shot "$out/3.png"
  tap_text "$(res_string "$strings" nav_expiries)"
  shot "$out/4.png"
  adb shell cmd uimode night yes
  start_app
  shot "$out/5.png"
  adb shell cmd uimode night no
done

demo exit
echo "Store screenshots written to screenshots/store/"
