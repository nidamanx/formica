# Security Policy

## Supported versions

Only the latest release receives fixes.

| Version | Supported |
| ------- | --------- |
| latest  | yes       |
| older   | no        |

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Use GitHub's private reporting instead: open the **Security** tab of this
repository and click **Report a vulnerability**. You will get a private
channel with the maintainer.

Useful details to include:

- version and where you got it from;
- operating system and environment;
- steps to reproduce;
- the impact you expect.

## What is in scope

- Data leaving the phone in any way other than an export started by the user
  (the app has no network access and disables Android backups).
- Permissions in the APK beyond those listed in the README.
- Import files: a crafted file that crashes the app, changes data outside the
  chosen import mode, or bypasses the preview and the automatic backup.
- Release integrity: a release whose checksum, signature or build provenance
  attestation does not match what the README says.

## What is out of scope

- Export files after they leave the app: they are plain JSON, readable by
  anyone who receives them, as the README states.
- Access by someone who has the unlocked phone, or root access to it.
- Behavior decided by Android, third-party software and unofficial builds.

## Disclosure

This is a personal project maintained on a best-effort basis. Reports are
acknowledged as soon as reasonably possible and fixes are released before any
public disclosure.
