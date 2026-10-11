# EdgeFlow Browser — Genesis Architecture

## Mission
Build an Android browser that owns the full browsing experience instead of depending on Microsoft Edge's private-extension installation paths. GeckoView is the browser engine; EdgeFlow Core is a first-party built-in WebExtension bundled in the APK.

## Why GeckoView
- Full browser engine rather than Android WebView.
- Built-in WebExtensions can be installed from APK assets with `ensureBuiltIn` and do not require a public add-on store.
- Built-in extensions can later communicate with the Android native layer through GeckoView native messaging.
- The app controls sessions, tabs, lifecycle and Android-level memory decisions.

## Genesis scope (0.1)
- GeckoView 157.0.20261005135250.
- Navigable Android browser shell with address/search bar, back, forward and reload.
- `http` / `https` intent handling so EdgeFlow Browser can become a default browser.
- EdgeFlow Core bundled in `assets/edgeflow/` and installed on first run.
- ChatGPT performance engine with historical-turn containment, event-loop pressure detection and adaptive Stream Shield.
- CI builds an installable debug APK on every commit to `browser/geckoview-genesis`.

## Next native layers
1. **Tab Engine** — multiple persistent GeckoSession instances, suspend/resume and crash isolation.
2. **Native Memory Guardian** — Android `ActivityManager.MemoryInfo`, trim-memory callbacks and safe background-session discard.
3. **AI Performance Mode** — per-site performance policy with an aggressive ChatGPT profile.
4. **Network Engine** — Gecko content blocking plus EdgeFlow rules and user-controlled allowlists.
5. **Native ↔ WebExtension bridge** — telemetry kept on-device; extension reports page pressure and native code reports memory/device state.
6. **Session store** — restore tabs/history after process death.
7. **Downloads, permissions, file picker, media and fullscreen** — browser-complete delegates.
8. **Private mode, bookmarks, history, search engines and desktop mode**.
9. **Extension manager** — first-party built-ins first; optional third-party WebExtension support only after compatibility/security controls are defined.

## Performance principles
- Never delete ChatGPT server state or rewrite conversation JSON.
- Prefer engine/session controls over continuous DOM polling.
- Use incremental page instrumentation and stop work when not useful.
- Fail open: if an optimization cannot confidently identify its target, leave the page untouched.
- Keep current generation and user input responsive before optimizing historical content.
- Measure on the Huawei target device before promoting an optimization from experimental to default.

## Verification boundary
A successful CI build proves source/API compatibility and APK packaging. It does not prove real-device performance, Huawei lifecycle behavior, or ChatGPT DOM compatibility. Those require device acceptance tests.
