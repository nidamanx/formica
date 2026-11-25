#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Fails if the APK requests a permission missing from config/allowed-permissions.txt,
# or declares a permission of its own other than those listed there.
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/check-permissions.sh [-h] <apk> [allowed-list]

  apk           APK to check (needs aapt2 from the Android SDK build-tools)
  allowed-list  default: config/allowed-permissions.txt; ${applicationId} in it stands
                for the package name of the APK
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

apk=$1
allowed=${2:-config/allowed-permissions.txt}
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}

aapt2=$(find "$sdk/build-tools" -mindepth 2 -maxdepth 2 -name aapt2 2>/dev/null | sort -V | tail -n 1 || true)
if [[ -z $aapt2 ]]; then
  echo "::error::aapt2 not found in $sdk/build-tools" >&2
  exit 1
fi

dump=$("$aapt2" dump permissions "$apk")
package=$(sed -n 's/^package: //p' <<<"$dump")
requested=$(grep -E '^uses-permission' <<<"$dump" | sed -E "s/.*name='([^']+)'.*/\1/" | sort -u || true)
declared=$(sed -n 's/^permission: //p' <<<"$dump" | sort -u)
expected=$(grep -vE '^[[:space:]]*(#|$)' "$allowed" | sed "s/\${applicationId}/$package/g" | sort -u)

echo "Package: $package"
indent() { while IFS= read -r line; do echo "  ${line:-(none)}"; done; }
echo "Requested permissions:"
indent <<<"$requested"
echo "Declared permissions:"
indent <<<"$declared"

status=0
extra=$(comm -23 <(printf '%s\n' "$requested" | sed '/^$/d') <(printf '%s\n' "$expected"))
if [[ -n $extra ]]; then
  while IFS= read -r perm; do
    echo "::error::$apk requests $perm, which is not in $allowed"
  done <<<"$extra"
  status=1
fi
extra=$(comm -23 <(printf '%s\n' "$declared" | sed '/^$/d') <(printf '%s\n' "$expected"))
if [[ -n $extra ]]; then
  while IFS= read -r perm; do
    echo "::error::$apk declares $perm, which is not in $allowed"
  done <<<"$extra"
  status=1
fi
exit "$status"
