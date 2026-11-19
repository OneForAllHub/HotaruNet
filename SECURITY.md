# Security Policy

## Reporting a Vulnerability

If you discover a security or privacy issue in **HotaruNet**, please report it responsibly via:

- **Telegram:** [@OneForAllHub](https://t.me/OneForAllHub)
- **GitHub Issues (non-sensitive bug reports):** [OneForAllHub/HotaruNet/issues](https://github.com/OneForAllHub/HotaruNet/issues)

## Security Architecture Notes

- **Network Security Config:** Non-loopback cleartext HTTP is disabled (`network_security_config.xml`) and user-installed root CAs are not trusted by default.
- **No Hardcoded Credentials in Public Source:** This public repository omits all subscription secrets, signing keystores, API tokens, and private endpoints. Official release APKs store build-time protected runtime configuration inside compiled native JNI memory (`libhotaruguard.so`) and are signed with APK Signature Scheme v2/v3.
- **APK Integrity Verification:** Always download official APKs exclusively from [`OneForAllHub/HotaruNet/releases`](https://github.com/OneForAllHub/HotaruNet/releases/latest) or [@OneForAllHub](https://t.me/OneForAllHub) and verify SHA-256 hashes published in each release's notes.
