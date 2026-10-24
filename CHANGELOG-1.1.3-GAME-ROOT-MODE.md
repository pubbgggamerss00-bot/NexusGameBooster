# Nexus Game Booster 1.1.3 — Game optimization + optional root mode

> اعمال‌شده از بسته `NexusGameBooster-1.1.2-GAME-ROOT-OPTIONS-SOURCE.zip`. این بسته بر پایه v1.1.0 + تغییرات Functional Reliability بریده شده بود و نسخه 1.1.2 را ادعا می‌کرد که با نسخه منتشرشده v1.1.2 (versionCode 23) تداخل دارد؛ بنابراین این انتشار با versionCode **24** / versionName **1.1.3** منتشر شد. هدر نئونی مشترک `NgbPageHeader` (مربوط به v1.1.1) و همه فیکس‌های DNS/WireGuard فازهای قبل دست‌نخورده حفظ شدند.

## Game optimization (existing real actions retained)
- Keeps real installed-game discovery and launch-intent checks; does not invent a game list.
- Existing boost engine continues to report each action as applied, skipped, unsupported, or failed.
- Keeps ART compile, cache trimming, game protection, and device-change restore paths behind their actual Shizuku/system capability checks.
- Does not force-stop arbitrary apps automatically; this remains disabled for safety.

## Optional root GameManager mode
- Adds a Settings card to check root availability on explicit tap.
- Allows an explicit package name and runs only the fixed Android GameManager `performance` or `standard` command.
- Validates package syntax before interpolating it into the command; no arbitrary shell input is exposed.
- Commands have a timeout; output/errors are surfaced instead of claiming success.
- Root is optional and never requested at app startup. The ROM/device must support GameManager mode; no FPS gain is promised.

## Verification status
- English and Persian string XML parsed successfully.
- Gradle unit tests and APK build could not run in this environment: Gradle 8.9 wrapper download failed because `services.gradle.org` was unreachable.
- Device-level root authorization and GameManager behavior are not verified; test on a rooted test device before publishing.

## وضعیت بیلد و امضا (پس از اعمال واقعی — نتیجه واقعی پایپ‌لاین انتشار)
- بیلد مرحله‌ای واقعی انجام شد: `compileDebugKotlin` تمیز در اولین تلاش؛ `testDebugUnitTest` **۱۱۲/۱۱۲** موفق (۱۱۰ تست قبلی + ۲ تست جدید روت)، ۰ خطا؛ `lintRelease` ۰ خطا / ۴۸ هشدار (بیس‌لاین بدون تغییر)؛ `assembleRelease` ≈ ۳ دقیقه و ۳۸ ثانیه و `assembleDebug` موفق.
- Badging: versionCode 24 / versionName 1.1.3 / minSdk 26 / target 35. امضا با `apksigner` تأیید شد: cert SHA-256 `862fabe69b1eb8207538406fd3e0be7a90c68e9801e19c9b78aa00390ebbad1e` (همان کلید از v0.11.3) → آپدیت مستقیم از v0.11.3 به بعد؛ نسخه‌های ≤ v0.11.2 همچنان به حذف یک‌باره نیاز دارند.
- APKهای نهایی: release 15,871,050 بایت (SHA-256 `552ffdeac45def76b15519183920c32f545a0a54e689bcf5a8e9366fd21015ba`) و debug 32,622,914 بایت (SHA-256 `177b83a69f2de68503aab5d67d766e8fc36b2a3944a731a0add54372bf4c9c0b`).
- دسترسی روت و رفتار GameManager روی دستگاه واقعی در این محیط قابل آزمایش نیست (امولاتور/دستگاه روت‌شده نداریم) — مثل همه فازهای قبل فقط از نظر کامپایل/تست/لینت تأیید شده است. کارت روت هیچ درخواست خودکاری در شروع برنامه ندارد و فقط با دکمه فعال می‌شود.
- رشته‌های کاربر-پدیدارِ کارت (۷ کلید) در هر دو زبان EN/FA بومی‌سازی شدند (۵۰۰/۵۰۰ پاریتی)؛ پیام‌های فنی خروجی کنترلر (مثل «Root access confirmed») مانند پیام‌های خطای سرور به‌صورت خام نمایش داده می‌شوند.
