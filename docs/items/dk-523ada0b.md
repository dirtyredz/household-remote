---
id: dk-523ada0b
type: feature
created: 2026-10-28
status: wip
since: 2026-10-28
area: ui
priority: P1
rank: z
parent:
fixes: []
blocked_by: []
relates: []
---
# Redesign the remote with a power toggle and connection status


Owner report from the first real phone test (2026-10-28): "terrible design, no way to trigger power on/off".

Implement docs/plans/remote-redesign.md: dark remote layout split into composables by responsibility, a prominent power toggle reusing the existing wake/standby code, a typed connection-status indicator, and Compose UI tests. Connection reliability is diagnosed separately; this item only adds the indicator and the typed status seam.

Acceptance: VERIFY.md fast gate passes; JVM tests cover power/status logic; Compose tests compile; attended phone check of power discoverability, standby/wake, and status indicator.

## Notes

### 2026-10-28T17:56:53.374Z · open · agent

Implemented on branch worktree-agent-a81ad857489e8c3b2 at 2c36b35; fast gate green. Not yet done: connected Compose tests (no device was attached) and the attended phone check in docs/VERIFY.md.
