<div dir="rtl">

# معماری کلاینت اندروید و طراحی رابط کاربری HotaruNet

🌐 **زبان:** [🇬🇧 English](ARCHITECTURE.md) | **🇮🇷 فارسی (Persian)**

---

## ۱. جریان کلی عملکرد در اپلیکیشن اندروید

<div dir="ltr">

```mermaid
flowchart LR
    UI["MinimalVpnHome (Jetpack Compose)\nOne-Tap Connect + Direct Apps + Switch Route"] --> Engine["HotaruSmartEngine\nWarm Cache (<2s) + Stratified Selection"]
    Engine -->|Stage 1: TCP + Network Profile| Probe["HotaruTunnelProbe\n~32 KiB Throughput + 204"]
    Probe -->|Best Scored Node| Core["Xray-core + HevSocks5Tunnel\n(tun0 VPN Service + Per-App Bypass)"]
    Core -->|Every 35s| Watchdog["CoreServiceManager Watchdog\nSpeed < 64 KB/s x3 -> Failover"]
    Watchdog -->|Seamless Switch| Engine
    Crypto["HotaruCrypto + libhotaruguard.so\nBuild-Time Encrypted Vault"] -.->|In-Memory Only| Engine
    Core -.->|Anonymous Heartbeat| Telemetry["Supabase Edge Function\nOnline Counter (No PII)"]
```

</div>

## ۲. ماژول‌های کلیدی کلاینت (`V2rayNG/app/src/main/`)

- **`ui/main/MinimalVpnHome.kt` و `HotaruDancingMascot.kt`**: رابط کاربری تک‌صفحه‌ای Void-Galaxy (Jetpack Compose) با انیمیشن ربات ۴ مرحله‌ای، شمارندهٔ دقیق ترافیک عبوری از پروکسی، انتخابگر **برنامه‌های مستقیم (Split Tunneling)**، دکمهٔ **تغییر مسیر آنی (Switch Route)**، تغییر آنی زبان (`FA / EN`)، بروزرسانی درون‌برنامه‌ای (`HotaruUpdateDialog`) و بخش درباره ما (`HotaruAboutDialog`).
- **`handler/HotaruSmartEngine.kt`**: تشخیص نوع شبکه کاربر (دیتا یا وای‌فای)، اولویت‌بندی پروتکل‌های ضد‌فیلتر (`REALITY`, `XHTTP`, `WS+TLS`, `Hysteria2`, `WARP`)، رتبه‌بندی بر اساس سرعت واقعی دانلود بدون محدودیت جغرافیایی و نگهداری کش گرم برای اتصال مجدد زیر ۲ ثانیه.
- **`core/HotaruHealthPolicy.kt` و `core/HotaruTunnelProbe.kt`**: اندازه‌گیری کم‌مصرف سرعت دانلود واقعی (`~32 KiB` با `Range` و تست اتصال تک‌CDN متناوب) و نگهبان زنده هر ۳۵ ثانیه؛ در صورت ۳ خطای متوالی یا افت سرعت زیر `64 KB/s` در ۳ نمونه پیاپی، انتقال خودکار و بدون قطعی کاربر به سرور سالم بعدی انجام می‌شود.
- **`service/QSTileService.kt`**: کاشی هوشمند Quick Settings برای اتصال مستقیم و خودکار بدون نیاز به باز کردن اپلیکیشن.
- **`handler/HotaruCommunityCounter.kt`**: نمایشگر ناشناس تعداد کاربران آنلاین با امضای HMAC روی Supabase Edge Functions + PostgreSQL بدون ذخیرهٔ هیچ دادهٔ هویتی یا ترافیکی.
- **`handler/UpdateCheckerManager.kt` و `handler/HotaruApkUpdater.kt`**: بررسی بی‌صدای آخرین ریلیز در `OneForAllHub/HotaruNet` هنگام باز شدن برنامه، دانلود مستقیم APK متناسب با معماری دستگاه درون برنامه و فراخوانی نصب‌کننده سیستم.
- **`util/HotaruCrypto.kt` و `cpp/hotaru_guard.cpp`**: محافظت از داده‌های حساس خارج از بایت‌کد DEX در لایهٔ نیتیو JNI و سخت‌سازی شبکه در `network_security_config.xml` (بستن گواهی‌های User CA و ترافیک غیررمزنشدهٔ غیراز لوپ‌بک).

---

> توجه: اسکریپت‌های سرور جمع‌آوری، لیست کانال‌ها و منابع، توکن‌ها و کلیدهای رمزنگاری واقعی در این مخزن نمایشی عمومی قرار داده نشده‌اند.

</div>
