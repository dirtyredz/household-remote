---
id: dk-dc2abcf5
type: task
created: 2026-10-28
status: todo
since: 2026-10-28
area: apps
priority: P2
rank: zzn
parent:
fixes: []
blocked_by: []
relates: []
---
# Share the TV information-port client and private-LAN guard

Structure review 2026-10-28 (abstraction): apps/AppInstallClient.kt:24-53 nearly duplicates discovery/TvInfoClient.kt:20-62: the same 3 s/no-redirect/no-retry OkHttp config, the privateIp-or-loopback guard with the same message, and the same close(). Extract a shared helper (client factory plus requirePrivateLan(ip, allowLoopback)).
