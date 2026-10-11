# Security notes

- No EdgeFlow extension private signing key is committed to this repository.
- The APK does not request broad package visibility.
- Accessibility automation is session-gated and time-limited to eight minutes.
- Accessibility events are ignored unless they originate from the selected Microsoft Edge package or a small allowlist of file-picker packages.
- The APK never attempts to bypass Edge's own extension-install confirmation.
- Managed-device policy deployment is not attempted unless the app actually has Device/Profile Owner authority and a real update endpoint is configured in a future release.
