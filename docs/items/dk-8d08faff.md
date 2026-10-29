---
id: dk-8d08faff
type: task
created: 2026-10-28
status: todo
since: 2026-10-28
area: ui
priority: P2
rank: zzz
parent:
fixes: []
blocked_by: []
relates: []
---
# One home for enum-to-string-resource mapping in remote UI

Structure review 2026-10-28 (abstraction): PowerControl.kt:21 uses a public powerLabelText() while ConnectionIndicator.kt:37 maps inline inside the composable. Put the resource mapping in one consistent place (next to the enums in RemotePresentation, or inline everywhere).
