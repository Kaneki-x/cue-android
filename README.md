<img align="left" width="80" height="80" src="metadata/en-US/images/icon.png" alt="App icon">

# Cue

<br>

**Cue** is a free, secure and open source two-factor authentication app. It
keeps your tokens in an encrypted vault and gets the code to where you need it:
a keyboard that inserts the code into any app, a home screen widget that shows
your pinned entries, and the usual list with search, groups and icon packs. Cue
supports HOTP and TOTP, so it works with thousands of services.

Cue for Android is a modified version of [Aegis Authenticator](https://github.com/beemdevelopment/Aegis)
by Beem Development, and like Aegis it is free software under the
[GNU General Public License v3](LICENSE). The source of every release is in this repository,
tagged with its version.

For a list of frequently asked questions, please check out [the FAQ](FAQ.md).

The security design of the app and the vault format is described in detail in
[this document](docs/vault.md).

## Features

- Free and open source
- Secure
  - Encryption (AES-256-GCM)
    - Password (scrypt)
    - Biometrics (Android Keystore)
  - Screen capture prevention
  - Tap to reveal
- Keyboard: insert the code of any entry into the focused field, then switch back
- Home screen widget: codes of your pinned entries, hidden until tapped
- Multiple backup options
  - Automatic
  - Cloud
  - Export (encrypted)
- Compatible with Google Authenticator
- Supports industry standard algorithms: [HOTP](https://tools.ietf.org/html/rfc4226) and [TOTP](https://tools.ietf.org/html/rfc6238)
- Lots of ways to add new entries
  - Scan a QR code or an image of one
  - Enter details manually
  - Import from other authenticator apps (Aegis, Google Authenticator, Microsoft Authenticator, 2FAS, Authy and more)
- Organization
  - Groups, pinning (pinned entries come first, marked by a bar on the card's edge), search,
    icon packs
  - Ships with one icon pack, Cue Icons: 7458 icons merged from seven open-source sets
    (CC0 1.0, CC BY 4.0 and Apache 2.0; see [design/iconpacks/](../design/iconpacks/)),
    installed on first start; a pack removed in Settings > Icon packs stays removed. New
    entries take the matching icon, and the app offers once to assign icons to existing entries
- Material 3 design with dynamic colors

## Languages

English, Simplified Chinese (the primary language) and Traditional Chinese, as on iOS.
`app/build.gradle` keeps only these in the APK (`localeFilters`); the other `values-*`
folders from Aegis stay in the source but are not shipped or kept up to date.

## Differences from Cue for iOS

Deliberate, so both apps' behaviour is on record:

- **Clipboard**: Android does not clear a copied code by itself; Cue marks the clip as sensitive,
  so Android hides it in its clipboard preview. iOS copies the code to this device only and
  expires it after 60 s.
- **Sort order**: issuer and account sorting compare case-insensitively by code point on
  Android; iOS sorts locale-aware (so Chinese names follow the system's collation).
- **Widget**: pinned entries are listed by title on Android; iOS lists them in vault order.

## Building

```
./gradlew assembleDebug
```

The third-party licences (Settings > About) come from `app/src/main/res/raw/aboutlibraries.json`.
The icon sets and the Manrope font are not Gradle dependencies: they are defined by hand in
`app/config/libraries` and added to that file by hand, with the licence texts it needs.

## Releasing

Google Play takes an app bundle:

```
./gradlew :app:bundleRelease
```

Release builds are signed only when all four of these are set, as Gradle properties
(`-P...` or `~/.gradle/gradle.properties`) or environment variables; without them the build
still works and produces unsigned output. Keep the keystore and passwords out of the repository.

| Name | What |
| --- | --- |
| `CUE_RELEASE_STORE_FILE` | Path to the upload keystore (absolute, or relative to `app/`) |
| `CUE_RELEASE_STORE_PASSWORD` | Keystore password |
| `CUE_RELEASE_KEY_ALIAS` | Key alias |
| `CUE_RELEASE_KEY_PASSWORD` | Key password |

Use Play App Signing: Google keeps the app signing key and the key above is only the upload
key, which can be replaced if it is lost. `versionCode` in `app/build.gradle` must grow with
every upload and never go back; it is 82, carried over from Aegis, for 0.1.0.

## License

Copyright (C) Beem Development and the Aegis contributors; modifications copyright (C) the
Cue authors.

This program is free software: you can redistribute it and/or modify it under the terms of
the GNU General Public License as published by the Free Software Foundation, either version 3
of the License, or (at your option) any later version. See [LICENSE](LICENSE).

The bundled icon pack and fonts keep their own licences (CC0 1.0, CC BY 4.0, Apache 2.0,
SIL OFL 1.1); the app lists them under About > Third-party licenses.

