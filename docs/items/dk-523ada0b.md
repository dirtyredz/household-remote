---
id: dk-523ada0b
type: feature
created: 2026-10-28
status: todo
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

Owner v0.2.1 phone feedback (2026-10-28): remove the Apps heading and give its height to the D-pad; leave the notice slot empty after accepted standby while retaining inferred power state and blocked keys; put the TV picker, short non-wrapping connection status, and overflow on one header line, with longer Wi-Fi/unavailable explanations and the Wi-Fi action in the notice slot.

Implementation on feat/header-inline passes the exact VERIFY.md fast gate; JVM presentation tests pass and both flavor Compose test sources compile. Remaining acceptance is an attended phone check of the revised header/fit, standby/wake, real connection states, and physical controls; connected Compose tests were not run without an attached device.

Acceptance: VERIFY.md fast gate passes; JVM tests cover power/status logic; Compose tests compile; attended phone check of power discoverability, standby/wake, and status indicator.

## Notes

### 2026-10-28T17:56:53.374Z · resolved · agent

Implemented on branch worktree-agent-a81ad857489e8c3b2 at 2c36b35; fast gate green. Not yet done: connected Compose tests (no device was attached) and the attended phone check in docs/VERIFY.md.
