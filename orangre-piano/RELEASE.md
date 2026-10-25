# orangre piano 1.0.0 — release readiness

## Verified implementation and assets
- Dedicated application ID: `com.orangrepiano.launcher`.
- Adaptive piano icon: foreground, background and monochrome vectors inside `src/main/res`; declared with `android:icon` / `android:roundIcon`.
- A-Z rail: each rendered letter is positioned by its exact visible glyph bounds rather than the font line-box and baseline.
- Android 16 target: `compileSdk = 36`, `targetSdk = 36`. Supported SDK starts at Android 9 (API 28).
- All original user flows preserved (hardware-keyboard search, swipe drawer, favorites, camera, torch, clock, calendar, language and wallpaper).

## Protected production signing is a release gate
**The first stable installation must be signed with a permanent, private key.**
The public GitHub repository must never contain the `.jks` file, private passwords, or base64 of the key. Its loss prevents same-key APK upgrades. Prior public debug versions were signed with *ephemeral* certificates; even a signed v1.0 cannot upgrade those in place.

Generate and back up a release keystore in a trusted local environment:

```bash
keytool -genkeypair -v -keystore orangre-piano-release.jks \
  -alias orangre-piano -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 orangre-piano-release.jks > orangre-piano-keystore.b64
```

Securely store the `.jks` and passwords in a password manager/offline backup, never here in chat or in repository files.

Add the following **GitHub repository Actions secrets** in GitHub Settings → Secrets and variables → Actions:

| Secret | Value |
| --- | --- |
| `PIANO_KEYSTORE_B64` | Base64-encoded bytes of the private `.jks` file |
| `PIANO_STORE_PASSWORD` | Keystore password |
| `PIANO_KEY_ALIAS` | Alias created in the keystore |
| `PIANO_KEY_PASSWORD` | Key password |

Once all four secrets exist, GitHub Actions builds signed `assembleRelease` APK and `bundleRelease` AAB, verifies signature, and publishes stable tag `orangre-piano-v1.0.0`. With missing secrets it only publishes clearly marked **RC1 debug APK**, to avoid falsely claiming store readiness. Do not overwrite a permanent signed release with a different key.

## Human acceptance checklist (Titan 2)
- [ ] Launch HOME, install as default, return to previous launcher.
- [ ] Confirm the 6/5 rows and custom pixel clock fit without scroll at original display scaling.
- [ ] Clock opens clock; date opens calendar; camera launches, torch toggles with first permission prompt.
- [ ] Select/reorder/clear favorites; launch from home and A-Z.
- [ ] Search by physical keyboard (Latin and Cyrillic), backspace, search field.
- [ ] Letter rail: each glyph is centered in its capsule; active state, swipe/drag, haptic and preview work.
- [ ] Scroll the app list and swipe down to close when at top; swipe up on home to open.
- [ ] Toggle Russian/English, glass, animations, status-bar option, wallpapers.
- [ ] Check themed icon and app label in Android app drawer, recent apps and settings.
- [ ] Check accessibility touch targets and TalkBack on interactive controls.
- [ ] Check performance and battery while using a personal wallpaper.
- [ ] Verify signed APK can update an older **signed** build with the *same* keystore.

## Google Play still needs human-account steps
An AAB, store listing artwork, privacy policy hosting, device screenshots, Play Console account, app content/data safety declarations, signing/Play App Signing selection, and closed/open testing must be completed in the Play developer account. Repository build success is **not** proof of Play review approval.
