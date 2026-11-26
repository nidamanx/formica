#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Runs ./gradlew with the given arguments, logging to <log file>. When a run fails only
# because a dependency or plugin could not be downloaded (a network error on the runner),
# it waits and runs again, up to three runs in all; any other failure (compiler, lint,
# tests) stops at once. Repositories sometimes fail for a minute or more, so the waits grow.
# The later runs ask the repositories again (--refresh-dependencies: no remembered
# "not found") and log every request (--info), so that a failure names the repository.
# On the final failure the errors become an annotation (scripts/report-gradle-failure.sh).
set -euo pipefail

usage() {
  echo "Usage: scripts/run-gradle.sh [-h] <log file> <gradle arguments...>"
  echo "FORMICA_GRADLE_RETRY_WAIT: seconds before the second run (default 30; the third waits 3 times as long)"
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
max_runs=3
# Messages of a failed download, never of a code problem
network_error='Could not resolve|could not resolve plugin artifact|Could not GET|Could not HEAD|Read timed out|Connect timed out|Connection reset|Received status code 5[0-9][0-9]|Remote host terminated the handshake'

run() {
  ./gradlew --no-daemon "$@" 2>&1 | tee "$log"
}

# Lines of the --info log about what went wrong, as one annotation readable through the API
report_requests() {
  local unresolved artifact_lines="" errors missing requests name
  # Group:name of the artifacts Gradle could not resolve, e.g. from
  # "could not resolve plugin artifact 'g:g.gradle.plugin:1.0'" or "Could not resolve g:a:1.0."
  unresolved=$(grep -oE "(could not resolve plugin artifact '|Could not resolve )[A-Za-z0-9._-]+:[A-Za-z0-9._-]+" "$log" |
    sed -E "s/^(could not resolve plugin artifact '|Could not resolve )//" | sort -u | head -n 5 || true)
  # Every request for those artifacts, in any repository: which one answered what
  while IFS= read -r name; do
    [[ -n $name ]] || continue
    artifact_lines+=$(grep -F "${name##*:}" "$log" | grep -E "HTTP|Resource missing|Could not|status code|timed out" |
      sort -u | head -n 10 || true)
    artifact_lines+=$'\n'
  done <<<"$unresolved"
  # Real errors; "Resource missing" is normal for most requests (Google has no JetBrains artifacts)
  errors=$(grep -E "Could not (GET|HEAD)|Received status code|timed out|Connection reset|handshake" "$log" |
    sort -u | head -n 20 || true)
  missing=$(grep -E "Resource missing" "$log" | sort -u | head -n 10 || true)
  requests=$(printf '%s\n%s\n%s' "$artifact_lines" "$errors" "$missing" | sed '/^$/d' | awk '!seen[$0]++')
  if [[ -n $requests ]]; then
    requests=${requests//'%'/'%25'}
    requests=${requests//$'\r'/}
    requests=${requests//$'\n'/'%0A'}
    echo "::warning title=Gradle requests::$requests"
  fi
}

if run "$@"; then
  exit 0
fi
attempt=1
while grep -Eq "$network_error" "$log" && ((attempt < max_runs)); do
  echo "::warning title=Gradle download failed::Network error while downloading (run $attempt of $max_runs), retrying in ${wait_seconds}s."
  sleep "$wait_seconds"
  attempt=$((attempt + 1))
  wait_seconds=$((wait_seconds * 3))
  if run --refresh-dependencies --info "$@"; then
    exit 0
  fi
done
if ((attempt > 1)); then
  report_requests
fi
scripts/report-gradle-failure.sh "$log"
exit 1
