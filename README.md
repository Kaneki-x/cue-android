<img align="left" width="80" height="80" src="metadata/en-US/images/icon.png" alt="App icon">

# Cue

<br>

**Cue** is a free, secure and open source two-factor authentication app. It
keeps your tokens in an encrypted vault and gets the code to where you need it:
a keyboard that inserts the code into any app, a home screen widget that shows
your pinned entries, and the usual list with search, groups and icon packs. Cue
supports HOTP and TOTP, so it works with thousands of services.

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
  - Import from other authenticator apps (Google Authenticator, Microsoft Authenticator, 2FAS, Authy and more)
- Organization
  - Groups, pinning (pinned entries come first, marked by a bar on the card's edge), search,
    icon packs
  - Ships with four CC0 icon packs (aegis-icons, delta-aegis-icons,
    aegis-simple-icons and its outlined variant), installed on first start; a pack removed in
    Settings > Icon packs stays removed. New entries take the matching icon, and the app offers
    once to assign icons to existing entries
- Material 3 design with dynamic colors

## Building

```
./gradlew assembleDebug
```
