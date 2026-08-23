# Release pipeline

Athena releases are built by `.github/workflows/release.yml` and published as GitHub Releases.

## Pipeline design

The workflow runs when a `vMAJOR.MINOR.PATCH` tag is pushed. It has four stages:

1. Validate the tag and derive the Android `versionName` and `versionCode`.
2. Run unit tests and Android lint.
3. Restore the signing key from GitHub Secrets, build and verify a signed release APK, and generate a SHA-256 checksum.
4. Transfer the verified assets to a separate job and create the GitHub Release.

The signing job only has read access to repository contents. The publishing job has write access but never receives the signing key or its passwords.

The release contains:

- `Athena-MAJOR.MINOR.PATCH.apk`
- `Athena-MAJOR.MINOR.PATCH.apk.sha256`

Re-running the workflow for an existing tag replaces those two assets instead of creating a duplicate release.

## One-time signing setup

Keep the signing key backed up in a secure location. Losing it prevents future APKs from being installed as updates to existing installations.

Create a key if the project does not already have one:

```powershell
keytool -genkeypair -v -keystore athena-release.jks -alias athena -keyalg RSA -keysize 4096 -validity 10000
```

Add these repository-level GitHub Actions secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded contents of the `.jks` file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias, such as `athena` |
| `ANDROID_KEY_PASSWORD` | Private-key password |

With GitHub CLI installed and authenticated, the keystore can be uploaded without writing its encoded value to a text file:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\athena-release.jks))) |
    gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD
```

The last three commands prompt for their values. Files ending in `.jks` or `.keystore` are ignored by Git.

## Publish a release

Create the next annotated tag from the commit to release, then push it:

```powershell
git switch main
git pull --ff-only
git tag -a v1.0.1 -m "Athena v1.0.1"
git push origin v1.0.1
```

GitHub Actions will publish the release after tests, lint, signing, and APK verification succeed. To retry an existing tag, open **Actions > Release Android app > Run workflow** and enter that tag.

The Android `versionCode` is calculated as `major * 1,000,000 + minor * 1,000 + patch + 1`. Major versions are limited to 2099, and minor and patch versions to 999.
