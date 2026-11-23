#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Runs ./gradlew with the given arguments, logging to <log file>. When the run fails only
# because a dependency or plugin could not be downloaded (a network error on the runner),
# it waits and runs once more; any other failure (compiler, lint, tests) stops at once.
# On the final failure the errors become an annotation (scripts/report-gradle-failure.sh).
set -euo pipefail

usage() {
  echo "Usage: scripts/run-gradle.sh [-h] <log file> <gradle arguments...>"
}

if [[ ${1:-} == -h ]]; then
  usage
  exit 0
fi
if [[ $# -lt 2 ]]; then
  usage >&2
  exit 2
fi

log=$1
shift
wait_seconds=${FORMICA_GRADLE_RETRY_WAIT:-30}
# Messages of a failed download, never of a code problem
network_error='Could not resolve|could not resolve plugin artifact|Could not GET|Could not HEAD|Read timed out|Connect timed out|Connection reset|Received status code 5[0-9][0-9]|Remote host terminated the handshake'

run() {
  ./gradlew --no-daemon "$@" 2>&1 | tee "$log"
}

if run "$@"; then
  exit 0
fi
if grep -Eq "$network_error" "$log"; then
  echo "::warning title=Gradle download failed::Network error while downloading, retrying in ${wait_seconds}s."
  sleep "$wait_seconds"
  if run "$@"; then
    exit 0
  fi
fi
scripts/report-gradle-failure.sh "$log"
exit 1
