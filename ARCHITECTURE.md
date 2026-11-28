# HotaruNet Client Architecture & UI Design

🌐 **Language:** **English** | [🇮🇷 فارسی (Persian)](ARCHITECTURE.fa.md)

---

## 1. High-Level Android Flow

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

## 2. Key Modules (`V2rayNG/app/src/main/`)

- **`ui/main/MinimalVpnHome.kt` & `HotaruDancingMascot.kt`**: Single-screen Void-Galaxy Compose UI featuring 4-stage dancing mascot animations, live upload/download proxy traffic counters, **Direct Apps (Bypass VPN)** split-tunneling selector, in-place **Switch Route** button, `FA / EN` instant switcher, in-app update progress (`HotaruUpdateDialog`), and project info (`HotaruAboutDialog`).
- **`handler/HotaruSmartEngine.kt`**: Adapts DNS, fragment, and candidate priorities to active Cellular/Wi-Fi networks; validates real throughput across stratified protocol tiers (`REALITY_VISION`, `XHTTP`, `WS_TLS`, `HYSTERIA2`, `WARP_DEDICATED`) without geographic bias, and caches recently verified nodes for sub-2s warm fast-reconnect.
- **`core/HotaruHealthPolicy.kt` & `core/HotaruTunnelProbe.kt`**: Evaluates payload download throughput (`~32 KiB` HTTP `Range` checks, `35s` watchdog interval, alternating single-CDN connectivity probe) and triggers seamless in-place reload failover when failures reach `>= 3` or sustained speed drops below `64 KB/s` for `3` consecutive samples.
- **`service/QSTileService.kt`**: Autonomous Quick Settings tile starting `HotaruSmartEngine` directly without requiring the main activity to be open.
- **`handler/HotaruCommunityCounter.kt`**: Privacy-preserving aggregate online session indicator backed by HMAC-signed ephemeral installation heartbeats (60s cadence, 180s server TTL, instant leave on disconnect, platform & version metadata) on Supabase Edge Functions + PostgreSQL.
- **`handler/UpdateCheckerManager.kt` & `handler/HotaruApkUpdater.kt`**: Queries `https://api.github.com/repos/OneForAllHub/HotaruNet/releases/latest` cleanly on launch, downloads the ABI-matched APK in-app over active proxy or direct connection, verifies integrity, and launches the system package installer.
- **`util/HotaruCrypto.kt` + `cpp/hotaru_guard.cpp`**: JNI native vault keeping AES keys and private subscription credentials outside Java/DEX bytecode (`network_security_config.xml` trusts system CAs only and disables non-loopback cleartext HTTP).

---

> Note: Private backend scrapers, proxy sources, encryption keys, and API tokens are excluded from this public showcase repository.
