# Nexus Game Booster — API integration changes

- Added Cloudflare Worker API client for license activation and server sync.
- Added startup license gate; app checks saved session online before opening the main interface.
- Bound API sync sessions to the registered installation ID using `X-Device-Id`.
- Excluded the API session preferences from Android backup and device transfer.
- Added five-minute polling and a manual sync control on the Ping Good screen.
- Added remote DNS entries to the DNS selector.
- Added remote WireGuard config import/update and cleanup of configs removed from the server.
- Backend includes device listing/revocation and checks device activation on each sync.

Build status: **verified after fixes**. The uploaded draft had never been compiled; the first real build found a syntax error (missing parenthesis in the DNS connect-button condition in `PingGoodScreen.kt`) and an offline-logout defect (the startup gate cleared the saved session on ANY failure, including a plain network outage — now only real auth failures (401/403) log the session out, and an unreachable server shows a retry screen while keeping the session). Hardcoded Persian UI strings in the draft were normalized to English + Persian resources (441/441 parity). Shipped as versionCode 15 / versionName `0.11.1-api-integration` because versionCode 14 is already released as v0.11.0-updates.
