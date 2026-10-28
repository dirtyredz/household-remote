# Features

| Capability | v1 implementation | Physical acceptance |
|---|---|---|
| Compose remote / owner shell | Distinct application IDs, identical dark remote UI (owner teaser removed) | Pending phone |
| Dark remote layout | Fits the window with no scrolling: header (TV picker, overflow), centred status row, power row, D-pad that takes the remaining height (48 dp key floor), Back/Home/Source, volume/mute/channel rockers (vertical; one horizontal row on short windows or large text), one row of app shortcuts; a running sequence swaps the shortcut row for Cancel; haptic + ripple feedback; setup lives in sheets | Owner phone feedback 2026-10-28 (v0.2.0); no-scroll layout pending phone check |
| Power toggle | Small red round key (56 dp) top-right of the remote, below the header: "Turn off" (one standby key) while a session is usable, "Turn on" (existing wake, readiness polling, reconnect) otherwise; spinner while working; progress, "Standby sent · power off not confirmed" and errors in the one-line notice slot beside it; disabled with "Phone not on Wi-Fi · Turn on needs Wi-Fi" when off Wi-Fi; state is inferred, not panel telemetry | Pending phone: standby turns the TV off, the next press wakes it |
| Connection indicator | Centred row beneath the header: typed Connected / Connecting / Reconnecting… / Approve on TV / TV unavailable — check Wi-Fi or Wake / Offline / Select a TV / Phone not on Wi-Fi (with a Wi-Fi settings button). No duplicate bottom status line; transient feedback auto-hides from the notice slot | Pending phone |
| Wi-Fi hint | `network/AndroidWifiMonitor` watches any Wi-Fi network while foregrounded; without one the status reads "Phone not on Wi-Fi" with a button to the system Wi-Fi panel, keys and power are disabled, the session parks with no attempts, and one fresh recovery window starts when Wi-Fi returns (dk-28d77a2d) | Pending phone: Wi-Fi off/on with the remote open |
| Automatic reconnect | Foreground-only recovery after a drop: 15 s WebSocket heartbeat, saved token + pinned certificate, retries after 1/2/4/8/15 s inside a 60 s window, then "TV unavailable"; one fresh ordinary key may wait 5 s and is sent once; no retry after deliberate standby, in the background, or on trust/pairing failures | Pending phone (dk-4bbfb674 acceptance) |
| Discovery / manual IP / JSON import | Bounded SSDP and REST; MAC identity; remembered selection; in the TV sheet | Pending household LAN |
| Pair / reconnect | Exact TV Tuneup name, 90-second approval, encrypted per-phone token, TV certificate pin; in the settings sheet | Pending two phones / both TVs |
| Remote keys | Navigation, home, source, volume, mute, channels; Exit in settings | Pending models / latency |
| Wake | Three magic packets on active Wi-Fi interface, bounded readiness probes | Pending standby settings |
| Calibrated shortcuts | Netflix, Prime, Disney+, Plex; per-TV local sequences, exclusive/cancellable; power cancels a running sequence | Disabled until owner calibrates |
| Distribution | Unsigned CI, local attended signer, public release updates | Pending real key / publication / phone |
| Parent controls | Absent from both flavors | v2 only |
