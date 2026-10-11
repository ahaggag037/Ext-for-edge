# EdgeFlow Installer build status

## Local verification

A locally built installer APK was produced and inspected before publishing this status.

- APK: `EdgeFlow-Installer-v1.0.0.apk`
- APK SHA-256: `8779d26e796055b07360b16020fb162410bec9cd95f3806409744ba15d04ca7c`
- Embedded EdgeFlow extension version: `3.1.0`
- Extension ID: `oeehabgdmlomabhpcbbdfjlehpbnfljp`
- Embedded CRX SHA-256: `d1a20e525e51f5bfaf632956618a588f312d6c420ddce7279ed4b057ae370444`
- APK structure check: passed (`unzip -t` / Android APK format)
- CRX header/checksum verification: passed

## GitHub CI

The repository workflow `.github/workflows/android-build.yml` builds an installable debug APK on every push to `main` and uploads it as the `EdgeFlow-Installer-APK` workflow artifact.

This commit intentionally triggers a fresh GitHub Actions build so the repository has an independently produced APK artifact.

## Real-device verification still required

The Android app and embedded extension are built, but the Accessibility-assisted Edge navigation must still be accepted on the target Huawei/Edge device because Edge's hidden Android CRX-install UI can change between channels/builds.

The assistant is deliberately bounded to an explicit install session and leaves Edge's final extension security confirmation to the user.
