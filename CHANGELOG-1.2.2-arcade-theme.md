# 1.2.2 "Arcade Theme on 1.2.0" — theme-only port per the user's course correction

Base: released v1.2.0 (`fdbde64`, versionCode 27). This release ships as
**versionCode 29 / versionName "1.2.2"** (vc 28 is v1.2.1, already published).

## Why 1.2.2 exists (the user's direction)
- After v1.2.1's full redesign the user asked: «به نسخه 1.2.0 برگرد؛ برای تم از این
  (فایل مرجع HTML) استفاده کن؛ فقط تم و رابط کاربری و دکمه‌ها، کارایی همان نسخه 1.2.0
  باشد» — go back to the v1.2.0 tree and apply ONLY the theme, UI styling and buttons
  from the reference HTML. No screen restructuring, zero functional changes.

## What was applied (theme/UI/buttons only, onto byte-identical v1.2.0 screens)
- Ported FROM v1.2.1 (identical files, kept out of the screens' way):
  - `ui/theme/NgbFonts.kt` + the 4 font files (Vazirmatn variable body, Rajdhani HUD,
    Orbitron tags — SIL OFL 1.1, listed in THIRD_PARTY_NOTICES.md) and the one-line
    `Theme.kt` typography wiring.
  - `ui/arcade/ArcadeKit.kt` — the reference's chamfered gradient-edge panels, 3D lip
    buttons, HUD primitives (glow blobs, floor grid, scan sweep, orbit, badges).
  - `ui/components/NgbNeonBackdrop.kt` — backdrop is now the drifting glow blobs +
    retro perspective floor grid (NgbNeonLoader for the session check kept as-is).
  - `ui/components/NgbPageHeader.kt` — arcade head wrapper (new `code` param is
    optional; every v1.2.0 caller compiles unchanged).
  - `ui/components/BoostCard.kt` — the REACTOR boost button from the reference
    (orbit ring + tick dial + hex core + honest step rows). Public API preserved;
    `tagline` made optional (defaults blank) so the untouched v1.2.0 HomeScreen
    compiles and renders without it.
  - `navigation/NgbApp.kt` — arcade bottom navigation (sliding chamfered indicator +
    icon pop, RTL-mirrored) + route-change scan sweep. Gate logic untouched.
- Re-skinned IN PLACE (identical public signatures, screens untouched):
  - `NgbButton` → the reference's 3D lip key (chamfer 12, gradient face, press sinks
    into the lip, shine sweep motion-gated, neon glow) via ArcadeButton.
  - `NgbOutlinedButton` → same chamfer silhouette, restrained Material outline.
  - `SectionCard` → ArcadePanel PLAIN construction (full-bleed neon→azure edge under
    a 1.25 dp inset dark fill), same header row + heading semantics.
  - `StatusChip` / `NgbMiniChip` → chamfered tag silhouettes, same color logic.
- NOT touched (byte-identical to v1.2.0): every file under `ui/screens/`,
  `ui/components/NgbConnectButton.kt` (already the reference's tunnel CTA),
  `EmptyState`, `MetricRow`, `LicenseCountdownCard`, `NgbScreenContainer`,
  `RocketSplash`, all ViewModels/data/network code, strings (512/512 parity kept —
  no new keys needed).

## Signing
- Same committed permanent keystore (`bc570bd1…`) as v1.2.1 — v1.2.2 installs as a
  normal update over v1.2.1; over ≤ 1.2.0 still needs the documented one-time
  uninstall (lost-key saga, see CHANGELOG-1.2.1 / README → Release signing).

## Numbers
- Tests: 115/115 pass. lintRelease: 0 errors. Strings: 512/512 EN/FA (v1.2.0 set).
- Measured: debug 33,411,356 bytes; release 16,513,910 bytes (SHA-256 `0d75b0c837c9c05d…`);
  signing cert SHA-256 `bc570bd1…` (the committed permanent key).
