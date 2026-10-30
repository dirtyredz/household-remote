---
id: dk-efadc595
type: task
created: 2026-10-30
status: todo
since: 2026-10-30
area: ui
priority: P2
rank: zzzy
parent:
fixes: []
blocked_by: []
relates: []
---
# Nest ui/remote controls before it exceeds the fan-out cap


Source: v0.2.4 push review (topology lens, 2026-10-30), P2.

ui/remote/ reached the 12-code-file fan-out cap when HeartShape.kt was added. It mixes layout (RemoteScreen, RemoteHeader), controls (DirectionPad, RemoteRocker, RemoteButton, AppButtons, PowerControl, NavigationActions) and drawing/presentation helpers (HeartShape, FitText, ConnectionIndicator, RemotePresentation).

Direction: before or with the next file added here, nest a responsibility subfolder (for example ui/remote/controls/) instead of adding a 13th sibling, and update the STRUCTURE.md Layout bullet. Related: dk-fb69758d and dk-bc241114.

Acceptance: ui/remote/ is under the cap, STRUCTURE.md matches, and the fast gate passes.

## Notes

### 2026-10-30T18:17:47.477Z · open · agent

v0.2.5 review (2026-10-30): ui/remote/ reached 13 children (12 files plus a one-file keyboard/ folder); a review fix may move that folder out. Should the split target about 5-6 responsibility subfolders (pad/rocker/navigation controls, header/connection/power chrome)?
