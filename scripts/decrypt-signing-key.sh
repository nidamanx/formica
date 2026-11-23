#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Decrypts a signing key from the CI secrets made by scripts/generate-signing-key.sh and
# checks it step by step, so a wrong secret is reported precisely (as an annotation that
# never shows a secret value): too short, not base64, cut, wrong password, keystore that
# does not open, or a certificate other than the one published in keystore/.
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/decrypt-signing-key.sh [-h] release|debug <output.p12>

Reads FORMICA_KEY_BASE64 and FORMICA_KEY_PASSWORD from the environment (the workflow
maps the secrets KEYSTORE_* or DEBUG_KEYSTORE_* to them) and writes the plain PKCS12
keystore to output.p12. Its certificate must match keystore/certificate-sha256.txt
(release) or keystore/debug-certificate-sha256.txt (debug). Run from the repository root.
USAGE
}
if [[ ${1:-} == -h ]]; then
  usage
  exit 0
fi
if [[ $# -ne 2 || ! $1 =~ ^(release|debug)$ ]]; then
  usage >&2
  exit 2
fi

kind=$1
out=$2
if [[ $kind == release ]]; then
  secret=KEYSTORE
  expected_file=keystore/certificate-sha256.txt
else
  secret=DEBUG_KEYSTORE
  expected_file=keystore/debug-certificate-sha256.txt
fi
fail() {
  echo "::error title=$kind signing key::$*"
  rm -f "$out" "$out.enc"
  exit 1
}

: "${FORMICA_KEY_BASE64:?}" "${FORMICA_KEY_PASSWORD:?}"
umask 077

# Spaces and line breaks picked up when pasting are not part of base64
b64=$(printf '%s' "$FORMICA_KEY_BASE64" | tr -d '[:space:]')
len=${#b64}
# The length of a short value is not printed: it could be the password's
(( len >= 1000 )) || fail "${secret}_BASE64 is far too short for a keystore (under 1000 characters): is it the password, or only part of the text?"
if [[ $b64 =~ [^A-Za-z0-9+/=] ]]; then
  fail "${secret}_BASE64 ($len characters) contains characters that are not base64: paste only the content of formica-$kind-keystore.b64"
fi
(( len % 4 == 0 )) || fail "${secret}_BASE64 has $len characters, not a multiple of 4: the text is cut"
printf '%s' "$b64" | base64 -d > "$out.enc" 2>/dev/null \
  || fail "${secret}_BASE64 ($len characters) is not valid base64"
unset b64

if ! openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -in "$out.enc" -out "$out" \
    -pass env:FORMICA_KEY_PASSWORD 2>/dev/null; then
  if [[ $FORMICA_KEY_PASSWORD =~ ^[[:space:]] || $FORMICA_KEY_PASSWORD =~ [[:space:]]$ ]]; then
    echo "::warning title=$kind signing key::${secret}_PASSWORD starts or ends with a space or a line break"
  fi
  fail "cannot decrypt ${secret}_BASE64 with ${secret}_PASSWORD: the password differs from the one given to generate-signing-key.sh for this key"
fi
rm -f "$out.enc"

openssl pkcs12 -in "$out" -noout -passin env:FORMICA_KEY_PASSWORD 2>/dev/null \
  || fail "the decrypted keystore does not open with ${secret}_PASSWORD"

# The fingerprint is public: it is embedded in every APK signed with this key
actual=$(openssl pkcs12 -in "$out" -nokeys -passin env:FORMICA_KEY_PASSWORD 2>/dev/null \
  | openssl x509 -noout -fingerprint -sha256 | sed 's/^.*=//')
expected=$(tr -d '[:space:]' < "$expected_file")
[[ $actual == "$expected" ]] || fail "the key's certificate ($actual) is not the one in $expected_file ($expected)"
echo "The $kind signing key is valid: certificate SHA-256 $actual"
