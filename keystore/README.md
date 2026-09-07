# Signing key

`smart-reminder.jks` is the self-signed key every released APK is signed with.
It exists so every build (yours, mine, GitHub Actions) installs as an *update* on your phone
instead of Android refusing it because the signature changed.

Password, alias and key password are all `smartreminder`.

This is a personal, side-loaded app, so keeping the key in the repo is a convenience trade-off:
anyone with the file could sign an APK that your phone would accept as an update of this app,
but they would still have to get you to install it. If you'd rather keep it private:

1. Delete the file from the repo.
2. Base64-encode it and store it in the GitHub secret `KEYSTORE_BASE64`
   (plus `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` if you change them).
3. The workflow in `.github/workflows/build.yml` picks it up automatically.
