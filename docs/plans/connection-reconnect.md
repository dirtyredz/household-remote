# Remote connection diagnosis and fix specification

Repository baseline: `24332d81ac1ec16a5a72cdfc42c70fa7bf449b06`. Worktree: `C:/Users/dirty/projects/.worktrees/household-remote/diag-connection`. Date: 2026-10-28. Tracking item: `dk-4bbfb674`. Diagnosis only; no production changes, commits, real-TV traffic, or dependency downloads.

## 1. Symptom

Owner reports repeated connection loss on a real phone and Samsung TV, with unknown trigger. The physical disconnect frequency and initiating cause cannot be measured without device evidence. The offline experiments below reproduce the app's recovery behavior after a controlled disconnect, not the original environmental trigger.

At the unmodified production baseline, a loopback TLS fake TV produced these measurements:

| Experiment | Detection | Automatic reconnects in next 1.5 s | Next tap accepted | Manual reconnect with saved credentials | New key delivered after manual reconnect |
| --- | ---: | ---: | --- | --- | ---: |
| Graceful WebSocket close | 100 ms | 0 | No | Yes | 1 |
| Abrupt TCP cut via loopback proxy | 1 ms | 0 | No | Yes | 1 |

Both experiments preserved the saved token and certificate, recorded **zero credential removals**, and observed **zero replayed keys**. The TV-specific client's measured `pingIntervalMillis` was **0**, and `retryOnConnectionFailure` was false. An earlier successful graceful run detected closure in 393 ms with the same recovery outcome; these are individual local timings, not phone latency estimates. The corrected targeted suite passed **11/11 tests**.

## 2. Root cause

### Ranked findings

1. **Missing recovery after a detected drop — high confidence in the code mechanism.** `remote/RemoteSession.kt:101` and `:116` route socket failure and completed close to `finish`; `:132` calls `disconnect` and emits a false readiness flag. `disconnect` at `:136` cancels input, clears readiness, cancels the socket and shuts down the client. No retry is scheduled and no selected endpoint is retained for recovery. `remote/KeyDispatcher.kt:38` rejects taps once its channel is cancelled. `ui/RemoteViewModel.kt:178` only reports unavailable input; it never connects. `ui/MainActivity.kt:126` disables remote keys when readiness is false. Consequently even a brief interruption leaves the foreground remote unusable until an explicit Reconnect, reselection, Wake success, or foreground transition. Experiment results below distinguish this from broken credential reuse.

2. **No client heartbeat — confirmed configuration; causal contribution to the owner's drops unproven.** `security/TvTrust.kt:31` builds the isolated OkHttp client without a ping interval. The session cancels its approval timer when ready (`remote/RemoteSession.kt:85`); no established-session watchdog replaces it. An explicit close or I/O error can be detected, but the application has no bounded detection time for a quiet, half-open connection. `send` at `:131` reports local WebSocket enqueue acceptance, not TV execution. `retryOnConnectionFailure(false)` is not the main cause: toggling an HTTP retry option would not implement an established WebSocket recovery state machine.

3. **Lifecycle teardown is deliberate, not evidence of lost pairing.** `ui/MainActivity.kt:57` forwards ON_START/ON_STOP and disposal. `ui/RemoteViewModel.kt:90` reconnects the selected TV on start using saved credentials; `:96` cancels operations/input/socket on stop. A failed foreground reconnect then falls into finding 1. `:125` does the same disconnect/connect sequence on selection changes. `start()` is not idempotent; the observer registration plus explicit already-started check (`MainActivity.kt:69`) merits a duplicate-start regression test, but no device run here establishes it as the reported trigger.

4. **Failure messages conflate transient transport problems and trust problems.** `RemoteSession.kt:110` emits the same certificate/re-pair hint for all `onFailure` exceptions. Explicit pairing alone removes credentials (`:48`). A normal disconnect does not remove them. A saved-token reconnect also inherits the 90-second approval timer (`:32`, `:59`), so an unresponsive peer can be labelled an approval timeout even though pairing was not requested. Certificate rejection itself is intentional and must remain fail-closed (`security/TvTrust.kt:19`).

