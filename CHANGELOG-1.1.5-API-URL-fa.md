# Nexus Game Booster 1.1.5 — تعویض آدرس API (فیکس واقعی)

## تغییرات
- آدرس پیش‌فرض API برنامه به Worker جدید تغییر کرد:
  `https://gameboster.nova-7bc9791a4174.workers.dev`
- آدرس قدیمی در همه فایل‌های متنی پروژه که آن را داشتند نیز به‌روزرسانی شد (README — هر ۳ مورد، `CHANGELOG-0.11.2-api-connected.md` و پنل ادمین `admin-v1.0.html`).

## چرا این آپدیت فیکس واقعی است (صادقانه)
- Worker قدیمی (`nexus-gamebooster-api.amineshgi-1386.workers.dev`) هنگام انتشار این نسخه **خارج از سرویس** بود: `GET /api/health` آن پاسخ `HTTP 500 / error code: 1101` (استثنای داخلی Worker) می‌داد — یعنی کاربرانِ روی نسخه‌های قبلی، هم‌گام‌سازی و فعال‌سازی را از دست داده بودند.
- Worker جدید به‌صورت زنده راستی‌آزمایی شد: `/api/health` → `200 {"ok":true}`؛ کلید ناشناس روی `/api/v1/license/activate` → `403 {"valid":false,"code":"NOT_FOUND"}`؛ هم‌گام‌سازی بدون احراز هویت → `401` — دقیقاً همان قراردادی که کلاینت پیاده کرده است.

## اصلاح شماره‌گذاری (صادقانه)
- بستهٔ آپلودی نسخهٔ 1.1.4 را ادعا می‌کرد که با v1.1.4 منتشرشده (پیشنهادهای بازی PUBG/COD، نسخهٔ قبلی همین ریپو) تداخل داشت؛ این نسخه با **versionCode 26 / versionName 1.1.5** منتشر شد.
- بیس بسته دوباره درخت قدیمی v1.1.0 بود: دیف‌های ۴ صفحهٔ آن فاقد `NgbPageHeader`، `BoostResultDialog` آن فاقد بخش پیشنهادهای بازی، و strings آن فاقد کلیدهای `game_advice_*` و `root_game_mode_idle` بود — همه رد شدند و فایل‌های فعلی ریپو دست‌نخورده ماندند.
- بسته خودش آپدیت آدرس را **ناقص** انجام داده بود (README خودش هنوز ۲ بار آدرس قدیمی را داشت)؛ این انتشار هر ۳ مورد README + ۲ فایل دیگر را کامل به‌روزرسانی کرد.

## وضعیت بیلد و امضا (واقعی، اندازه‌گیری‌شده)
- به‌روزرسانی سندباکس: کلون تازه از GitHub main + بازیابی keystore از ریپوی خصوصی `NexusGameBooster-signing` (cert SHA-256 `862fabe6…` با keytool تأیید شد) + نصب مجدد Android SDK (build-tools 35.0.0 / platform 35).
- نکته بیلد (صادقانه): سندباکس تازه بود و `repo.maven.apache.org` به دانلودها پاسخ `429` می‌داد؛ دانلود dependencyها با یک init-script موقتِ mirror (Aliyun، خارج از ریپو) انجام شد. `minifyReleaseWithR8` یک بار با کمبود حافظه (سندباکس 4 گیگابایتی) شکست خورد و با daemon تازه و heap صریح 3G پاس شد. هیچ تغییری در فایل‌های ریپو برای این دو کار لازم نشد.
- `compileDebugKotlin` پاک؛ `testDebugUnitTest` **115/115، 0 شکست**؛ `lintRelease` **0 خطا / 48 هشدار** (بیس‌لاین بدون تغییر).
- `aapt2 badging`: `com.nexus.gamebooster` versionCode 26 / `1.1.5`، minSdk 26 / compileSdk 35.
- امضای release با apksigner تأیید شد: cert SHA-256 `862fabe69b1eb8207538406fd3e0be7a90c68e9801e19c9b78aa00390ebbad1e` — همان keystore از v0.11.3؛ آپدیت مستقیم روی هر نسخهٔ 0.11.3 به بعد.
- APKهای v1.1.5: release 15,877,742 بایت (SHA-256 `5cf6d672c901bcc7c0cd6f5eabb1729c0e3e120714b4f9cf37b4d5661f8fc11b`)؛ debug 32,629,610 بایت (SHA-256 `5d87acf4d1a5a4d983f6f5e41e0724c9a28e894e0ced2ab0ba15120c84f0ac31`).

## پارگی رشته‌ها
- strings دست‌نخورده → **510/510** پاریتی دقیق (تجزیه XML، دوباره تأیید شد).
