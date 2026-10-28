---
id: dk-5e3bbdc0
type: task
created: 2026-10-28
status: todo
since: 2026-10-28
area: remote
priority: P2
rank: zw
parent:
fixes: []
blocked_by: []
relates: []
---
# Make RemoteSession transport required; split credential handshake

Structure review 2026-10-28:
- RemoteSession.kt:27: the default OkHttpTvTransport ties the core to OkHttp. Make it required and construct it in RemoteViewModelFactory.
- RemoteSession.kt:154: received()/open() also do credential persistence (store the token and pin, remove on pair). Extract a pairing/credential collaborator, or record in STRUCTURE.md why it stays.
