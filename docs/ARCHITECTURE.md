# Architecture

Compose → lifecycle-owned controller → one selected-TV WSS session. SSDP yields bounded private IPv4 candidates; REST :8001 resolves MAC identity. Inventory restores selection before discovery and merges IP drift by MAC. Manual IP and local JSON import work without multicast.

TV information alone uses plaintext HTTP. Pairing uses :8002, samsung.remote.control, exact base64 `TV Tuneup`, a 90-second approval window and token refresh. An isolated TLS client accepts a first certificate only on explicit pairing, then pins it. Credentials never enter backups or logs. Disconnect/selection/background cancel outstanding input and shortcuts; reconnect never replays input.

WOL broadcasts a bounded burst on the active Wi-Fi network and polls information before reconnect. Shortcut sequences own input exclusively and cancel on lifecycle/selection change.

Release path: private CI → unsigned APK/AAB plus hashes/provenance → private draft → attended local verification/signing → public release draft → downloaded-byte verification → publication last. The app checks the public release manually, downloads to private cache, validates APK metadata and signer, then opens the system installer.
