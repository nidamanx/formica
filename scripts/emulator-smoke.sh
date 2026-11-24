#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Smoke test on a running emulator: install the debug APK, start the app, pass the welcome
# screen, load the sample data, open each section of the bottom bar and take a screenshot
# of it (also in the dark theme on Android 10+ and with large text), and fail on any crash
# or ANR (not responding) of the app.
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/emulator-smoke.sh [-h] <name> [apk]

  name  label used in the screenshot file names (e.g. api26-phone)
  apk   debug APK to install (default: app/build/outputs/apk/debug/app-debug.apk)

Screenshots: screenshots/<name>-welcome.png, screenshots/<name>-<section>.png, on
Android 10+ screenshots/<name>-dark-<section>.png, and with large text
screenshots/<name>-font13-<section>.png and <name>-font20-<section>.png.
Full log: screenshots/<name>-logcat.txt.
USAGE
}

if [[ ${1:-} == -h ]]; then
  usage
  exit 0
fi
if [[ $# -lt 1 ]]; then
  usage >&2
  exit 2
fi

name=$1
apk=${2:-app/build/outputs/apk/debug/app-debug.apk}
# Debug builds have their own package (".dev"); class names keep the namespace
package=io.github.nidamanx.formica.dev
namespace=io.github.nidamanx.formica
# Bottom bar labels in English, the emulator's language
sections=("Pantry:pantry" "Shopping list:shopping-list" "Expiry dates:expiry-dates")

mkdir -p screenshots
trap 'adb logcat -d > "screenshots/$name-logcat.txt" 2>/dev/null || true' EXIT

# A slow emulator often shows "System UI isn't responding" for other apps: hide those
# dialogs so they do not cover the screenshots. An ANR of this app is still detected
adb shell settings put global hide_error_dialogs 1 || true
close_system_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null || true
}

# "Boot completed" may come before the system can start apps: wait for the activity manager
for _ in $(seq 30); do
  adb shell cmd activity get-current-user >/dev/null 2>&1 && break
  sleep 5
done
sleep 10
adb install -r "$apk"
# Android 13+: granted beforehand, so "Start" does not open the permission dialog
if (( $(adb shell getprop ro.build.version.sdk | tr -d '\r') >= 33 )); then
  adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
fi
adb logcat -c || { sleep 2 && adb logcat -c; } || echo "logcat not cleared, continuing"

# Tap the view showing this exact text, found in the uiautomator dump
tap_text() {  # text
  local bounds x1 y1 x2 y2
  adb shell uiautomator dump /sdcard/formica-ui.xml >/dev/null
  bounds=$(adb shell cat /sdcard/formica-ui.xml | tr '>' '\n' | grep -F "text=\"$1\"" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | head -n 1 || true)
  [[ -n $bounds ]] || { echo "::error::\"$1\" not found on screen ($name)"; exit 1; }
  read -r x1 y1 x2 y2 <<<"$bounds"
  adb shell input tap "$(((x1 + x2) / 2))" "$(((y1 + y2) / 2))"
}

# The welcome screen of the first start, if shown: screenshot, then "Start"
pass_welcome() {  # prefix for the file names
  local ui
  adb shell uiautomator dump /sdcard/formica-ui.xml >/dev/null
  # Read first: "grep -q" stopping early would fail the pipe under pipefail
  ui=$(adb shell cat /sdcard/formica-ui.xml)
  [[ $ui == *'text="Start"'* ]] || return 0
  adb exec-out screencap -p > "screenshots/$1-welcome.png"
  tap_text "Start"
  sleep 3
}

# Sample data (debug builds only), once, after the welcome screen: the screenshots show
# a pantry in use. The activity closes itself when the data is written
demo_loaded=false
load_demo() {
  [[ $demo_loaded == true ]] && return 0
  adb shell am start -W -n "$package/$namespace.demo.DemoDataActivity"
  sleep 5
  adb logcat -d -s FormicaDemo:I | tail -n 1
  demo_loaded=true
}

# Start the app (again, with -S), check it is alive, screenshot every section
capture() {  # prefix for the file names
  local attempt section label file
  for attempt in 1 2 3; do
    adb shell am start -S -W -n "$package/$namespace.MainActivity" && break
    (( attempt < 3 )) || exit 1
    sleep 20
  done
  sleep 5
  adb shell pidof "$package"
  pass_welcome "$1"
  load_demo
  for section in "${sections[@]}"; do
    label=${section%%:*}
    file=${section#*:}
    tap_text "$label"
    sleep 2
    close_system_dialogs
    adb exec-out screencap -p > "screenshots/$1-$file.png"
  done
}

capture "$name"
# Dark theme: "cmd uimode night" exists from Android 10 (API 29)
if (( $(adb shell getprop ro.build.version.sdk | tr -d '\r') >= 29 )); then
  adb shell cmd uimode night yes
  sleep 2
  capture "$name-dark"
  adb shell cmd uimode night no
fi

# Large text (Settings > Display > Font size): nothing may be cut or overlap
for scale in 1.3 2.0; do
  adb shell settings put system font_scale "$scale"
  sleep 2
  capture "$name-font${scale/./}"
done
adb shell settings put system font_scale 1.0

adb shell dumpsys package "$package" | grep -i "permission" || true
if adb logcat -d -b crash | grep -F "$package"; then
  echo "::error::$package crashed on $name"
  exit 1
fi
if adb logcat -d -b main -b system | grep -F "ANR in $package"; then
  echo "::error::$package was not responding (ANR) on $name"
  exit 1
fi
echo "Smoke test passed on $name"
