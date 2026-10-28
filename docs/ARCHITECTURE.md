# Architecture

Compose → lifecycle-owned controller → one selected-TV WSS session. SSDP yields bounded private IPv4 candidates; REST :8001 resolves MAC identity. Inventory restores selection before discovery and merges IP drift by MAC. Manual IP and local JSON import work without multicast.

TV information alone uses plaintext HTTP. Pairing uses :8002, samsung.remote.control, exact base64 `TV Tuneup`, a 90-second approval window and token refresh. An isolated TLS client accepts a first certificate only on explicit pairing, then pins it. Credentials never enter backups or logs. Disconnect/selection/background cancel outstanding input and shortcuts; reconnect never replays input.

WOL broadcasts a bounded burst on the active Wi-Fi network and polls information before reconnect. Shortcut sequences own input exclusively and cancel on lifecycle/selection change.

UI: `MainActivity` → `RemoteApp` (lifecycle START/STOP, ViewModel collection, which sheet/dialog shows) → stateless `ui/remote` composables fed an immutable `RemoteState` and a callback set. Presentation is a pure mapping (`RemotePresentation`) from typed state to labels, so it is JVM-testable and the Compose tests mount it with fake state. Setup (TV list, discovery, manual IP, import), pairing, calibration, Exit and updates live in sheets/dialogs off the default surface; pairing and calibration bind to the TV they were opened for.

Connection presentation boundary: `RemoteSession` emits `ConnectionStatus` at its transition sites; the ViewModel stores it as `RemoteState.connection`, starts input on Connected, and forwards it to the power coordinator. General `message` text is feedback only and never parsed for state.

Power: `PowerController` (power package, no Compose) decides intent when a press is accepted (usable session → one standby frame via `KeyDispatcher.standby()`; otherwise disconnect → WOL → readiness polling → reconnect), tracks phase/off-intent/error, and binds each completion to an activation generation. Selection, background and explicit reconnect/re-pair reset it. It reaches the session only through the narrow `PowerLink` the ViewModel implements, which reconnects only the still-selected TV while foregrounded.

Release path: CI builds unsigned APK/AAB and provenance, then drafts a release in this public repository. Attended local signing verifies the CI inputs, replaces unsigned draft assets, checks downloaded bytes and publishes last. The app checks this repository manually, downloads to private cache, validates APK metadata and signer, then opens the system installer.

The `remote` and `owner` product flavors have distinct package IDs but the same v1 remote behavior; `owner` conveys audience only and grants no administration authority. Each published `update.json` has schema 1 and exactly one APK record per flavor, binding flavor, package name, forward-only version code/name, byte size, SHA-256, and the canonical release URL. Selection validates that contract before download; installation additionally requires archive package/version and signer set to match the installed app.
