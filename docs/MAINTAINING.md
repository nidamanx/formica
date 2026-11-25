# Maintaining Formica

Building, testing, signing and releasing all happen in GitHub Actions: no local tools
are needed.

## Branches

- `develop`: work in progress. Every push runs the Build and CodeQL workflows and, when
  the app or the build changes, the emulator smoke test.
- `main`: the default branch. It only receives released versions: `develop` is merged
  into it (`git merge --no-ff`) just before each release, then `develop` is
  fast-forwarded to `main`. Releases are built from `main` only.

## Signing keys

The keys are never stored in the repository, not even encrypted. Each one exists in two
places only:

- the repository secrets (*Settings → Secrets and variables → Actions → Secrets*);
- an offline backup in the maintainer's password manager.

| Key | Secrets | Public fingerprint |
|---|---|---|
| Release | `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` | `keystore/certificate-sha256.txt`, both READMEs |
| Debug (test builds, "Formica-D") | `DEBUG_KEYSTORE_BASE64`, `DEBUG_KEYSTORE_PASSWORD` | `keystore/debug-certificate-sha256.txt` |

The Release workflow builds the APK unsigned and signs it afterwards with
`scripts/sign-apk.sh` (apksigner). The Build workflow runs the same script on every run
with a throwaway key made in the job, so the signing steps are tested before each
release.

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
4. With Build and emulator test green on `develop`, merge `develop` into `main`
   (`git merge --no-ff develop -m "Release X.Y.Z"`).
5. *Actions → Release → Run workflow* on `main`. The `build` job checks the version and
   builds the unsigned APK (lint, tests, permissions) from a clean state; the `sign` job
   decrypts the key, signs with `scripts/sign-apk.sh`, removes
   the key, checks the certificate against `keystore/certificate-sha256.txt`, writes
   `SHA256SUMS` and attests the build provenance; the `publish` job creates the tag
   `v<versionName>` with the release.
6. Locally: `git fetch origin --tags`, then check the release page and the files, and
   bring `develop` up to `main` (`git merge --ff-only main`, push).

A published tag is never moved or deleted (repository ruleset): a wrong release is
fixed with a new version.

## Icon and store graphics

`art/formica_art.py` draws the ant once; `python3 art/generate_icon.py` (needs the Debian
packages `python3-shapely` and `python3-cairosvg`) writes every icon file from it: the
adaptive icon with its monochrome layer for themed icons in `app/src/main/res`, the debug
variant ("Formica-D": draft greys and a "D" badge) in `app/src/debug/res`, `art/icon.svg`,
the store icon `fastlane/metadata/android/en-US/images/icon.png` and the notification icon
(a simpler ant, readable at 18-24 px). The generated files are committed; change the
drawing, never the generated files.

`python3 art/generate_feature_graphic.py` writes the store banner
(`fastlane/metadata/android/<locale>/images/featureGraphic.png`, one per language, with the
slogans written in the script) and the social preview `art/social-preview.png` (1280x640),
uploaded by hand in *Settings → General → Social preview*. `python3 art/generate_badges.py` writes the README install badges in
`art/badges/`. Both need `fonts-roboto` besides `python3-cairosvg`.

## Changing the database

Users' data must survive every update, so a schema change always comes with:

1. `version` increased by 1 in `FormicaDatabase`;
2. an explicit `Migration(old, new)` added to the builder (there is no destructive
   fallback: a missing migration stops the app instead of deleting the data);
3. the new schema file `app/schemas/<package>.FormicaDatabase/<version>.json`, written by
   the build. The Build workflow fails while it is missing or different, and keeps the
   generated files as the artifact `room-schemas`: commit them unchanged.

The previous schema files are never edited: comparing them shows what a migration must
do.

## Testing builds

- Every push to `main` or `develop` runs the Build workflow: lint, unit tests, debug and
  release builds, permission check (`config/allowed-permissions.txt`), debug APK as an
  artifact. On a Gradle failure the errors are also written as an annotation.
- Emulator smoke test: installs and starts the debug app on Android 8, 11 and 16, passes
  the welcome screen, loads the sample data, opens every section in the light and the dark
  theme and with large text, and keeps screenshots. It runs on every push to `develop`
  that changes the app or the build; by hand (*Actions → Emulator smoke test → Run
  workflow*, once the workflow is on `main`) also on one level only (input `only`) or on
  Android 17 (experimental).
- Store screenshots, only when asked (they double the emulator run time): on Android 16
  the same workflow takes them in every language (`scripts/store-screenshots.sh`) if the
  last commit of the push has `[store-screenshots]` in its message (an empty commit is
  enough: `git commit --allow-empty -m "chore: update the store screenshots
  [store-screenshots]"`), or with the input `store_screenshots` of a manual run. They are
  in the artifact `screenshots-api36` under `store/`: copy them to
  `fastlane/metadata/android/<locale>/images/phoneScreenshots/` when the screens change.
- Reproducibility, only when asked: four independent builds of the same commit (four
  runners, two by two with different folder, time zone and file mode mask, no cache) must
  give the same unsigned APK byte for byte, as F-Droid requires. R8 runs on one thread
  (`android.r8.threadPoolSize=1` in `gradle.properties`): with several, it sometimes
  narrowed a parameter type differently from one build to the next. It runs if the last commit of a push to `develop`
  has `[reproducibility]` in its message, or by hand once the workflow is on `main`; when
  the APKs differ, an annotation lists the entries that differ.
- CodeQL: code scanning of the Kotlin code (built as a debug app) and of the workflows,
  on every push to `main` or `develop`, on pull requests to `main` and every Monday.
  Alerts are in *Security → Code scanning*; those of `develop` are shown with the branch
  filter.
