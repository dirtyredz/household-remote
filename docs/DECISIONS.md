# Decisions

- Owner overrides the original plan: separate household-remote repo, Compose, phases 1/2/3/6 only. `owner` is a distinct package with identical remote functionality and no administration authority. No helper, enrollment, AdGuard or timer code in v1.
- Command Deck toolchain: AGP 8.13.2, Kotlin 2.3.21, Compose BOM 2026.03.01, SDK 36/min 26, JVM 17. Command Deck has no ktlint Gradle setup; use Agent Eye's ktlint 14.2.0 plugin. JSON suffices; no Room.
- TV credentials are independent per phone, Keystore AES-GCM encrypted in no-backup storage. Explicit pairing permits first-use TV certificate trust; reconnect pins the leaf certificate. Changed certificates require explicit re-pair. Trust exceptions never apply to updates.
- The source repository `dirtyredz/household-remote` is public. CI drafts unsigned artifacts here; attended signing replaces draft assets and publishes this same release last. The app reads this repository anonymously. No separate releases repository or GitHub credentials in the app.
- Updates are manual, forward-only, exact package/flavor and installed-certificate matched. Android package installation remains attended. Release Gradle builds never access the permanent key or 1Password.
- Shortcut profiles are local and disabled until calibrated while watching the TV. Success means sequence sent, not confirmed launch. Standby is one key with no retry; ordinary taps have no artificial delay.
