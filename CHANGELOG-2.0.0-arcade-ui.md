# CHANGELOG — 2.0.0 «Arcade UI on 1.2.0»

**Date:** 2026-10-29 · **versionCode:** 30 · **versionName:** 2.0.0
**Base:** v1.2.0 (`fdbde64`) functional codebase, untouched.

## User directive

> «این پروژه (سورس ۱.۲.۰) رو حفظ کن؛ فقط و فقط برای تم، رابط کاربری و انیمیشن‌هاش از فایل دوم (`Nexus Game Booster.html`) استفاده کن. توجه کن که کاربرد سورس از بین نره — فقط تم و رابط کاربری و انیمیشن‌ها اعمال بشه.»

## What this release is

The **complete arcade-HUD redesign** from the reference prototype applied on top of the
**untouched v1.2.0 codebase** — the same full-screen port that shipped as v1.2.1, now
audited file-by-file against the reference HTML and re-verified against the v1.2.0 logic
contracts, released as the 2.0.0 milestone.

## How it was built (guarantees)

1. **Functional base = v1.2.0 exactly.** The user-supplied `NexusGameBooster-1.2.0.zip`
   was diffed against tag `fdbde64` — byte-identical (`diff -rq` clean). All ViewModels,
   services, repositories, data and gate logic in this tree are the v1.2.0 files.
2. **Screen ports restored from the audited Phase 19 redesign** (`9f3ba98`), which was
   built from this same reference HTML: all 7 screens + boost-result sheet + arcade shell.
3. **Logic-contract audit (v1.2.0 → 2.0.0):** for every screen, the set of ViewModel
   actions referenced is **identical** to the v1.2.0 screen (verified by diffing the
   extracted `vm.*` / `vm::` call sets — zero differences on HomeScreen, LibraryScreen,
   MonitorScreen, ProfilesScreen, PingGoodScreen, LicenseGateScreen; SettingsScreen keeps
   every action via method references). Composable entry signatures unchanged
   (`BoostResultDialog` byte-comparable, `SettingsScreen()` param-less, etc.).
4. **Reference-fidelity audit:** every screen checked against the reference HTML catalog —
   hero + countdown + shizuku card + REACTOR (orbit/ticks/halo/hex/steps/cancel) on Home;
   segmented tabs + skewed covers on Library; ring gauges + sparklines + LIVE on Monitor;
   pips stats + selected halo on Profiles; power ring + DNS bars + stepped test reveal on
   Ping; segmented language/appearance + reduce-motion switch on Settings; floating rocket
   + focus-glow field on License; score ring + count-up on the boost sheet; sliding
   chamfered nav indicator + scan sweep + stagger entrances + spark bursts app-wide.

## Verification

- `:app:compileDebugKotlin` — clean.
- Unit tests: **115/115 pass** (0 failures, 0 errors).
- Lint: **0 errors**.
- Strings: **518 / 518** EN = FA.
- Signing: permanent committed keystore, cert SHA-256 `bc570bd1…` — same as v1.2.1/v1.2.2,
  so 2.0.0 installs as a normal update (no uninstall).

## Files

- Restored/ported: `navigation/NgbApp.kt`, `ui/components/*` (BoostCard reactor, NgbButtons,
  NgbKit, SectionCard, StatusChip), `ui/screens/**` (all 7 screens + BoostResultDialog),
  `ui/theme/Theme.kt`, `values*/strings.xml` (+7 arcade Ping/DNS strings, both locales).
- Kept from the v1.2.2 arcade layer: `ui/arcade/ArcadeKit.kt`, `ui/theme/NgbFonts.kt`,
  `res/font/*`, backdrop & motion kits, `RocketSplash.kt`, `LicenseCountdownCard.kt`.
- Bumped: `app/build.gradle.kts` → vc 30 / 2.0.0; `whatsnew/*`; README; this changelog.

## Not changed

Anything functional: no logic, service, repository, manifest-permission, or flow changes.
The only `strings.xml` additions are display strings for the arcade Ping/DNS components.
