---
id: dk-d8a9bd39
type: feature
created: 2026-10-28
status: done
since: 2026-10-28
area: ui
priority: P1
rank: zn
parent:
fixes: []
blocked_by: []
relates: []
---
# Move update controls into a separate Settings screen

Owner request (2026-10-28): updates belong in a separate settings menu. The overflow sheet keeps quick actions plus a Settings entry; Settings opens a back-dismissible full-screen surface with an App updates section containing current version, manual check, and install controls. Update controls no longer appear in the overflow sheet. Update STRUCTURE.md, docs/FEATURES.md, and relevant Compose coverage. Acceptance: the exact docs/VERIFY.md fast gate passes; compile Compose instrumentation tests when no device is attached; report phone-visible behavior. No new dependencies, live TV contact, merge, or push.
