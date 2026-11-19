<div align="center">

<img src="V2rayNG/app/src/main/ic_launcher-web.png" alt="HotaruNet Android VPN Logo" width="112" height="112" />

# ⚡ HotaruNet — Autonomous Android VPN Client

**Free, Open-Source, Zero-Config Anti-Censorship VPN for Android (VLESS REALITY • Hysteria 2 • Cloudflare WARP • XHTTP • Xray-core)**

🌐 **Language:** **English** | [🇮🇷 فارسی (Persian)](README.fa.md)

[![Latest Release](https://img.shields.io/github/v/release/OneForAllHub/HotaruNet?style=for-the-badge&color=00F5D4&logo=github)](https://github.com/OneForAllHub/HotaruNet/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/OneForAllHub/HotaruNet/total?style=for-the-badge&color=00E5FF&logo=android)](https://github.com/OneForAllHub/HotaruNet/releases/latest)
[![Telegram](https://img.shields.io/badge/Telegram-@OneForAllHub-26A5E4?style=for-the-badge&logo=telegram)](https://t.me/OneForAllHub)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024--35)-3DDC84?style=for-the-badge&logo=android)](https://github.com/OneForAllHub/HotaruNet/releases/latest)
[![License](https://img.shields.io/badge/License-GPL--3.0-A855F7?style=for-the-badge)](LICENSE)

[📥 **Download APK (v2.3.43)**](#-download-latest-apk-v2343) • [📱 **Screenshots (EN / FA)**](#-screenshots--bilingual-ui) • [✨ **Features**](#-what-makes-hotarunet-different) • [🏗️ **Architecture**](ARCHITECTURE.md) • [❓ **FAQ**](#-frequently-asked-questions-faq)

</div>

---

> ⭐ **Support Open Internet Access:** If HotaruNet helps you stay connected reliably, please **Star this repository** on GitHub and join **[@OneForAllHub](https://t.me/OneForAllHub)** on Telegram for instant release alerts and updates.

---

## 📱 Screenshots — Bilingual UI (English & فارسی)

HotaruNet ships with an instant in-app **`FA / EN`** switcher supporting full **English (LTR)** and **Persian / فارسی (RTL)** layouts across every dialog, live traffic telemetry card, and status indicator.

<table align="center">
  <thead>
    <tr>
      <th align="center">🇬🇧 English — Connected VPN</th>
      <th align="center">🇮🇷 فارسی — وضعیت متصل</th>
      <th align="center">🇬🇧 English — Smart Home</th>
      <th align="center">🇮🇷 فارسی — صفحه اصلی</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><img src="assets/screenshots/connected-en.png" alt="HotaruNet Android VPN Connected Screen in English UI" width="210" /></td>
      <td align="center"><img src="assets/screenshots/connected-fa.png" alt="HotaruNet Android VPN Connected Screen in Persian RTL UI" width="210" /></td>
      <td align="center"><img src="assets/screenshots/home-en.png" alt="HotaruNet Android VPN Home Screen in English UI" width="210" /></td>
      <td align="center"><img src="assets/screenshots/home-fa.png" alt="HotaruNet Android VPN Home Screen in Persian UI" width="210" /></td>
    </tr>
  </tbody>
</table>

<details>
<summary>🖼️ View About & Bilingual Support Screens (EN / فارسی)</summary>
<br />
<table align="center">
  <thead>
    <tr>
      <th align="center">🇬🇧 About & Support (English)</th>
      <th align="center">🇮🇷 درباره ما و حمایت (فارسی)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><img src="assets/screenshots/about-en.png" alt="HotaruNet About Dialog in English" width="240" /></td>
      <td align="center"><img src="assets/screenshots/about-fa.png" alt="HotaruNet About Dialog in Persian" width="240" /></td>
    </tr>
  </tbody>
</table>
</details>

---

## 📥 Download Latest APK (`v2.3.43`)

Get the signed Android APK directly from [**GitHub Releases (Latest)**](https://github.com/OneForAllHub/HotaruNet/releases/latest) — **no subscription links, account registration, or manual config imports required**:

| File Name | Target Devices / CPU Architecture | Recommended For |
| --- | --- | --- |
| [`HotaruNet_2.3.43_arm64-v8a.apk`](https://github.com/OneForAllHub/HotaruNet/releases/latest) | **ARM64 (64-bit)** (~26.3 MB) | Almost all modern Android smartphones |
| [`HotaruNet_2.3.43_armeabi-v7a.apk`](https://github.com/OneForAllHub/HotaruNet/releases/latest) | **ARMv7 (32-bit)** (~26.8 MB) | Older Android phones & tablets |
| [`HotaruNet_2.3.43_x86_64.apk`](https://github.com/OneForAllHub/HotaruNet/releases/latest) | **x86_64** (~27.3 MB) | Chromebooks & PC Android emulators |
| [`HotaruNet_2.3.43_x86.apk`](https://github.com/OneForAllHub/HotaruNet/releases/latest) | **x86** (~27.8 MB) | Legacy 32-bit x86 emulators |
| [`HotaruNet_2.3.43_universal.apk`](https://github.com/OneForAllHub/HotaruNet/releases/latest) | **Universal (All ABIs)** (~64.2 MB) | Unsure which CPU your phone uses |

> 💡 All release APKs carry SHA-256 verification checksums on the [Releases page](https://github.com/OneForAllHub/HotaruNet/releases/latest) and work out-of-the-box on Android 7.0 through Android 15 (API 24–35).

---

## ✨ What Makes HotaruNet Different?

HotaruNet combines a **Zero-Config Cyber-Anime Jetpack Compose UI** with an **Autonomous Multi-Stage Auto-Pilot Engine** (`HotaruSmartEngine`) built specifically for high-DPI throttling networks and mobile carriers:

- **One-Tap Zero-Config Auto-Pilot:** No broken public server lists, expired JSONs, or manual tweaking. Tap **CONNECT** and HotaruNet adapts MTU, fragment strategy, DNS, and server candidates to your active network (Wi-Fi, LTE/5G Cellular, ADSL, Fiber).
- **Real Download Throughput Over Empty Pings:** Instead of relying on raw TCP ping or empty `HTTP 204` responses, HotaruNet verifies sustained **~32 KiB** download throughput (`HotaruTunnelProbe`) and scores nodes primarily on empirical bandwidth (`Bps`) combined with stability.
- **5-Protocol Anti-Censorship Shield:** Dynamically probes across stratified transports to route around ISP-specific throttling:
  1. **`VLESS + REALITY`** (TCP 443 + XTLS Vision flow)
  2. **`Cloudflare WARP Dedicated`** (per-device ephemeral WireGuard key registration + clean Anycast endpoints)
  3. **`Hysteria 2`** (QUIC / UDP congestion-controlled transport)
  4. **`XHTTP / SplitHTTP`** (HTTP/2 & HTTP/3 CDN-resilient transport)
  5. **`WebSocket + TLS`** (reverse-proxy fallback)
- **Lightweight Live Watchdog & Seamless Auto-Failover:** Checks tunnel health with minimal background data usage (`~32 KiB` HTTP Range checks every `35s`, single-CDN alternating `204` checks, and `30–60 min` smart background intervals). If throughput falls below `64 KB/s` for 3 consecutive samples or fails 3 times, HotaruNet silently switches to the next fastest route.
- **No Artificial Geographic Locks:** Nodes across North America, Europe, and Asia compete fairly on real measured throughput and stability rather than artificial country filters.
- **Silent GitHub Auto-Update Notifications:** Checks [`OneForAllHub/HotaruNet/releases/latest`](https://github.com/OneForAllHub/HotaruNet/releases/latest) automatically on startup, staying completely silent when you are on the latest version.

---

## 📚 Documentation (English / فارسی)

| Document | English | فارسی |
| --- | --- | --- |
| Project Overview & Screenshots | [README.md](README.md) | [README.fa.md](README.fa.md) |
| Client Architecture & Failover Engine | [ARCHITECTURE.md](ARCHITECTURE.md) | [ARCHITECTURE.fa.md](ARCHITECTURE.fa.md) |
| Privacy Policy (Zero-Log Architecture) | [PRIVACY.md](PRIVACY.md) | [PRIVACY.fa.md](PRIVACY.fa.md) |
| Security Policy | [SECURITY.md](SECURITY.md) | [SECURITY.md](SECURITY.md) |

---

## ❓ Frequently Asked Questions (FAQ)

<details>
<summary><b>How do I use HotaruNet? Do I need a V2Ray subscription or config?</b></summary>
<p>No configuration is required. Install the latest APK, launch the app, choose your language (English or فارسی), and tap <b>CONNECT</b>. The Smart Auto-Pilot handles discovery, throughput verification, and automatic failover for you.</p>
</details>

<details>
<summary><b>Which APK file should I download for my Android phone?</b></summary>
<p>Most phones from the last 7+ years use <code>HotaruNet_2.3.43_arm64-v8a.apk</code> (~26 MB). If you are unsure or installing across multiple devices, choose <code>HotaruNet_2.3.43_universal.apk</code>.</p>
</details>

<details>
<summary><b>Does the background speed check waste mobile data?</b></summary>
<p>No. Watchdog throughput validation uses compact <code>32 KiB</code> HTTP Range probes every <code>35 seconds</code> while connected (~3.2 MB/hour) with alternating single-target reachability checks and reduced background refresh cadence when healthy.</p>
</details>

<details>
<summary><b>Does HotaruNet log browsing activity or personal data?</b></summary>
<p>Never. See <a href="PRIVACY.md">PRIVACY.md</a> — no browsing history, DNS queries, IPs, or hardware IDs are collected. Only an anonymous random session UUID is used for aggregate online user counts.</p>
</details>

---

## 🔒 Public Showcase Repository Notice

This public repository publishes the **Android application UI, client auto-pilot routing engine, health policy, failover state machine, and cryptographic verification architecture** (`V2rayNG/`).

To protect infrastructure resilience and user security:
- **No servers, node URIs, or proxy configs** are committed (`assets/subscription.dat` is an empty placeholder).
- **No private feed URLs, API tokens, cookies, signing keystores, or telemetry credentials** are present in source control.
- Sensitive endpoints (`HOTARU_FEED_TOKEN`, `HOTARU_TELEMETRY_SECRET`, custom AES keys) are injected strictly at build time into compiled native JNI memory (`libhotaruguard.so`).

---

## 📲 Community & Credits

- **Official Releases:** [GitHub Releases](https://github.com/OneForAllHub/HotaruNet/releases/latest)
- **Telegram Community:** [@OneForAllHub](https://t.me/OneForAllHub)
- **Bug Reports & Feature Requests:** [GitHub Issues](https://github.com/OneForAllHub/HotaruNet/issues)

Built with Jetpack Compose and grounded on open-source [Xray-core](https://github.com/XTLS/Xray-core) & [2dust/v2rayNG](https://github.com/2dust/v2rayNG) under GPL-3.0.
