<div dir="rtl" align="center">

# ⚡ HotaruNet (هوتارو نت)

**کلاینت هوشمند، خودکار و ضد‌فیلتر اندروید**

🌐 **زبان:** [🇬🇧 English](README.md) | **🇮🇷 فارسی (Persian)**

[![Telegram](https://img.shields.io/badge/Telegram-@OneForAllHub-26A5E4?style=for-the-badge&logo=telegram)](https://t.me/OneForAllHub)
[![Releases](https://img.shields.io/badge/Releases-%D8%AF%D8%A7%D9%86%D9%84%D9%88%D8%AF%20%D9%86%D8%B3%D8%AE%D9%87%20%D8%AC%D8%AF%DB%8C%D8%AF-00F5D4?style=for-the-badge&logo=github)](https://github.com/OneForAllHub/HotaruNet/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024--35)-3DDC84?style=for-the-badge&logo=android)](https://github.com/OneForAllHub/HotaruNet)

</div>

---

<div dir="rtl">

> ⭐ **حمایت از پروژه:** اگر HotaruNet برایتان مفید بوده و به دسترسی آزاد شما کمک کرده است، لطفاً با **ستاره دادن (Star) به این مخزن در گیت‌هاب** و عضویت در کانال تلگرام **[@OneForAllHub](https://t.me/OneForAllHub)** از تداوم و توسعهٔ رایگان این پروژه حمایت کنید!

---

## ✨ ویژگی‌های کلیدی HotaruNet

HotaruNet با رابط کاربری **Cyber-Anime (Compose)** و موتور هوشمند **Auto-Pilot** (`HotaruSmartEngine`) برای عبور پایدار از محدودیت‌ها و اختلالات شبکه طراحی شده است:

- **اتصال هوشمند تک‌لمسی:** بدون نیاز به وارد کردن یا تست دستی کانفیگ‌ها — فقط با یک لمس، برنامه متناسب با نوع اینترنت شما (همراه اول، ایرانسل، وای‌فای و فیبر) سریع‌ترین سرور سالم را پیدا و متصل می‌کند.
- **سنجش سرعت واقعی به‌جای پینگ خام:** به‌جای تکیه صرف بر پینگ یا پاسخ خالی `HTTP 204`، سرعت واقعی دانلود (پیلود ۳۲ کیلوبایتی) بررسی می‌شود و انتخاب نهایی بر اساس ترکیب نرخ انتقال (`Bps`) و پایداری انجام می‌گیرد.
- **پشتیبانی از پروتکل‌های ضدسانسور مدرن:**
  - `VLESS + REALITY` (پورت 443 + Vision)
  - `Cloudflare WARP Dedicated` (ثبت کلید اختصاصی روی هر دستگاه و چرخش IPهای تمیز Anycast)
  - `Hysteria 2` (مبتنی بر QUIC / UDP)
  - `XHTTP / SplitHTTP` و `WebSocket + TLS`
- **نگهبان زنده اتصال (Smart Watchdog) و سوییچ خودکار:** در طول اتصال، کیفیت و سرعت دانلود را پایش می‌کند و در صورت افت سرعت مداوم یا قطعی سرور، به‌صورت خودکار به سرور پرسرعت بعدی منتقل می‌شود.
- **پشتیبانی کامل از فارسی و انگلیسی:** امکان تغییر آنی زبان بین فارسی و انگلیسی در صفحه اصلی، نوتیفیکیشن و پنجره‌های برنامه.
- **بررسی خودکار آپدیت‌ها:** در هر بار باز شدن برنامه به‌صورت بی‌صدا ریلیزهای جدید گیت‌هاب را چک می‌کند و فقط در صورت انتشار نسخهٔ جدید پیام دانلود را نمایش می‌دهد.

---

## 📚 مستندات پروژه (فارسی / English)

| سند | فارسی | English |
| --- | --- | --- |
| معرفی پروژه | [README.fa.md](README.fa.md) | [README.md](README.md) |
| معماری کلاینت و موتور هوشمند | [ARCHITECTURE.fa.md](ARCHITECTURE.fa.md) | [ARCHITECTURE.md](ARCHITECTURE.md) |
| سیاست حفظ حریم خصوصی | [PRIVACY.fa.md](PRIVACY.fa.md) | [PRIVACY.md](PRIVACY.md) |

---

## 🔒 توضیح دربارهٔ مخزن عمومی (Showcase Source)

این مخزن عمومی شامل **طراحی رابط کاربری (UI)، معماری کلاینت اندروید، الگوریتم انتخاب هوشمند مسیر و سازوکار امنیتی برنامه** (`V2rayNG/`) است.

برای جلوگیری از مسدودسازی زیرساخت و حفظ امنیت کاربران:
- هیچ **سرور، کانفیگ، لینک سابسکریپشن یا آدرس سرور جمع‌آوری** در این مخزن قرار نگرفته است (`assets/subscription.dat` به‌صورت نمونه خالی است).
- هیچ **توکن، کلید رمزنگاری، کوکی، پسورد یا فایل Keystore** در کدهای عمومی وجود ندارد.
- مقادیر حساس هنگام بیلد نسخهٔ رسمی از طریق متغیرهای محیطی امن درون کتابخانهٔ نیتیو (`libhotaruguard.so`) کامپایل و محافظت می‌شوند.

---

## 📲 دانلود و ارتباط با ما

- **دانلود آخرین نسخه (APK):** [صفحه Releases گیت‌هاب](https://github.com/OneForAllHub/HotaruNet/releases/latest)
- **کانال رسمی تلگرام:** [@OneForAllHub](https://t.me/OneForAllHub)
- **گزارش باگ و پیشنهادها:** [بخش Issues گیت‌هاب](https://github.com/OneForAllHub/HotaruNet/issues)

ساخته‌شده بر پایهٔ هستهٔ متن‌باز Xray-core و [2dust/v2rayNG](https://github.com/2dust/v2rayNG) تحت مجوز GPL-3.0.

</div>
