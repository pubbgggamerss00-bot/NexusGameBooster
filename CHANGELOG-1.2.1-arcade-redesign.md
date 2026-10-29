# 1.2.1 "Arcade Edition" — full arcade-HUD redesign + permanent signing key

Base: released v1.2.0 (`fdbde64`, versionCode 27). This release ships as
**versionCode 28 / versionName "1.2.1"**.

## Why 1.2.1 exists (the user's report)
- "The 1.2.0 release APK doesn't install on my phone." Forensics: the published
  v1.2.0 release APK is healthy (badging OK, zip OK, v2 sig verified) and carries the
  SAME cert as every release since v0.11.5 (`862fabe6…`). The phone's installed copy
  is signed with a key that no longer exists — every previous keystore was lost with
  sandbox wipes (documented repeatedly since Phase 11).
- Fix: a NEW permanent signing key (`bc570bd1…`) generated AND committed to the repo
  (`release.keystore` + `keystore.properties`, deliberate `.gitignore` exception, with
  the trade-off documented in README → Release signing). From 1.2.1 on, updates install
  as normal updates forever. This one release requires a single uninstall first.

## The redesign (reference: user's "Nexus Game Booster.html", UI-only, zero functional changes)
- NEW `ui/arcade/ArcadeKit.kt`: chamfered gradient-edge panels, 3D lip buttons with
  press-into-lip + shine sweep, outlined gradient buttons, chips, skewed pips,
  chamfered switch/segmented/fields, HUD ring gauges, sparklines, step badges,
  particle bursts, route scan sweep, drifting glow blobs, retro perspective floor grid.
- NEW `ui/theme/NgbFonts.kt`: Vazirmatn (variable body), Rajdhani (HUD), Orbitron
  (tags) — all SIL OFL 1.1, listed in THIRD_PARTY_NOTICES.md.
- Shell: arcade bottom navigation (sliding chamfered indicator + icon pop, RTL-mirrored)
  + route-change scan sweep; backdrop = blobs + floor grid (replaces the star field).
- Screens restyled: Home (hero, Shizuku card, REACTOR with orbit/ticks/hex core/real
  step badges, bottom-sheet boost report + burst), Monitor (ring gauges on real
  battery/memory values; honesty cards), Profiles (hex-icon cards, animated pips from
  real policy values), Ping (power-ring WireGuard, DNS signal bars — untested bars are
  GRAY, fixing the reference's red-bars bug), Library (gradient letter covers with real
  icons when available), Settings (segmented controls, arcade switch, update/restore
  cards), License gate (floating rocket, chamfered HUD key field, success burst).
- Reference bugs fixed in the port: step-sequencing glitch, fake "24 ms"/"Frankfurt-02"
  not reproduced, untested-DNS red bars, stuck double-tap reset (kept the confirm
  dialog), missing dir=rtl handled by the app's real locale engine.
- All animations respect LocalNgbMotionEnabled (Reduce motion + system animator scale).

## Numbers
- Strings: 518/518 EN/FA exact parity (+6 keys: ping_power_hint, ping_kv_profile,
  ping_kv_protocol, dns_best_chip, dns_row_select, dns_row_active).
- Tests: 115/115 pass. lintRelease: 0 errors.
- Artifacts: debug 33,495,952 B (SHA-256 01e12775…); release 16,535,818 B
  (SHA-256 62246eef…); signing cert SHA-256 bc570bd1… (NEW permanent key).
- Fonts add ~1 MB to the APK (the rest of the delta is code + resources).
