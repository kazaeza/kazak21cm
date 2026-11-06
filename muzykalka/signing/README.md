# Sideload signing key

`sideload.jks` signs every APK (debug and release, local and CI) with ONE stable key, so a new
build always installs over the previous one and the user's library/progress on the phone survives.

- Store/key password: `muzykalka`, alias `muzykalka` (deliberately public: the app is sideloaded
  to one personal phone and is not distributed through a store).
- **Never regenerate or replace this file.** A different key = Android refuses the update = the
  app must be uninstalled = local data is lost (unless exported first via Settings → Экспорт).
- If the app ever goes to Google Play, create a separate upload key kept in CI secrets.
