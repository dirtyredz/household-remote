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

Owner v0.2.1 phone feedback (2026-10-28): remove the Apps heading and give its height to the D-pad; put the TV picker, short non-wrapping connection status, power button, and overflow on one header line. The notice slot is removed entirely. The 56 dp red power button always shows its static white power icon, while its enabled alpha and TalkBack action/state may change. The dot plus short header label is the only state text; "No Wi-Fi" opens Wi-Fi settings. Transient key/app feedback and power errors are not displayed. The connection and power state machines are unchanged.

Implementation on feat/header-inline passes the exact VERIFY.md fast gate with ANDROID_HOME set to the installed SDK. JVM presentation and dispatcher tests pass. Compose tests cover the static power icon, absent spinner/notice slot, tappable Wi-Fi status, one-line 360 dp header, no scrolling, and fit at the documented phone sizes; connected Compose tests were not run without an attached device.

Remaining acceptance is an attended phone check of the revised header and fit, static power icon through standby/wake/reconnect, real connection states and Wi-Fi shortcut, and physical controls.

Acceptance: VERIFY.md fast gate passes; JVM tests cover power/status logic; Compose tests compile; attended phone check of power discoverability, standby/wake, and status indicator.

## Notes

### 2026-10-28T17:56:53.374Z · resolved · agent

Implemented on branch worktree-agent-a81ad857489e8c3b2 at 2c36b35; fast gate green. Not yet done: connected Compose tests (no device was attached) and the attended phone check in docs/VERIFY.md.
