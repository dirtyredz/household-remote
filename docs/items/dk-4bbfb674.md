---
id: dk-4bbfb674
type: bug
created: 2026-10-28
status: todo
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

Implementation (branch worktree-agent-a76c476eb748f7e25, commit 0352eb1d3c4cb8d0cd5d8bb781a3e8fef7964c5e): RemoteSession owns bounded foreground recovery through a TvTransport seam (15 s WebSocket ping; 1/2/4/8/15 s backoff, one attempt at a time, 60 s window, then "TV unavailable — check Wi-Fi or Wake"); typed ConnectionFailure keeps trust, pairing, denial and protocol failures terminal without removing credentials; standby suppresses recovery; background, selection and explicit reconnect cancel it. One fresh ordinary key may wait 5 s and is sent once. Failing before: ConnectionRecoveryTest at 49f0e020c170d897f25f93482599ca1548807c5e failed 2/2 ("No automatic Connected within 5 s"). Passing after at 0352eb1: the full VERIFY.md fast gate passed (60 JVM tests per flavor, zero failures); loopback graceful and abrupt drops recovered in about 1.05-1.08 s with the held key delivered exactly once; a frozen half-open peer was detected in about 0.41-0.43 s at a 200 ms test ping. State: implemented and verified offline; not yet confirmed on a phone. Close only after the VERIFY.md attended "Connection recovery" acceptance on a phone and TV.