5. **Standby, network path loss, IP drift, and Doze remain environmental candidates, not demonstrated causes.** There is no connectivity callback or foreground recovery loop. Selection restores the stored IP; discovery/import can refresh inventory but does not automatically migrate an active session. Wake is manual (`RemoteViewModel.kt:215`): it sends WOL, polls MAC identity, and only then reconnects. `power/WakeOnLan.kt:32` defines `AwakeClock` as a clock for that polling loop, not a wake lock or connection heartbeat. No foreground service or wake-lock permission is declared in the manifest. A process death loses in-memory connections/jobs; the next model loads selection and encrypted no-backup credentials (`inventory/TvRepository.kt:18`, `security/TokenStore.kt:27`) and start reconnects. Android device/Doze and Keystore restoration behavior were not exercised here.

All source paths in this section are relative to `src/android/app/src/main/java/com/dirtyredz/householdremote/`, except the manifest at `src/android/app/src/main/AndroidManifest.xml`.

## 3. What was run

Read the required structure/architecture/gotchas/decisions/verification documents, then the session, dispatcher, protocol, lifecycle UI, trust, credential storage, inventory, and Wake code. Used `rg` to locate lifecycle, ping, network callback and wake-lock hooks. Inspected `git status --short` and `git rev-parse HEAD`. No supplied good build was compared.

Inspected `Get-CimInstance Win32_Process` before Gradle. An unrelated build initially existed; waited until it exited and a fresh Java process inventory was empty. Never stopped that build. Used the installed Microsoft JDK 21 and Android SDK, `.tmp/test-temp` for TEMP/TMP, and the worktree-specific daemon registry required by VERIFY.md. In-process Kotlin compilation is already set in `src/android/gradle.properties`.

Executed this targeted subset of the documented Gradle gate, twice (the second run corrected only the temporary test harness):

```powershell
$env:JAVA_HOME = 'C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot'
$env:ANDROID_HOME = 'C:/Users/dirty/AppData/Local/Android/Sdk'
$env:TEMP = (Resolve-Path .tmp/test-temp).Path
$env:TMP = $env:TEMP
$daemonRegistry = Join-Path (Resolve-Path .tmp).Path 'gradle-daemon'
.\src\android\gradlew.bat -p src/android --offline --no-daemon "-Dorg.gradle.daemon.registry.base=$daemonRegistry" testRemoteDebugUnitTest --tests '*ConnectionDiagnosisTest' --tests '*SessionTest' --tests '*PowerTest' --tests '*ShortcutCancellationTest'
```

First run: **11 tests, 10 passed, one harness failure**, 7m 2s Gradle elapsed. The abrupt-drop experiment invoked `cancel()` on MockWebServer's server-side WebSocket, which threw inside `okhttp3.internal.ws.RealWebSocket.cancel`; this is not an app failure. Graceful-drop, zero-ping configuration, and all eight existing tests passed. Preserved the first result in `.tmp/first-diagnosis-result.xml` and log in `.tmp/connection-tests.log`.

The corrected experiment severs both sides of a local TCP forwarding connection instead. TLS still terminates in the local fake TV, with the real application certificate-pin logic. All network test destinations are loopback. A single-thread coroutine dispatcher serializes session and input calls as the app's main scope would. Tests explicitly clean up sessions, servers, proxy sockets and executors.

Second run: **11 tests passed, zero failures**, 3m 56s Gradle elapsed. Suite breakdown: ConnectionDiagnosisTest 3, SessionTest 5, PowerTest 2, ShortcutCancellationTest 1. The drop tests used the normal saved-credential path from their first connection; manual reconnect sent the same saved token, accepted the same certificate and delivered exactly one fresh key. This rules out mandatory re-pairing or inherent saved-credential reconnect failure in these controlled drops. It does not rule out actual device token revocation or certificate changes. Log: `.tmp/connection-tests-rerun.log`; XML evidence: `src/android/app/build/test-results/testRemoteDebugUnitTest/`. Temporary harness: `src/android/app/src/test/java/com/dirtyredz/householdremote/ConnectionDiagnosisTest.kt`.

During slow Gradle startup, ran installed `jcmd.exe 35492 Thread.print`; it showed SDK/class loading rather than a test hang. The documented SDK XML compatibility warning appeared. No tool or dependency download was attempted. The full release/lint/package/signing gate and owner flavor were not run: this was a focused diagnosis with no production changes.

Cleanup: ran `.\src\android\gradlew.bat -p src/android "-Dorg.gradle.daemon.registry.base=$daemonRegistry" --stop`; it returned **“No Gradle daemons are running.”** Final Java inventory contained only an unrelated build in another repository, which was left untouched. Both of this diagnosis's Gradle commands and their test processes exited. `git diff --exit-code` was clean for existing tracked files. `dk check` passed with zero errors/warnings; recorded the evidence in `dk-4bbfb674`, returned it to todo for implementation and released the claim. No commits or pushes.

