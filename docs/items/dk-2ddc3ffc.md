---
id: dk-2ddc3ffc
type: bug
created: 2026-11-22
status: todo
since: 2026-11-22
area: android
priority: P2
rank: zzzzzw
parent:
fixes: []
blocked_by: []
relates: []
---
# Declare/request local-network permission and show a clear in-app message when missing

On 2026-11-22 an Android update changed nearby-device / local-network permission behaviour on the owner phone: app showed Offline + Discover 'Found 0 TV(s)' while the TV was reachable. Fix was clearing app data and re-running Discover (see docs/GOTCHAS.md SSDP entry). The app declares no ACCESS_LOCAL_NETWORK / NEARBY_WIFI_DEVICES. Scope: declare and request the applicable permission(s) (ACCESS_LOCAL_NETWORK and/or NEARBY_WIFI_DEVICES per target/OS version) before Discover/connect, and show a clear in-app message with a path to app permission settings when it is missing/denied, so this never fails silently as Offline/0 TVs. File only; not started.
