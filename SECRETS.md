# GitHub Actions Secrets Guide for Meow

To automatically build and sign release APKs using GitHub Actions on [Meow-Music/Meow](https://github.com/Meow-Music/Meow), configure the following repository secrets under **Settings → Secrets and variables → Actions**:

## Required Secrets for Signed Release Builds

| Secret Name | Description | Example / How to generate |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded string of your `.jks` release keystore file | PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("your_keystore.jks"))`<br>Linux/Mac: `base64 -w 0 your_keystore.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | Password for the release keystore | `your_keystore_password` |
| `RELEASE_KEY_ALIAS` | Alias name for the key inside the keystore | `your_key_alias` |
| `RELEASE_KEY_PASSWORD` | Password for the key alias | `your_key_password` |

> [!NOTE]
> If `RELEASE_KEYSTORE_BASE64` is not provided, the GitHub Actions workflow will automatically build using the fallback signing config so builds always succeed.

## How to Trigger a Release

1. **Automatic Release on Tag:** Push a tag starting with `v` (e.g. `git tag v1.0.2 && git push origin v1.0.2`).
2. **Manual Release:** Go to **Actions → Build & Release Meow → Run workflow** and check "Create a GitHub Release".
