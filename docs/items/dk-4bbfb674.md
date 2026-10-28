---
id: dk-4bbfb674
type: bug
created: 2026-10-28
status: wip
since: 2026-10-28
area: remote
priority: P1
rank: z
parent:
fixes: []
blocked_by: []
relates: []
---
# Remote connection drops require manual recovery

Owner reports repeated connection loss on a real phone and Samsung TV; the initiating trigger remains unknown. Diagnosis at baseline 24332d81ac1ec16a5a72cdfc42c70fa7bf449b06 found that RemoteSession finishes and cancels input after socket close/failure without scheduling recovery. The UI disables remote keys, and tap does not reconnect. The TV client has no configured heartbeat. Foreground start reconnects; background stop deliberately disconnects. AwakeClock only times Wake polling.

Loopback WSS experiments reproduce graceful and abrupt drops leaving input unavailable and no automatic reconnect during a 1.5-second observation. Detection took 100 ms and 1 ms respectively in the corrected run. Manual reconnect reuses the saved token and certificate and delivers a new key without removing credentials. This proves a recovery gap, not the owner's original physical disconnect cause. The corrected targeted run passed all 11 tests, including existing certificate, token, Wake and shortcut checks. The first run's abrupt-drop harness failed internally in MockWebServer; a local TCP fault proxy corrected that experiment. No real TV was contacted.

Implement a single foreground-owned recovery state machine in the existing session, typed transport versus trust failures, a protocol ping/pong heartbeat, bounded backoff, lifecycle/selection cancellation and honest Reconnecting/Unavailable UI. Preserve certificate pinning, attended pairing, manual Wake, and no replay of already-submitted keys or shortcuts. A fresh unsent ordinary key may wait briefly for recovery; standby must never be replayed. Prove graceful/abrupt recovery and silent-peer detection with local fake peers, and verify no retry or pending input survives background/selection change.

Detailed diagnosis, proposed timing defaults, placement, tests and device acceptance requirements are retained in .tmp/connection-diagnosis.md in the diag/connection-drops worktree. The bug remains open for a separate implementation; this diagnosis changes no production code.