No worktree was created by this diagnosis. The supplied `diag-connection` worktree is retained with the report, ignored logs/results and untracked temporary diagnosis test plus Docket item for handoff. The temporary test asserts current broken recovery behavior; it is evidence, not a proposed permanent regression test. The fix developer should replace its expectations with recovery assertions. Total session work was about **21 minutes**, within the 45-minute budget; no device or silent-half-open experiment was performed.

## 4. Fix spec

### Goal

Recover transient disconnects while the selected remote is foregrounded, using the saved certificate and token. Show an honest connection state. Preserve explicit attended pairing and the existing rule that previously submitted input and shortcut sequences are never replayed. Do not attempt to keep the app connected in the background or automatically wake a TV.

### Placement

- Extend the existing `remote/RemoteSession.kt` as the single socket/retry owner. Separate terminal owner disconnect from transient attempt teardown; the current `disconnect()` cannot serve both because it invalidates the entire generation. Add a small `remote/ConnectionState.kt` for typed state/failure/retry policy. No second session manager or alternate transport.
- Keep TLS pin checking in `security/TvTrust.kt`; add typed pin/first-trust failures and a configurable WebSocket ping interval to this isolated client only. Do not alter discovery or update TLS.
- Keep input serialization and bounded new-input waiting in `remote/KeyDispatcher.kt`. Update `ui/RemoteViewModel.kt` and `ui/RemoteViewModelFactory.kt` to provide timing/transport seams and translate typed connection state. Keep UI rendering in `ui/MainActivity.kt`.
- `SamsungProtocol.kt` should retain wire encoding and the pairing approval duration; do not invent Samsung key-based keepalives. `WakeOnLan.kt` needs no heartbeat responsibility and no keep-awake mechanism. Keep each file below roughly 800 lines.

### Sequence

1. Add regression tests before changing behavior. Inject attempt creation, timing and retry scheduling into the existing session, with production defaults. Tests should use a serialized coroutine context; all socket callbacks must re-enter that context, including close handling.
2. Model `Idle`, `Connecting`, `Connected`, `Reconnecting(attempt)`, `Unavailable`, `Pairing`, and terminal `PairingRequired`, `TrustChanged`, `CredentialsUnavailable`/`ProtocolError` states. Carry typed failure reasons rather than parsing status strings. Expose readiness as derived state. Clear it immediately on closing, failure, or rejected send; the dispatcher status path currently cannot do that.
3. Retain the foreground selected target and use separate owner-generation and attempt identifiers. Every callback, retry, timeout and pending key checks both relevant identities. `stop`, selection replacement, explicit pairing and clear cancel retry/timeout/input before retiring the socket. Make repeated foreground start for the same target idempotent. Repeated connect events on one established socket must not restart the dispatcher or cancel a shortcut.
4. Configure protocol WebSocket ping/pong at **15 seconds** initially. Give saved-credential connection/authentication an overall **10-second** attempt deadline; keep explicit pairing at **90 seconds**. Test the pinned OkHttp implementation's no-pong behavior. Do not add application key messages as heartbeats. A ping interval is a failure-detection and compatibility choice, not proof that Samsung will retain idle sessions.
5. On recoverable close/I/O failure, cancel existing input and retry after **1, 2, 4, 8, then 15 seconds**, capped at 15 seconds, within a **60-second foreground recovery window**. Permit only one attempt. Re-read credentials each attempt so token refresh persists. Reset the failure streak only after 30 seconds of stable connection to avoid a rapid connect/drop loop. At the window deadline cancel any active attempt and expose Unavailable with Reconnect and Wake. A fresh ordinary tap or explicit Reconnect can start a fresh bounded window; repeated taps must not create attempts or extend a window indefinitely. Stop retrying on denied authorization, changed pin, unavailable credentials, or invalid protocol; never silently fall back to first trust. No background retries and no automatic WOL.
6. UI displays **“Reconnecting…”**, then **“TV unavailable — check Wi-Fi or Wake”** if exhausted. Routine transport messages must not mention re-pairing. Retain a distinct attended re-pair action for actual trust/authorization cases. Standby and shortcuts remain disabled until Connected. Ordinary navigation/volume controls may submit one fresh pending key while recovery is underway: expire it after **5 seconds**, reject additional pending taps visibly, bind it to selected TV/generation, and dispatch it once after readiness. Cancel it on lifecycle/selection/trust failure. A key already accepted by the WebSocket, an in-flight shortcut, and KEY_POWER are never resubmitted. If an attempted send returns false, discard that key and enter recovery; do not retry an ambiguous submission.
7. Preserve manual Wake's identity-checked polling and cancel any retry before Wake starts. On successful Wake, begin normal saved-credential connection. Read the current selected endpoint when explicitly starting/restarting recovery; do not infer trust from unauthenticated REST identity. Broad automatic rediscovery is outside this fix.

