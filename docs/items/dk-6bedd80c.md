---
id: dk-6bedd80c
type: task
created: 2026-10-28
status: todo
since: 2026-10-28
area:
priority: P3
rank: n
parent:
fixes: []
blocked_by: []
relates: []
---
# Retire LegacyShortcuts migration; decide persistence/ single-file package

Structure review 2026-10-28:
- (componentization) apps/LegacyShortcuts.kt is a one-time v0.2.0 cleanup sitting in apps/. Delete it and its STRUCTURE clause once v0.2.0 installs are gone, or record the removal condition in GOTCHAS.
- (topology) persistence/ holds only AtomicWrite.kt. Keep it as a declared seam, or move the file to its caller.
