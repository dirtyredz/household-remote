# Features

| Capability | v1 implementation | Physical acceptance |
|---|---|---|
| Compose remote / owner shell | Distinct application IDs, identical dark remote UI (owner teaser removed) | Pending phone |
| Dark remote layout | Header with TV picker and overflow; fixed power control; D-pad, Back/Home/Source, volume/mute/channel rockers, app shortcuts; 56–64 dp targets, haptic + ripple feedback; lower controls scroll at large text; setup and quick actions live in sheets, with updates on a separate Settings screen | Owner reported the old layout unusable on a phone (2026-10-28); redesign pending phone check |
| Power toggle | One full-width control: "Turn off" (one standby key) while a session is usable, "Turn on" (existing wake, readiness polling, reconnect) otherwise; progress and persistent inline errors; state is inferred, not panel telemetry | Pending phone: standby turns the TV off, the next press wakes it |
| Connection indicator | Typed Connected / Connecting / Reconnecting… / Approve on TV / TV unavailable — check Wi-Fi or Wake / Offline / Select a TV slot under the TV name | Pending phone |
| Automatic reconnect | Foreground-only recovery after a drop: 15 s WebSocket heartbeat, saved token + pinned certificate, retries after 1/2/4/8/15 s inside a 60 s window, then "TV unavailable"; one fresh ordinary key may wait 5 s and is sent once; no retry after deliberate standby, in the background, or on trust/pairing failures | Pending phone (dk-4bbfb674 acceptance) |
| Discovery / manual IP / JSON import | Bounded SSDP and REST; MAC identity; remembered selection; in the TV sheet | Pending household LAN |
| Pair / reconnect | Exact TV Tuneup name, 90-second approval, encrypted per-phone token, TV certificate pin; in the overflow quick-actions sheet | Pending two phones / both TVs |
| Remote keys | Navigation, home, source, volume, mute, channels; Exit in overflow quick actions | Pending models / latency |
| Wake | Three magic packets on active Wi-Fi interface, bounded readiness probes | Pending standby settings |
| Calibrated shortcuts | Netflix, Prime, Disney+, Plex; per-TV local sequences, exclusive/cancellable; power cancels a running sequence | Disabled until owner calibrates |
| Distribution | Unsigned CI, local attended signer, public release updates; current version, manual update check, and install under Settings > App updates | Pending real key / publication / phone |
| Parent controls | Absent from both flavors | v2 only |
