# Stable Android release signing

Release APKs are signed only in GitHub Actions. The private keystore must never be committed to this repository.

## Required GitHub Actions secrets

Open repository **Settings → Secrets and variables → Actions → New repository secret** and create:

- `ANDROID_KEYSTORE_BASE64` — the complete release `.jks` encoded as one-line Base64.
- `ANDROID_KEYSTORE_PASSWORD` — keystore password.
- `ANDROID_KEY_ALIAS` — key alias.
- `ANDROID_KEY_PASSWORD` — private-key password.

The workflow [`.github/workflows/release.yml`](.github/workflows/release.yml) decodes the keystore into the temporary GitHub runner directory, builds the release APK, verifies the certificate with `apksigner`, and uploads only the APK plus the public certificate report.

## Creating the Base64 value

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("pebble-rear-display-release.jks"))
```

Linux:

```bash
base64 -w0 pebble-rear-display-release.jks
```

macOS:

```bash
base64 < pebble-rear-display-release.jks | tr -d '\n'
```

## Building

After all four secrets are configured:

1. Open **Actions → Signed release APK**.
2. Choose **Run workflow**.
3. Download the `pebble-rear-display-signed-release` artifact.

Every future release must use the same keystore. Losing or replacing it prevents Android from installing an update over an APK signed with the old certificate.
