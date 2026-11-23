#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Turns the errors of a failed Gradle run into a GitHub Actions error annotation.
# Job logs can be read only when logged in, also on a public repository; annotations
# can be read by anyone through the API (check-runs/<job id>/annotations).
set -euo pipefail

if [[ ${1:-} == -h || $# -ne 1 ]]; then
  echo "Usage: scripts/report-gradle-failure.sh [-h] <gradle log file>"
  [[ ${1:-} == -h ]] && exit 0
  exit 2
fi

# Compiler and lint errors, failed tests, and the "What went wrong" block
summary=$(awk '
  /^\* What went wrong:/ { block = 1; print; next }
  block && /^\* (Try|Exception is):/ { block = 0 }
  block { print; next }
  /^e: / || /: Error: / || /FAILED$/ || /^> Task .* FAILED/ || /tests completed, .* failed/ { print }
' "$1" | head -n 60)
[[ -n $summary ]] || summary=$(tail -n 30 "$1")
# Annotation messages are one line: newlines are encoded as %0A
summary=${summary//'%'/'%25'}
summary=${summary//$'\r'/}
summary=${summary//$'\n'/'%0A'}
echo "::error title=Gradle build failed::$summary"
