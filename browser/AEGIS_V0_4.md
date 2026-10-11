# EdgeFlow Browser v0.4 — Aegis

Aegis is the first deliberately **large-batch** EdgeFlow Browser release. It is developed on `browser/v0.4-aegis`; `browser/v0.3-atlas` stays untouched as the rollback baseline.

## Release goals

1. Keep ChatGPT responsive on long, streaming conversations without rewriting ChatGPT transport or conversation payloads.
2. Make the whole browser adaptive to RAM, Android low-memory signals, thermal state, power saver, network validation and metered connectivity.
3. Add enough browser fundamentals that EdgeFlow can be used as a real daily browser rather than a ChatGPT shell.
4. Reduce future maintenance and data cost: no micro-APK releases for tiny tweaks, ARM64-only packaging, CI size guards, and browser-wide policy logic centralized in native code.

## Major changes from Atlas

- New `AegisActivity` browser shell while Atlas remains a rollback target.
- Persistent history and bookmarks (private tabs excluded).
- Per-site compatibility policy persisted by host.
- Per-site data clearing, permission reset, and browser-cache clearing through Gecko `StorageController`.
- Search engine choice: Google, DuckDuckGo, Brave Search.
- Data modes: Auto, Saver, Full quality.
- AI modes: Auto, Max Smoothness, Live Response.
- Browser-wide adaptive optimizer for images, iframes, muted autoplay and costly visual effects.
- The global optimizer explicitly exits on ChatGPT/OpenAI so the specialized ChatGPT engine is the only DOM observer on that hot path.
- Global DOM observation is burst-based and stops on healthy settled pages instead of imposing a permanent observer cost.
- Memory Guardian uses both relative RAM percentage and absolute available-memory headroom, Android low-RAM classification, thermal status and power-save state.
- Background Gecko sessions are deactivated and can be hibernated while preserving form data safety.
- Native web-notification bridge to Android notification channels.
- Gecko runtime tuning: Fission/high-value isolation, parallel marking, fetch priority, cookie partitioning hardening, private fingerprinting protection.
- Existing Atlas privacy stack retained: Safe Browsing, tracking/fingerprinting/cryptomining protections, bounce tracking protection, cookie purging, query-parameter stripping and Global Privacy Control.
- Network Doctor and explicit load-error page retained and expanded.
- File upload, native download path, fullscreen, media permissions, session restoration and encrypted session state retained.

## ChatGPT integrity invariant

Aegis performance code must not intercept or rewrite ChatGPT conversation transport/history structures. CI rejects known-dangerous hooks including `current_node`, `json.mapping`, `window.fetch =`, XHR prototype interception and WebSocket prototype interception.

## Data-budget invariant

The mobile APK is ARM64-only. CI rejects APKs larger than 230 MB and rejects accidental x86/x86_64/armeabi-v7a native libraries. This exists specifically to avoid reverting to the ~500 MB universal-package failure mode.

## Verification gates before a user release

- JavaScript syntax checks for both optimization engines.
- Aegis source/manifest/version checks.
- Android compile/build on JDK 17 + API 36.
- APK ZIP integrity.
- Embedded EdgeFlow assets present.
- ARM64-only ABI check.
- APK size budget check.
- SHA-256 emitted by CI.
- Real-device verification on the Huawei device is still required before calling runtime behavior verified.

## Known deliberate deferrals

- Password storage/autofill: deferred until a dedicated encrypted credential design exists.
- Automatic translation-model downloads: deferred because models can be large and the target device has a limited data budget.
- Forced DNS-over-HTTPS provider: not enabled silently; choosing a third-party resolver should be an explicit user setting.
- Arbitrary unsigned extension installation: not enabled. Gecko's normal extension installation requires Mozilla-signed XPIs; EdgeFlow Core remains a privileged built-in extension shipped in the APK.
- Stable Android release signing: the CI currently builds development/debug artifacts. A long-lived release key must be configured securely (not committed to the public repository) before normal in-place updates can be guaranteed.
