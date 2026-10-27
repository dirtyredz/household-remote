# Gotchas

- The original plan is historical; its monorepo/Views/helper instructions and verification commands are superseded by CLAUDE.md and VERIFY.md.
- Pair each phone independently. Shared client name does not imply shared tokens. Two-phone coexistence needs physical acceptance.
- REST information is unauthenticated. Never trust a discovered IP as identity; WSS pins are keyed by MAC. A changed certificate requires re-pair while watching the correct TV.
- Multicast may be filtered; manual IP is supported. Wake depends on TV/network settings and a usable Wi-Fi broadcast interface.
- Home-bar positions and delays drift. Recalibrate while watching; cancel immediately if navigation is wrong.
- No phone is attached during implementation. Keystore, Android installer, multicast permissions, actual TV response and latency need device acceptance.
- Gradle's shared daemon registry crosses worktrees: another checkout's `gradlew --stop` can stop a single-use build here. Wait for other builds and do not issue global daemon stops. The installed SDK command-line tools emit an SDK XML v4/v3 compatibility warning; no SDK download is needed to address app source failures.

- Household inventory belongs only in ignored `config/household.local.json` or `.tmp/`; examples are fictional. The privacy gate rejects the household subnet and non-placeholder 1Password references.
