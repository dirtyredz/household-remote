# Household Remote v1

Android Compose remote for Samsung TVs on the household LAN. Remote and owner editions have separate package identities; owner is a shell for future v2 parent controls. No ANU/AdGuard authority ships in v1.

Build and verify: [docs/VERIFY.md](docs/VERIFY.md). Layout: [STRUCTURE.md](STRUCTURE.md). Signing and updates: [docs/ANDROID-DISTRIBUTION.md](docs/ANDROID-DISTRIBUTION.md).

Install a debug APK, connect the phone to the same Wi-Fi, discover or add the TV's private IPv4 address, select it, then choose **Pair / re-pair** while watching that TV. Approve **TV Tuneup** within 90 seconds. Each phone pairs independently. Reconnect uses the saved certificate and encrypted token. A changed certificate requires explicit re-pair. Wake depends on the TV's standby/network settings.

Keep the real household inventory in git-ignored `config/household.local.json` (the tv-tuneup inventory shape). Export selected TV facts for private phone import:

```powershell
node src/household/export.mjs config/household.local.json .tmp/household.json
```

Transfer that local JSON privately and paste it into **Import JSON**. The checked-in `config/household.example.json` is fictional. Never commit household addresses, MACs, room/model mappings, tokens or 1Password references. Local exports stay under git-ignored `.tmp/`. The privacy gate scans tracked and unignored candidate files.

App buttons (Netflix, Prime, Disney+, Plex) open their app directly by Samsung app ID over the paired connection. A button is greyed out when the TV reports that app is not installed. **Opening …** means the request was sent, not that the app opened.

Real TV pairing, wake, app launch on each TV, latency and phone update installation remain owner acceptance steps. Automated tests use fake REST/WSS servers and disposable signing keys.
