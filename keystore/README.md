# keystore/

- `certificate-sha256.txt`: the public fingerprint of the release signing certificate.
  Users can compare it with the installed app; the Release workflow refuses an APK
  signed with any other key.
- `debug-certificate-sha256.txt`: the public fingerprint of the debug signing
  certificate (test builds, "Formica-D"). The Build workflow refuses a debug APK signed
  with any other key.

The keys themselves are never stored in the repository, not even encrypted: they live
only in the repository secrets and in an offline backup (`docs/MAINTAINING.md`). They
were created with `scripts/generate-signing-key.sh` on the maintainer's computer. The
Build workflow fails if a key file appears (`scripts/check-secrets.sh`). Never replace
the release key: with a new key, users cannot update and must uninstall the app.
