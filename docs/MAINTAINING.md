# Maintaining Formica

Building, testing, signing and releasing all happen in GitHub Actions: no local tools
are needed.

## Branches

- `develop`: work in progress. Every push runs the Build workflow and, when the app or
  the build changes, the emulator smoke test on Android 8 (API 26).
- `main`: the default branch. It only receives verified, working states, one commit
  each, made identical to `develop`. Releases are built from `main` only.

## Signing keys

The keys are never stored in the repository, not even encrypted. Each one exists in two
places only:

- the repository secrets (*Settings → Secrets and variables → Actions → Secrets*);
- an offline backup in the maintainer's password manager.

| Key | Secrets | Public fingerprint |
|---|---|---|
| Release | `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` | `keystore/certificate-sha256.txt`, both READMEs |
| Debug (test builds, "Formica-D") | `DEBUG_KEYSTORE_BASE64`, `DEBUG_KEYSTORE_PASSWORD` | `keystore/debug-certificate-sha256.txt` |

`*_BASE64` holds the content of the `formica-<kind>-keystore.b64` file (one long line
of base64); `*_PASSWORD` the password given to the script. The fingerprints are public
and are not secrets.

Secrets cannot be read back from GitHub: if the backup is lost too, no update can ever
be signed again and users would have to uninstall the app. Never replace the release
key.

### Creating a key

On the maintainer's own computer (never a shared machine or a CI runner), with OpenSSL 3:

1. Create the password in the password manager first (at least 24 characters).
2. Run `scripts/generate-signing-key.sh release` (or `debug`) and enter the password.
3. Copy the content of `formica-<kind>-keystore.b64` into a secure note of the password
   manager (not a password field: it may cut long text). Paste it back from there into
   a file and compare it with `cmp` before going on.
4. Create the two secrets, then delete the file with `shred -u`.
5. Put the fingerprint in `keystore/` (and, for the release key, in both READMEs).

The workflows decrypt the keys with `scripts/decrypt-signing-key.sh`, which reports a
wrong secret precisely (too short, not base64, cut, wrong password, other certificate)
without showing its value.

## Releasing a version

1. In `app/build.gradle.kts` increase `versionCode` by 1 and set `versionName`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (English only, at
   most 500 characters: stores show it for every language).
3. In `CHANGELOG.md` move the `[Unreleased]` entries to `## [X.Y.Z] - YYYY-MM-DD`. Set
   the same version in the badges of `README.md` and `README.it.md`, and `version` and
   `date-released` in `CITATION.cff`. `scripts/check-version.sh --release` checks it all.
4. Bring the change to `main` with a green Build.
5. *Actions → Release → Run workflow* on `main`. The workflow checks the version, the
   key and the permissions, builds and signs the APK, checks its certificate against
   `keystore/certificate-sha256.txt`, writes `SHA256SUMS`, attests the build provenance
   and creates the tag `v<versionName>` with the release.
6. Locally: `git fetch origin --tags`, then check the release page and the files.

A published tag is never moved or deleted (repository ruleset): a wrong release is
fixed with a new version.

## Testing builds

- Every push to `main` or `develop` runs the Build workflow: lint, unit tests, debug and
  release builds, permission check (`config/allowed-permissions.txt`), debug APK as an
  artifact. On a Gradle failure the errors are also written as an annotation.
- *Actions → Emulator smoke test → Run workflow*: installs and starts the debug app on
  Android 8, 11, 16 and 17 (experimental), opens every section in the light and the
  dark theme and keeps screenshots. Try one API level first (input `only`).
