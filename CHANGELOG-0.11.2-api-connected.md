# Changelog — v0.11.2-api-connected (versionCode 16)

## What changed
- **The app ships pre-connected.** The production control-plane Worker URL is built into the app (`NexusApiClient.DEFAULT_BASE_URL = https://gameboster.nova-7bc9791a4174.workers.dev`). The license gate now asks for **only the access key**; no server address is required.
- **Key-only sign-in UI.** The gate shows the server host as a caption and keeps a collapsed "Change server address" option for self-hosted Workers (https-validated, persisted as before). A stored v0.11.1 base keeps working, so existing sessions migrate silently.
- **Persistent session unchanged.** One activation; every start re-syncs silently; only real revocation (401/403) signs the user out; an unreachable server shows a retry screen and keeps the session.

## Build/verification (real)
- `testDebugUnitTest` 109/109, 0 failures; `lintRelease` 0 errors / 48 warnings
- `assembleRelease` / `assembleDebug` signed with the SAME key as v0.11.1 (release cert SHA-256 `c3a5c74a…` verified with `apksigner` — installs as a direct update, no uninstall)
- `aapt2` badging: versionCode 16 / `0.11.2-api-connected`, minSdk 26 / target 35, locale `fa`
- release 15,809,730 B (SHA-256 `daa1d5c4…`), debug 32,463,802 B (SHA-256 `faa8296d…`)
- Endpoint contract probed LIVE against the deployed Worker: unknown key → HTTP 403 `{"valid":false,"code":"NOT_FOUND"}`; unauthenticated sync → HTTP 401 with the Worker's own messages.

## Still not verifiable from the sandbox (honest)
- A real-key activation round-trip, device-capacity denial, revocation and the DNS/WireGuard sync payload need a real device with a valid key.
- On-device behavior remains unverified (no emulator/device in the sandbox).

## Strings
- +2 keys × EN/FA (`license_server_caption`, `license_advanced_toggle`) → **443/443 exact parity** (sorted-key diff + XML validation).