**Tradeoffs:** 15-second pings add a small foreground traffic cost and require Samsung compatibility acceptance; bounded retry prevents continuous traffic to a sleeping TV. Waiting for one fresh unsent key improves recovery usability but intentionally drops extra/stale input. The no-replay rule means a key submitted during an undetected half-open interval cannot be guaranteed to execute: the Samsung protocol used here has no per-key execution acknowledgment. Promise transparent success only for a fresh key held before submission when recovery finishes within its deadline.

### Tests (reproduction first)

1. Convert the temporary graceful/abrupt-drop tests to expect a second authenticated connection without a manual call. Send one fresh key after the detected drop; with server recovery inside five seconds, receive exactly one key and zero credential removals. Assert the old queue/shortcut never appears on the new socket.
2. Use a loopback fault proxy or controlled transport to suppress pongs without closing TCP. Verify Connected clears within the heartbeat bound. Test responsive idle sessions for at least three intervals. Check ping control frames rather than remote-control messages.
3. Virtual-time tests: exact retry schedule, one attempt maximum, deadline enforcement, stable-connection reset, no retry after stop/dispose/selection/process recreation, cancellation during TLS/auth wait, and stale callbacks after replacement. Repeated start and duplicate connect events must not reset live input.
4. New pending tap: expires at five seconds, additional taps bounded, selection/background cancels it, no standby/shortcut replay, send-false clears readiness and does not resend. Distinguish enqueue acceptance from delivered-to-fake-peer evidence.
5. Keep existing tests for token refresh, changed-certificate real TLS rejection, explicit first trust, denial, approval timeout, shortcut ownership, and MAC-checked Wake. Add transient I/O failure versus typed trust failure assertions; neither removes saved credentials automatically.
6. Run both flavor JVM suites and the documented fast gate offline with installed pinned dependencies. Device acceptance must be attended separately; this diagnosis sends no real-TV traffic.

### Verification

- Graceful and abrupt drop: **0 automatic reconnects today -> one successful automatic reconnect**, within **5 seconds** when the loopback peer is immediately available; fresh held key delivered **exactly once**, no manual reconnect or pairing.
- Silent peer: no current application heartbeat bound -> detection within **35 seconds** with a 15-second ping interval (two intervals plus scheduler tolerance), under an active foreground test scheduler. This is not an Android background timing guarantee.
- Offline TV: stop automatic recovery within **60 seconds**; no overlapping attempts; no new retry after foreground exit or TV replacement. Trust change: zero automatic re-pair and unchanged saved pin/token.
- Later owner acceptance: record phone/app/TV version and timestamps for foreground idle, screen-off/resume, network interruption, selection changes and standby/Wake. Measure detection/recovery independently. Verify responsive idle connections for at least 15 minutes, and never classify expected standby as lost pairing.

### Docs

Update `docs/ARCHITECTURE.md` with typed foreground recovery and the distinction between fresh unsent pending input and replay. Update `docs/GOTCHAS.md` with unavailable versus trust-changed behavior and the bounded recovery window. Add fake-peer commands and device acceptance cases to `docs/VERIFY.md`. Preserve the certificate ADR in `docs/DECISIONS.md`; document the no-background/no-auto-wake policy and retry defaults. Update the Docket bug with measured verification before closing it.

## 5. Open questions / assumptions

- Which phone/Android version, TV model/firmware, and app revision produced the report? What exact status appears? Does Reconnect work, or is attended re-pair actually necessary?
- Does the drop occur while the UI remains visible, only after screen-off/resume, after changing TVs, or after standby? No real-device trigger is established here.
- The five-second fresh-key window, 15-second ping, 10-second saved-token attempt, and 60-second recovery budget are proposed defaults, not measured Samsung requirements. Confirm ping compatibility and usability during owner acceptance.
- There is no supplied known-good build and no proven regression interval; no bisect or good/bad performance comparison was justified.
