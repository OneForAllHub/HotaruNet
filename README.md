<div align="center">

<img src="V2rayNG/app/src/main/ic_launcher-web.png" alt="HotaruNet Autonomous VPN Logo" width="112" height="112" />

# ⚡ HotaruNet — Autonomous Android & Windows VPN Client

**Free, Open-Source, Zero-Config Anti-Censorship VPN Client for Android & Windows 10/11**  
*(VLESS REALITY • Hysteria 2 • Cloudflare WARP • XHTTP / SplitHTTP • Xray-core • Wintun TUN)*

🌐 **Language:** **English** | [🇮🇷 فارسی (Persian)](README.fa.md)

[![Latest Release](https://img.shields.io/github/v/release/OneForAllHub/HotaruNet?style=for-the-badge&color=00F5D4&logo=github)](https://github.com/OneForAllHub/HotaruNet/releases/latest)
[![Windows](https://img.shields.io/badge/Windows-10%20%2F%2011%20(x64)-0078D6?style=for-the-badge&logo=windows)](https://github.com/OneForAllHub/HotaruNet/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024--35)-3DDC84?style=for-the-badge&logo=android)](https://github.com/OneForAllHub/HotaruNet/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/OneForAllHub/HotaruNet/total?style=for-the-badge&color=00E5FF&logo=github)](https://github.com/OneForAllHub/HotaruNet/releases/latest)
[![Telegram](https://img.shields.io/badge/Telegram-@OneForAllHub-26A5E4?style=for-the-badge&logo=telegram)](https://t.me/OneForAllHub)
[![License](https://img.shields.io/badge/License-GPL--3.0-A855F7?style=for-the-badge)](LICENSE)

[📥 **Download for Windows**](#-download-for-windows-pc--laptop) • [📥 **Download APK for Android**](#-download-for-android-smartphones--tablets) • [📱 **UI & Screenshots**](#-screenshots--bilingual-ui) • [✨ **Features**](#-what-makes-hotarunet-different) • [🔔 **Update Alerts**](#-instant-release-notifications-android--windows-parity) • [🏗️ **Architecture**](ARCHITECTURE.md) • [❓ **FAQ**](#-frequently-asked-questions-faq)

</div>

---

> ⭐ **Support Open Internet Access:** If HotaruNet helps you stay connected reliably, please **Star this repository** on GitHub and join **[@OneForAllHub](https://t.me/OneForAllHub)** on Telegram for instant release alerts and updates.

---

## 📥 Download Latest Releases (`v2.3.45`)

HotaruNet is available natively for both **Windows (PC/Laptop)** and **Android**. No subscription URLs, manual config imports, or account registration needed.

### 🪟 Download for Windows (PC / Laptop)

<table align="center">
  <thead>
    <tr>
      <th align="center">🪟 Windows — Ready &amp; Mode Selector</th>
      <th align="center">🔌 Windows — No Proxy (Local Ports)</th>
      <th align="center">🇮🇷 ویندوز — حالت بدون پروکسی (فارسی)</th>
      <th align="center">🤖 Windows — Animated 4-Stage Connect</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><img src="assets/screenshots/windows-home-en.png" alt="HotaruNet v2.3.45 Windows VPN home screen with System Proxy, No Proxy, and Wintun TUN mode" width="210" /></td>
      <td align="center"><img src="assets/screenshots/windows-connected-noproxy-en.png" alt="HotaruNet v2.3.45 Windows No Proxy mode showing local SOCKS5 and HTTP proxy ports with copy buttons" width="210" /></td>
      <td align="center"><img src="assets/screenshots/windows-connected-noproxy-fa.png" alt="اسکرین‌شات ویندوز هوتارو نت v2.3.45 در حالت بدون پروکسی فارسی با نمایش پورت محلی" width="210" /></td>
      <td align="center"><img src="assets/screenshots/windows-stage-robot-en.png" alt="HotaruNet v2.3.45 Windows 4-stage animated Cyber Robot connection progress" width="210" /></td>
    </tr>
  </tbody>
</table>

High-performance portable client featuring **3 flexible routing modes**: **System Proxy**, **No Proxy** (local `SOCKS5` + `HTTP` listener on dynamic free ports with 1-click copy, leaving Windows proxy untouched for IDEs/browsers/tools), and **Wintun TUN Mode** (system-wide routing for games, terminal, and desktop apps).

| Release Package | Architecture & OS | Size | Description & SHA-256 Checksum |
| --- | --- | --- | --- |
| [**`HotaruNet_v2.3.45_windows_x64.zip`**](https://github.com/OneForAllHub/HotaruNet/releases/download/v2.3.45/HotaruNet_v2.3.45_windows_x64.zip) | **Windows 10 / 11 (64-bit)** | **45.8 MB** | **Standalone Portable Archive** (Zero-Install). Includes `HotaruNet.exe`, privileged TUN service, obfuscated Xray-core DLL, and official Wintun driver.<br/>`SHA-256: 25567243E173AD581F3ED9397973C1FCF5E503233EB517AD5F6AE963F1194CB5` |

#### 🚀 Quick Start for Windows:
1. Download [**`HotaruNet_v2.3.45_windows_x64.zip`**](https://github.com/OneForAllHub/HotaruNet/releases/download/v2.3.45/HotaruNet_v2.3.45_windows_x64.zip) and **extract the entire ZIP archive** to a folder first *(do not double-click `HotaruNet.exe` directly inside WinRAR / 7-Zip / ZIP preview, so `libhotarucore.dll`, `WebView2Loader.dll`, `wintun.dll`, and `assets/` load side-by-side)*.
2. Run **`HotaruNet.exe`**.
3. Choose your mode:
   - **System Proxy Mode** (Default): Works immediately without Administrator rights. Automatically configures and restores Windows system proxy.
   - **No Proxy Mode** *(New in v2.3.45)*: Starts the fast autonomous local tunnel (`127.0.0.1` `SOCKS5` + `HTTP` on conflict-free dynamic ports) without touching Windows Registry proxy settings, displaying live copy buttons for manual app/IDE/terminal setup.
   - **TUN Mode**: Full virtual network adapter routing. Right-click `HotaruNet.exe` and select *Run as administrator* to enable system-wide Wintun routing.
4. Click **CONNECT** to watch the 4-stage animated Cyber Robot sync fresh cloud mirrors, sweep speeds, and lock onto the fastest route!

> 💡 **Zero-Prerequisite Portable Build:** Runs out-of-the-box on 64-bit Windows 11 and standard Windows 10 — **no `.NET`, no `VC++ Redistributable`, and no separate driver installer required**. Only stripped custom OS editions (*Windows 10 LTSC / Lite* where Edge was completely removed) require Microsoft's 2 MB [Edge WebView2 Evergreen Runtime](https://developer.microsoft.com/en-us/microsoft-edge/webview2/), and Administrator rights are only needed for optional **TUN Mode**.

---

### 📱 Download for Android (Smartphones & Tablets)

Official signed APK packages directly from [**GitHub Releases (`v2.3.45`)**](https://github.com/OneForAllHub/HotaruNet/releases/latest):

| File Name | Target Devices / CPU Architecture | Recommended For |
| --- | --- | --- |
| [`HotaruNet_2.3.45_arm64-v8a.apk`](https://github.com/OneForAllHub/HotaruNet/releases/download/v2.3.45/HotaruNet_2.3.45_arm64-v8a.apk) | **ARM64 (64-bit)** (28.7 MB) | Almost all modern Android smartphones (Recommended) |
| [`HotaruNet_2.3.45_armeabi-v7a.apk`](https://github.com/OneForAllHub/HotaruNet/releases/download/v2.3.45/HotaruNet_2.3.45_armeabi-v7a.apk) | **ARMv7 (32-bit)** (29.3 MB) | Older Android phones & tablets |
| [`HotaruNet_2.3.45_x86_64.apk`](https://github.com/OneForAllHub/HotaruNet/releases/download/v2.3.45/HotaruNet_2.3.45_x86_64.apk) | **x86_64** (29.8 MB) | Chromebooks & PC Android emulators |
| [`HotaruNet_2.3.45_x86.apk`](https://github.com/OneForAllHub/HotaruNet/releases/download/v2.3.45/HotaruNet_2.3.45_x86.apk) | **x86** (30.2 MB) | Legacy 32-bit x86 emulators |
| [`HotaruNet_2.3.45_universal.apk`](https://github.com/OneForAllHub/HotaruNet/releases/download/v2.3.45/HotaruNet_2.3.45_universal.apk) | **Universal (All ABIs)** (66.6 MB) | Unsure which CPU your phone uses |

> 💡 All release packages carry SHA-256 verification checksums on the [Releases page](https://github.com/OneForAllHub/HotaruNet/releases/latest) and work out-of-the-box on Android 7.0 through Android 15 (API 24–35).

---

## 🔔 Instant Release Notifications (Android & Windows Parity)

HotaruNet implements a unified silent update checker across both Android and Windows clients:
- **Silent Background Probing:** On launch and 1.5s after connection, HotaruNet checks GitHub releases quietly.
- **Prominent Update Dialog:** Whenever a new version is published, the app pops up an immediate Cyber-Anime modal displaying:
  - Exact version transition (`vCurrent ➔ vLatest`)
  - Formatted release notes and speed improvements
  - Direct 1-click download button opening GitHub Releases
  - Remind me later option
- Stays completely silent when you are already running the latest version.

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
<summary>🖼️ View 4-Stage Animated Cyber Robot, About & Creator Bot Screens (EN / فارسی)</summary>
<br />
<table align="center">
  <thead>
    <tr>
      <th align="center">🤖 Stage Progress (English)</th>
      <th align="center">🤖 پیشرفت مرحله‌ای (فارسی)</th>
      <th align="center">🇬🇧 About &amp; Creator Bot</th>
      <th align="center">🇮🇷 درباره ما و ربات سازنده</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><img src="assets/screenshots/android-stage-en.png" alt="HotaruNet v2.3.45 Android 4-Stage Animated Robot Connection Screen (English)" width="210" /></td>
      <td align="center"><img src="assets/screenshots/android-stage-fa.png" alt="HotaruNet v2.3.45 Android 4-Stage Animated Robot Connection Screen (Persian)" width="210" /></td>
      <td align="center"><img src="assets/screenshots/about-en.png" alt="HotaruNet About & Contact Creator Dialog in English" width="210" /></td>
      <td align="center"><img src="assets/screenshots/about-fa.png" alt="HotaruNet About & Contact Creator Dialog in Persian" width="210" /></td>
    </tr>
  </tbody>
</table>
</details>

---

## ✨ What Makes HotaruNet Different?

HotaruNet combines a **Zero-Config Cyber-Anime UI** with an **Autonomous Multi-Stage Auto-Pilot Engine** built specifically for high-DPI throttling networks, Iranian telecom carriers (MCI, MTN Irancell, Rightel, Shatel, Mokhaberat), and restricted environments:

- **One-Tap Zero-Config Auto-Pilot & Always-Fresh Feed Racing:** On every connect, HotaruNet races resilient cloud CDN mirrors in parallel with SHA-256 deduplication so you always start with verified high-bandwidth nodes across Wi-Fi, Cellular, ADSL, and Fiber.
- **Real Download Throughput Over Empty Pings:** Instead of relying on raw TCP ping or empty `HTTP 204` responses, HotaruNet verifies sustained **~32 KiB** download throughput and scores nodes primarily on empirical bandwidth (`Bps`) combined with stability, backed by a serverless **Globalping IR Eyeball + check-host.net** hybrid verification pipeline.
- **4-Stage Animated Cyber Robot Feedback & Direct Creator Support:** Track live sync, port sweep, speed test, and route lock across 4 interactive animated robot stages, or launch Telegram directly to **[@OneForAllHubBot](https://t.me/OneForAllHubBot)** with the built-in **Contact Creator** button.
- **5-Protocol Anti-Censorship Shield:** Dynamically probes across stratified transports to route around ISP-specific throttling:
  1. **`VLESS + REALITY`** (TCP 443 + XTLS Vision flow)
  2. **`Cloudflare WARP Dedicated`** (per-device ephemeral WireGuard key registration + clean Anycast endpoints)
  3. **`Hysteria 2`** (QUIC / UDP congestion-controlled transport)
  4. **`XHTTP / SplitHTTP`** (HTTP/2 & HTTP/3 CDN-resilient transport)
  5. **`WebSocket + TLS`** (reverse-proxy fallback)
- **Three Windows Routing Modes (`v2.3.45`):**
  - **System Proxy Mode:** Fast HTTP/SOCKS system proxy toggle with safe automatic registry cleanup on exit.
  - **No Proxy Mode (`SOCKS5` + `HTTP` Local Ports):** Runs the tunnel strictly on conflict-free local ports (`127.0.0.1:10808/10809` or next free port) without modifying Windows system proxy settings, with 1-click copy badges for browsers, IDEs, or CLI tools.
  - **Wintun TUN Adapter Mode:** Full kernel-level network tunneling utilizing the official signed Wintun driver.
- **Lightweight Live Watchdog & Seamless Auto-Failover:** Checks tunnel health with minimal background data usage (`~32 KiB` HTTP Range checks every `35s`, single-CDN alternating checks). If throughput falls below `64 KB/s` for 3 consecutive samples or fails 3 times, HotaruNet silently switches to the next fastest route.
- **No Artificial Geographic Locks:** Nodes across Europe, North America, and Asia compete fairly on real measured throughput and stability without transatlantic RTT penalties or country filters.

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
<p>No configuration is required. Install the APK on Android or extract the ZIP on Windows, launch the app, choose your language (English or فارسی), and tap <b>CONNECT</b>. The Smart Auto-Pilot handles discovery, throughput verification, and automatic failover for you.</p>
</details>

<details>
<summary><b>Does Windows notify me when an update is released?</b></summary>
<p>Yes, exactly like Android. Both clients check GitHub releases silently in the background and pop up a dedicated update notification modal whenever a new version is published.</p>
</details>

<details>
<summary><b>Which Windows version is supported? Do I need to install any prerequisites first?</b></summary>
<p>HotaruNet supports 64-bit Windows 10 and Windows 11 with <b>zero installation and zero prerequisites</b> — no <code>.NET</code>, no <code>VC++ Redistributable</code>, and no separate Wintun driver setup are needed. Always <b>extract the entire ZIP archive</b> to a folder before running <code>HotaruNet.exe</code> (do not launch it directly inside WinRAR/7-Zip). Only heavily stripped custom OS builds (such as <i>Windows 10 LTSC / Lite</i> with Microsoft Edge removed) require installing Microsoft's 2 MB <a href="https://developer.microsoft.com/en-us/microsoft-edge/webview2/">Edge WebView2 Evergreen Runtime</a>, and Administrator rights are only needed if you select <b>TUN Mode</b>.</p>
</details>

<details>
<summary><b>Which APK file should I download for my Android phone?</b></summary>
<p>Most phones from the last 7+ years use <code>HotaruNet_2.3.45_arm64-v8a.apk</code> (28.7 MB). If you are unsure or installing across multiple devices, choose <code>HotaruNet_2.3.45_universal.apk</code> (66.6 MB).</p>
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

This public repository publishes the **client UI, auto-pilot routing engine, health policy, failover state machine, Windows service & TUN adapter bindings, and cryptographic verification architecture**.

To protect infrastructure resilience and user security:
- **No servers, node URIs, or proxy configs** are committed (`assets/subscription.dat` is an empty placeholder).
- **No private feed URLs, API tokens, cookies, signing keystores, or telemetry credentials** are present in source control.
- Sensitive endpoints (`HOTARU_FEED_TOKEN`, `HOTARU_TELEMETRY_SECRET`, custom AES keys) are injected strictly at build time into compiled native binaries (`libhotaruguard.so` on Android and obfuscated Rust vault on Windows).

---

## 📲 Community & Credits

- **Official Releases:** [GitHub Releases](https://github.com/OneForAllHub/HotaruNet/releases/latest)
- **Telegram Community:** [@OneForAllHub](https://t.me/OneForAllHub)
- **Bug Reports & Feature Requests:** [GitHub Issues](https://github.com/OneForAllHub/HotaruNet/issues)

Built with Jetpack Compose & Tauri 2, grounded on open-source [Xray-core](https://github.com/XTLS/Xray-core), [Wintun](https://www.wintun.net), and [2dust/v2rayNG](https://github.com/2dust/v2rayNG) under GPL-3.0.
