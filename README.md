<div align="center">

# ⚡ HotaruNet

**Autonomous, Protocol-Adaptive Android VPN Client**

🌐 **Language:** **English** | [🇮🇷 فارسی (Persian)](README.fa.md)

[![Telegram](https://img.shields.io/badge/Telegram-@OneForAllHub-26A5E4?style=for-the-badge&logo=telegram)](https://t.me/OneForAllHub)
[![Releases](https://img.shields.io/badge/Releases-Download%20APK-00F5D4?style=for-the-badge&logo=github)](https://github.com/OneForAllHub/HotaruNet/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024--35)-3DDC84?style=for-the-badge&logo=android)](https://github.com/OneForAllHub/HotaruNet)

</div>

---

> ⭐ **Support the Project:** If HotaruNet keeps you connected, please **give this repository a Star** on GitHub and join our Telegram channel **[@OneForAllHub](https://t.me/OneForAllHub)**! Your support keeps updates, server infrastructure research, and development alive.

---

## ✨ What Makes HotaruNet Different?

HotaruNet is built with a **Zero-Config Cyber-Anime Compose UI** and an **Autonomous Multi-Stage Auto-Pilot Engine** (`HotaruSmartEngine`) designed for restrictive networks and aggressive DPI throttling:

- **One-Tap Autonomous Connection:** No manual server hunting or confusing config lists — tap once and HotaruNet selects the fastest healthy tunnel for your exact network (Cellular / Wi-Fi / ADSL / Fiber).
- **Real Bandwidth Probing Over Raw Ping:** Instead of trusting empty TCP/204 pings, HotaruNet verifies sustained ~32 KiB payload download throughput and combines throughput (`Bps`) with latency.
- **Multi-Protocol Adaptive Selection:** Automatically probes across:
  - `VLESS + REALITY` (TCP 443 + Vision)
  - `Cloudflare WARP Dedicated` (per-device ephemeral WireGuard registration + Anycast endpoint rotation)
  - `Hysteria 2` (QUIC / UDP congestion-controlled transport)
  - `XHTTP / SplitHTTP` & `WebSocket + TLS`
- **Live Session Watchdog & Seamless Failover:** Actively probes throughput every 25–35 seconds (`FAST_BYTES_PER_SECOND = 64 KB/s`) and switches automatically to the next verified node on sustained slowdown or failure.
- **Bilingual UI (English / فارسی):** Full instant switching across the main screen, notifications, Update popup, First-Connect guide, and About screen.
- **Automatic GitHub Release Update Checker:** Silently checks for newer releases on launch and lets users jump straight to the GitHub Releases page (with zero popups or noise when up-to-date).

---

## 📚 Documentation (English / فارسی)

| Document | English | فارسی |
| --- | --- | --- |
| Project Overview | [README.md](README.md) | [README.fa.md](README.fa.md) |
| Architecture & Engine Design | [ARCHITECTURE.md](ARCHITECTURE.md) | [ARCHITECTURE.fa.md](ARCHITECTURE.fa.md) |
| Privacy Policy | [PRIVACY.md](PRIVACY.md) | [PRIVACY.fa.md](PRIVACY.fa.md) |

---

## 🔒 Public Showcase Repository Notice

This public repository contains the **application UI, Android client architecture, smart routing/failover engine, and cryptographic verification design** (`V2rayNG/`).

To protect infrastructure stability and user security:
- **No servers, node URIs, or proxy configs** are committed (`assets/subscription.dat` is a placeholder).
- **No private feed URLs, API tokens, cookies, signing keystores, or telemetry credentials** are included.
- Sensitive endpoints (`HOTARU_FEED_TOKEN`, `HOTARU_TELEMETRY_SECRET`, custom AES keys) are designed to be injected cleanly at build time into native JNI memory (`libhotaruguard.so`).

---

## 📲 Download & Community

- **Latest APK Downloads:** [GitHub Releases](https://github.com/OneForAllHub/HotaruNet/releases/latest)
- **Official Telegram Channel:** [@OneForAllHub](https://t.me/OneForAllHub)
- **Bug Reports & Feature Requests:** [GitHub Issues](https://github.com/OneForAllHub/HotaruNet/issues)

Based on the open-source [2dust/v2rayNG](https://github.com/2dust/v2rayNG) & Xray-core foundations under GPL-3.0.
