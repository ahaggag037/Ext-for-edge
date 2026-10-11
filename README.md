# EdgeFlow Installer for Android

EdgeFlow Installer is a small Android companion that prepares and assists installation of the private **EdgeFlow Mobile Performance** extension on Microsoft Edge Android.

## What this repository contains

- An Android APK project (`com.edgeflow.installer`) built with the Android Gradle Plugin.
- The signed EdgeFlow **3.1.0** CRX embedded as Base64 asset chunks and reconstructed locally inside the APK.
- The EdgeFlow extension source for inspection.
- A GitHub Actions workflow that builds an installable debug APK and publishes it as a workflow artifact.

## Installation model

### Personal Android phone

A normal Android APK cannot silently write Microsoft Edge enterprise policies. The app therefore uses a bounded assisted route:

1. Verify the bundled CRX by SHA-256.
2. Export it to `Downloads/EdgeFlow/`.
3. Ask the user to enable **EdgeFlow Install Assistant** once in Android Accessibility settings.
4. Launch the best installed Edge channel (Canary > Beta > Dev > Stable).
5. The Accessibility service navigates Edge's developer CRX-install flow and the Android file picker when the matching UI is exposed.
6. Edge's own final extension confirmation remains authoritative.

The assistant only acts after an explicit install session, only inside the selected Edge package and known file-picker packages, and expires after 8 minutes.

### Managed Android device

Microsoft Edge supports `ExtensionInstallForcelist` on Android 149+ for silent extension deployment. Android still requires Device Owner/Profile Owner or MDM/UEM authority to write those managed app restrictions. This project detects managed-owner status but deliberately does not apply a fake policy until a valid self-hosted extension update endpoint is configured.

## EdgeFlow identity

- Extension ID: `oeehabgdmlomabhpcbbdfjlehpbnfljp`
- Version: `3.1.0`
- CRX SHA-256: `d1a20e525e51f5bfaf632956618a588f312d6c420ddce7279ed4b057ae370444`

The private key used to keep this extension ID stable is **not stored in this public repository**.

## Build

GitHub Actions builds on every push to `main`. Open the latest **Build EdgeFlow Installer APK** workflow run and download the `EdgeFlow-Installer-APK` artifact.

## Current verification boundary

The repository verifies the Android build and the embedded CRX checksum in CI. The accessibility navigation still needs a real-device acceptance run because Edge's hidden Android developer UI is not a stable/public automation API and text/layout can change between Edge channels.
