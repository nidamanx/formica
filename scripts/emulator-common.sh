# shellcheck shell=bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Helpers shared by the emulator scripts (sourced, not run): close system dialogs, find and
# tap a view by its text. They expect $phase (label of the current step, used in the
# names of the failure files) and a screenshots/ folder.
# shellcheck disable=SC2154  # phase is set by the sourcing script

close_system_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null || true
}

# Bounds of the lowest view showing this exact text, from a fresh uiautomator dump. The
# lowest, because the same word can appear higher up too (a product in the "Pantry"
# location shows "Pantry" on its card): the bottom bar is at the bottom
find_text() {  # text
  adb shell uiautomator dump /sdcard/formica-ui.xml >/dev/null
  adb shell cat /sdcard/formica-ui.xml | tr '>' '\n' | grep -F "text=\"$1\"" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' \
    | sort -n -k2 | tail -n 1 || true
}

# Tap the view showing this exact text; if it is missing, the screen and its layout are kept
# as <phase>-FAILED.png/.xml and the annotation names the phase
tap_text() {  # text
  local bounds x1 y1 x2 y2
  bounds=$(find_text "$1")
  if [[ -z $bounds ]]; then
    adb exec-out screencap -p > "screenshots/$phase-FAILED.png" || true
    adb shell cat /sdcard/formica-ui.xml > "screenshots/$phase-FAILED.xml" || true
    echo "::error::\"$1\" not found on screen ($phase; see screenshots/$phase-FAILED.png)"
    exit 1
  fi
  read -r x1 y1 x2 y2 <<<"$bounds"
  adb shell input tap "$(((x1 + x2) / 2))" "$(((y1 + y2) / 2))"
}
