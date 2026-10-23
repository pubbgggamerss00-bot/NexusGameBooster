# Nexus Game Booster 0.11.3

- Removed the server address, hostname caption, and server-change controls from the license screen.
- License activation always uses the built-in production API endpoint; any previously saved custom API base is ignored.
- Error messages are displayed in red and successful activation in green.
- Version code bumped to 17 and version name to 0.11.3.

Security note: the API URL is hidden from the app UI, but a URL embedded in an APK cannot be truly secret from someone inspecting the APK. Keep all admin secrets on the server and never bundle them in the app.

## Build & signing status (real, measured)

- versionCode 17 / versionName `0.11.3`; `testDebugUnitTest` 109/109, 0 failures; `lintRelease` 0 errors / 48 warnings; `aapt2 badging` = com.nexus.gamebooster vc 17 / 0.11.3 / minSdk 26 / target 35.
- **Signing key rotated:** the v0.11.1/v0.11.2 keystore (cert `c3a5c74a…`) was lost in a build-sandbox wipe on 2026-10-23 and is unrecoverable (a private key cannot be derived from published APKs). A new 4096-bit RSA keystore was minted; the shipped release APK's cert SHA-256 is `862fabe69b1eb8207538406fd3e0be7a90c68e9801e19c9b78aa00390ebbad1e` (apksigner-verified). Consequence: every install of v0.9.4 → v0.11.2 must be uninstalled once before installing v0.11.3, and the access key is entered once again (uninstalling clears the stored session). Clean installs are unaffected. The new keystore is durably backed up in the private `NexusGameBooster-signing` repo, so releases from v0.11.4 onward update directly again.
- Release APK 15,808,326 bytes (SHA-256 `e4061e29318728b113be78ab3ab0828d593d434c565d184d51aa0ffbc7d40249`); debug APK 32,462,414 bytes (SHA-256 `98bfbe0d90bafd368d5a4e2c21bb377fe74c157460332e02487edf5a80117e9c`).
