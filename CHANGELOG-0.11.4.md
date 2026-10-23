# Nexus Game Booster 0.11.4

## License countdown
- Added a live neon license countdown card to the Home screen.
- Shows days, hours, minutes, and seconds remaining.
- Uses the server-authoritative license expiry timestamp (Unix epoch seconds); no expiry is inferred from the device activation time.
- Green indicates an active license; red indicates expiry or three days or less remaining.
- The ring animates and reflects the remaining fraction of the license term.
- The API Worker now returns the license creation and expiry timestamps during activation and sync, allowing existing sessions to backfill expiry metadata after the Worker is redeployed.

## Deployment note
Deploy the updated `nexus-gamebooster-api.js` Worker before installing this app build. The API URL is not a cryptographic secret; it remains fixed in the app UI/code, while expiry enforcement remains server-side.

## Build & signing status (real, measured)

- versionCode 18 / versionName `0.11.4`; `testDebugUnitTest` 109/109, 0 failures; `lintRelease` 0 errors / 48 warnings; `aapt2 badging` = com.nexus.gamebooster vc 18 / 0.11.4 / minSdk 26 / target 35.
- Signing cert SHA-256 `862fabe69b1eb8207538406fd3e0be7a90c68e9801e19c9b78aa00390ebbad1e` (apksigner-verified — the SAME keystore as v0.11.3, durably backed up in the private `NexusGameBooster-signing` repo): v0.11.3 → v0.11.4 installs as a DIRECT update; ≤ v0.11.2 installs still need their one-time uninstall first.
- Release APK 15,830,518 bytes (SHA-256 `a8de29e9d913f458881555462f62089fece6074107191ed7716b19bb0e69def9`); debug APK 32,517,346 bytes (SHA-256 `f85dcf3f78136b57b6525d2632a11fc82a1e15096bfd48edf2fd7f40318d3e0d`).
- Applied on top of 0.11.3 with engineering care: the zip's stale "Change server address" KDoc was rejected (0.11.3 removed that UI), its 3 dead server-field string keys were not re-added (0 code references), and its stale v0.11.2-era whatsnew files were replaced with real v0.11.4 notes. The theme artboards + button kit were faithfully replicated in Compose (exact token values, RTL-mirrored chamfers, Reduce-motion-gated animations); the artboards reference Vazirmatn/Orbitron by name but shipped no font files, so no fonts were bundled.
