# CHANGELOG — v0.11.5-neon (versionCode 19)

## Phase 11.5 — "NEON ARENA v2" theme + fancy entry/loading animations

Source of truth: the user-supplied `NexusGameBooster-Neon-Theme.html` mockup (its
`:root` tokens, star field, and component styles were extracted verbatim).

### Theme retune (single source of color truth — `ui/theme/Color.kt`)
- Background `#05050C` (purple-tinted near-black), card base `#0F0C1E`, container ramp
  harmonized to the violet family (`#171129 → #2A1F55`), outline `#2A1F4D`.
- Neon accents now EXACT mockup values: primary green `#00FF9D`, tertiary cyan `#00F0FF`,
  secondary purple `#BF00FF`; new tokens `NgbPink #FF2D95` and `NgbPurpleSoft #E0A0FF`.
- Text: primary `#FFFFFF`, muted `#A0A0C0`. AMOLED variant retuned to the same family on pure black.
- Launcher background color `#05050C`.
- All kit components that hardcoded the previous palette (`NgbButtons`, `NgbConnectButton`,
  `NgbKit`, `NgbSecondaryButton`, `StatusChip`, `LicenseCountdownCard`) now read the shared
  tokens — no stray old hexes remain (`27F58A`, `8B5CF6`, `3AC2F8`, `9D6BFF` all gone).

### New `ui/components/NgbNeonBackdrop.kt`
- `NgbNeonBackdrop`: the mockup's page background — three faint radial glows
  (purple 20%/20% · green 80%/80% · cyan center) + a deterministic 60-star twinkling
  field (2–5 s sine twinkle, scale 1→1.5).
- `NgbNeonLoader`: app-entry loader — gradient logo chip, two staggered expanding rings,
  rotating green→cyan→purple sweep arc, gradient wordmark, status line and the shine
  progress bar (`.progress-fill`).
- `NgbNeonSpinner`, `NgbShineBar`, `NgbStaggerIn` (mockup fadeIn/slideIn entrance,
  staggered ~70 ms), `NgbLogoChip`.
- Motion honesty: every animation is gated by `LocalNgbMotionEnabled` (in-app Reduce
  motion OR system animation scale 0) — reduced-motion devices get the same layout
  fully static. The loader only shows while the real session check runs.

### Gate & shell wiring
- Gate state 0 (app entry, session re-sync) now shows `NgbNeonLoader` instead of the
  plain `CircularProgressIndicator`.
- Main shell: transparent `Scaffold` over `NgbNeonBackdrop`; bottom bar is the mockup's
  translucent near-black (alpha 0.92).
- `LicenseGateScreen` rebuilt on the v2 kit: staggered entrance, gradient logo chip +
  wordmark, the key field inside the mockup's violet card (green hairline border),
  kit CTA that swaps to `NgbNeonSpinner` + "checking" while the key is validated,
  blinking status dot while busy, success message in neon green (was a dark green
  with poor contrast on dark backgrounds). Behavior/strings/API contract unchanged.

### Strings
- +2 keys × EN/FA (`app_loading_subtitle`, `loader_content_description`) → **454/454 parity**.

### Build status (verified in this repo's pipeline)
- `:app:testDebugUnitTest` → **109/109 passed, 0 failures**.
- `:app:lintRelease` → **0 errors** (48 pre-existing warnings, baseline unchanged after
  wiring the new subtitle string).
- `:app:assembleRelease` (R8) + `:app:assembleDebug` → OK; badging `versionCode 19`,
  `0.11.5-neon`, minSdk 26, target/compile 35.
- Release signing cert SHA-256 `862fabe69b1eb8207538406fd3e0be7a90c68e9801e19c9b78aa00390ebbad1e`
  — identical to v0.11.3/v0.11.4 → direct update, no uninstall.
- On-device visual verification is NOT possible in the build sandbox (no emulator) —
  stated honestly; all animations are standard Compose APIs.
