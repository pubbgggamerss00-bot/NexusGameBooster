# Nexus Game Booster 0.10.1 — Network Tools Fix

- Fixed DNS VPN disconnect handling with an explicit stop action that closes the TUN descriptor before stopping the service.
- Added confirmed deletion for imported WireGuard profiles. Deletion is disabled while WireGuard is connected.
- Refined the Ping Good screen with neon edge lighting, a subtle pulsing header icon, and animated tab transitions.
- Added English and Persian strings for WireGuard profile deletion.

## Validation status

The Gradle wrapper could not download Gradle 8.9 in this environment because network access to `services.gradle.org` was unavailable. The project has **not** been compiled or tested on a physical Android device as part of this patch.
