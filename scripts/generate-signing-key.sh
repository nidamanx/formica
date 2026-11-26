#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Creates a signing key for Formica on the maintainer's own computer, with openssl only.
# Output: the keystore, encrypted and base64-encoded, for the GitHub secret, and the
# public certificate fingerprint. The plain key exists only in a private temporary
# directory (in RAM when /dev/shm is available) and is deleted at exit.
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/generate-signing-key.sh [-h] release|debug [output-dir]

  release     the key that signs published releases (secrets KEYSTORE_PASSWORD,
              KEYSTORE_BASE64). Create it once: it can never be replaced.
  debug       the key that signs CI test builds, "Formica-D" (secrets
              DEBUG_KEYSTORE_PASSWORD, DEBUG_KEYSTORE_BASE64)
  output-dir  where to write the two files (default: current directory)

Asks twice for the password (at least 24 characters): keep it somewhere safe, outside
the repository, before pasting it. Writes:
  formica-<kind>-keystore.b64           encrypted keystore, one line of base64 (secret)
  formica-<kind>-certificate-sha256.txt certificate fingerprint (public)
Needs openssl 3 and base64. Never run it on a shared machine or a CI runner.
USAGE
}

if [[ ${1:-} == -h || ${1:-} == --help ]]; then
  usage
  exit 0
fi
if [[ $# -lt 1 || $# -gt 2 || ! ${1} =~ ^(release|debug)$ ]]; then
  usage >&2
  exit 2
fi

kind=$1
out_dir=${2:-.}
if [[ $kind == release ]]; then
  alias_name=formica
  subject="/CN=Formica"
  secret_prefix=""
else
  alias_name=formica-debug
  subject="/CN=Formica debug"
  secret_prefix=DEBUG_
fi
b64_file="$out_dir/formica-$kind-keystore.b64"
fingerprint_file="$out_dir/formica-$kind-certificate-sha256.txt"

command -v openssl >/dev/null || { echo "Error: openssl not found" >&2; exit 1; }
[[ $(openssl version) == "OpenSSL 3"* ]] || { echo "Error: OpenSSL 3 is required ($(openssl version))" >&2; exit 1; }
[[ -d $out_dir && -w $out_dir ]] || { echo "Error: $out_dir is not a writable directory" >&2; exit 1; }
for f in "$b64_file" "$fingerprint_file"; do
  [[ ! -e $f ]] || { echo "Error: $f already exists: move it away first" >&2; exit 1; }
done

umask 077
tmp_base=/dev/shm
[[ -d $tmp_base && -w $tmp_base ]] || tmp_base=${TMPDIR:-/tmp}
work=$(mktemp -d "$tmp_base/formica-key.XXXXXX")
cleanup() {
  if [[ -d $work ]]; then
    find "$work" -type f -exec shred -u {} + 2>/dev/null || true
    rm -rf "$work"
  fi
}
trap cleanup EXIT
# Ctrl-C or kill: stop here (EXIT then cleans up), never go on with the next command
trap 'exit 130' INT TERM

# The password reaches openssl through a file descriptor: never on the command line
# (visible in the process list) nor in the environment
read_password() {  # prompt
  local value
  if [[ -t 0 ]]; then
    IFS= read -rs -p "$1" value
    echo >&2
  else
    IFS= read -r value
  fi
  printf '%s' "$value"
}
password=$(read_password "Password for the $kind key: ")
repeat=$(read_password "Same password again: ")
[[ $password == "$repeat" ]] || { echo "Error: the two passwords differ" >&2; exit 1; }
unset repeat
(( ${#password} >= 24 )) || { echo "Error: the password must have at least 24 characters" >&2; exit 1; }

echo "Creating the $kind key (RSA 4096, certificate valid about 27 years)..."
# openssl prints progress dots while creating the key: show its output only on failure
if ! openssl req -x509 -newkey rsa:4096 -sha256 -days 10000 -noenc -subj "$subject" \
    -keyout "$work/key.pem" -out "$work/cert.pem" 2>"$work/req.log"; then
  cat "$work/req.log" >&2
  echo "Error: openssl could not create the key" >&2
  exit 1
fi
openssl pkcs12 -export -inkey "$work/key.pem" -in "$work/cert.pem" -name "$alias_name" \
  -out "$work/keystore.p12" -passout fd:3 3<<<"$password"
shred -u "$work/key.pem"

# Same encryption the workflows reverse: openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000
openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -in "$work/keystore.p12" \
  -out "$work/keystore.enc" -pass fd:3 3<<<"$password"
base64 -w 0 "$work/keystore.enc" > "$work/keystore.b64"

# Check the round trip exactly as CI will do it, and that the keystore opens
base64 -d "$work/keystore.b64" | openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 \
  -out "$work/check.p12" -pass fd:3 3<<<"$password"
cmp -s "$work/keystore.p12" "$work/check.p12" || { echo "Error: round trip check failed" >&2; exit 1; }
openssl pkcs12 -in "$work/keystore.p12" -noout -passin fd:3 3<<<"$password" \
  || { echo "Error: the keystore does not open with this password" >&2; exit 1; }
unset password

fingerprint=$(openssl x509 -in "$work/cert.pem" -noout -fingerprint -sha256 | sed 's/^.*=//')
install -m 600 "$work/keystore.b64" "$b64_file"
printf '%s\n' "$fingerprint" > "$fingerprint_file"

cat <<DONE

Done. Certificate SHA-256 (public):
  $fingerprint

Next steps:
  1. Keep a copy of $b64_file and of the password somewhere safe, outside the repository.
  2. In github.com/nidamanx/formica: Settings > Secrets and variables > Actions, create
     ${secret_prefix}KEYSTORE_BASE64 (content of that file) and ${secret_prefix}KEYSTORE_PASSWORD (the password).
  3. Delete the file: shred -u $b64_file
  4. Send the fingerprint (it is public): it goes into keystore/ in the repository.
DONE
