## 1. Goal

Replace the setup-heavy screen with a clear, comfortable remote: visible TV selection and connection status, one prominent power toggle, a proper D-pad, grouped audio/channel controls, and calibrated shortcuts. Power must reuse existing wake and standby behavior, expose progress and failure, and distinguish confirmed connectivity from inferred TV power state.

Benchmark findings:

| Reference | Layout and touch targets | Haptics and dark theme | Design takeaway |
|---|---|---|---|
| Samsung SmartThings | Explicit power, VOL/CH controls, directional/touchpad modes, and Back/Home. Published guidance does not specify target dimensions. | Documents remote vibration and selectable themes; exact dark colors are unspecified. | Adopt its recognizable control grouping and visible power action. [Samsung guide](https://www.samsung.com/hk_en/support/tv-audio-video/controlling-your-samsung-smart-tv-with-the-smartthings-app-mobile-remote/) |
| Apple TV Remote | A dominant navigation surface with separate supporting controls. Published guidance does not specify target dimensions. | The reviewed guide does not establish current haptic behavior or dark-theme settings. | Give navigation generous space and separate frequent actions clearly. [Apple guide](https://support.apple.com/guide/tv/iphone-ipad-apple-watch-a-remote-atvb701cadc1/tvos) |

These are documentation benchmarks, not measurements of installed apps. For this implementation, use explicit 56–64 dp primary targets, never below Android’s 48 dp minimum, consistent dark surfaces, and brief press feedback. [Android accessibility guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

## 2. Placement

Kotlin paths below are relative to `src/android/app/src/main/java/com/dirtyredz/householdremote/`.

| File | Responsibility and placement |
|---|---|
| **Changed** `ui/MainActivity.kt` | Activity startup, edge-to-edge configuration, and theme entry only. Extract `RemoteApp`, controls, and dialogs. |
| **New** `ui/RemoteApp.kt` | Lifecycle observation, ViewModel collection, screen/sheet selection, and callback wiring. Preserve START/STOP/disposal behavior. |
| **New** `ui/RemoteState.kt` | Extract the existing immutable screen state from the ViewModel; add independent connection and power fields. |
| **Changed** `ui/RemoteViewModel.kt` | Coordinate selection, existing setup actions, session events, and power collaborator. Extract wake orchestration; expose one `togglePower()` entry point. |
| **Changed** `ui/RemoteViewModelFactory.kt` | Construct/inject the power collaborator and adapt the typed session callback. Remain the production composition root. |
| **New** `power/PowerController.kt` | Own power intent, operation state, cancellation, and wake orchestration extracted from `RemoteViewModel.wake()`. Inject existing wake/probe/clock and narrow session/input callbacks. No Compose dependencies. |
| **Changed** `remote/KeyDispatcher.kt` | Add a one-shot standby path reporting actual `send` acceptance; retain ownership of serialized input and shortcut cancellation. |
| **New** `remote/ConnectionStatus.kt` | Small typed connection-event contract shared with the reliability work. |
| **Changed** `remote/RemoteSession.kt` | Emit typed status at existing transition sites only. Preserve connection, timeout, TLS, cancellation, and retry behavior. |
| **New** `ui/remote/RemoteScreen.kt` | Stateless arrangement of header, power, navigation, rockers, shortcuts, and feedback. |
| **New** `ui/remote/RemoteHeader.kt` | Selected-TV picker and overflow entry point. |
| **New** `ui/remote/ConnectionIndicator.kt` | Stable, accessible connected/reconnecting/offline slot. |
| **New** `ui/remote/PowerControl.kt` | Render power action, state, progress, and persistent failure feedback. |
| **New** `ui/remote/DirectionPad.kt` | Five distinct directional/OK targets. |
| **New** `ui/remote/RemoteRocker.kt` | Shared two-button rocker used for volume and channel. |
| **New** `ui/remote/NavigationActions.kt` | Back/Home/Source row and its existing key mappings. |
| **New** `ui/remote/ShortcutControls.kt` | Shortcut row, calibration entry, sequence progress, and cancellation. |
| **New** `ui/remote/RemoteButton.kt` | Shared target sizing, pressed treatment, semantics, and haptic behavior used by the controls. |
| **New** `ui/setup/TvManagementSheet.kt` | Extract TV list, discovery, manual IP, and import entry. |
| **New** `ui/setup/ImportManifestDialog.kt` | Extract local JSON import dialog. |
| **New** `ui/setup/PairingDialog.kt` | Extract explicit trust/re-pair confirmation. |
| **New** `ui/shortcuts/CalibrationDialog.kt` | Extract calibration editor; retain existing codec and watching-the-TV instructions. |
| **New** `ui/settings/RemoteSettingsSheet.kt` | Secondary reconnect, pairing, Exit, and update access. Reuse `ui/UpdateControl.kt` unchanged. |
| **New** `ui/theme/RemoteTheme.kt` | Material 3 dark color scheme, typography, and shapes. |

Additional paths:

- `src/android/app/src/main/res/values/strings.xml`: new visible labels, state/error text, and accessibility descriptions.
- `src/android/app/src/main/res/values/themes.xml` and existing `colors.xml`: dark native window background and system chrome.
- `src/android/app/src/main/AndroidManifest.xml`: replace the current platform light theme with the app’s dark window theme.
- `src/android/app/src/main/res/drawable/ic_remote_*.xml`: local vectors for power, directions, navigation, audio/channel, picker, and overflow. No icon-library dependency.
- `src/android/app/build.gradle.kts` and `src/android/gradle/libs.versions.toml`: authorized instrumentation-test configuration only.
- Test files and living-document changes are specified below.

Keep every code file below approximately 800 lines. The deliberate tradeoff is retaining inventory/setup coordination in the existing ViewModel while extracting power and presentation; a broader controller rewrite would overlap the separate reliability work.

## 3. Sequence

1. **Establish work tracking and the shared status seam.** Before implementation writes, follow `docket guide`, locate the applicable item using filtered `dk list --json`/`dk show --json`, and claim it in the implementation worktree. Coordinate the typed connection contract with the reliability implementer. Do not derive connection state from `RemoteState.message`: discovery, shortcuts, and calibration overwrite it.

2. **Extract the current UI without changing command behavior.** Move lifecycle wiring into `RemoteApp`; move setup and calibration dialogs to their responsibility modules. Change `MainActivity` to call the extracted route. `UpdateControl` moves behind settings through its caller; its implementation remains unchanged. Bind pairing/calibration dialogs to the selected TV identity and dismiss them when selection changes.

3. **Introduce typed connection presentation.** Map existing session transitions to Connected, Connecting, AwaitingApproval, and Offline; the indicator presents:
   - **Connected:** ready session, dot plus text.
   - **Reconnecting:** an actual connection attempt, small progress indicator plus text.
   - **Offline:** no usable session, outlined indicator plus text.
   
   Awaiting approval adds “Approve on TV” detail; no selected TV displays “Select a TV.” Emit offline on explicit disconnect as well as existing terminal paths. Update `RemoteViewModelFactory`, the ViewModel callback, and `SessionTest` callers. Do not introduce reconnect timers, retries, or additional probes.

4. **Implement the power operation independently of general messages and shortcut `busy`.** Capture the selected MAC and operation generation before starting; reject stale completions. Determine intent when the action is accepted:

   | State | Main control | Behavior |
   |---|---|---|
   | No selected TV | Disabled “Power” | Explain “Select a TV.” |
   | Ready session | “Turn off” · “On · connected” | Attempt one standby key. |
   | Unavailable/offline | “Turn on” · “Off or unreachable” | Execute existing wake flow. |
   | Waking | Disabled “Turning on…” | Show indeterminate progress and existing bounded wait. |
   | Wake probe succeeded, connecting | Disabled “Connecting…” | Finish through the existing session outcome/timeout. |
   | Sending standby | Disabled “Turning off…” | Await the single send attempt. |
   | Standby accepted | “Turn on” · “Standby sent” | Show “Power off not confirmed”; retain requested-off intent for this selection. |
   | Failure | Available action plus inline error | A fresh press re-evaluates current intent; nothing retries automatically. |

   “On · connected” is an inference from the usable session, not panel telemetry. Never translate an ordinary disconnect into confirmed “Off.”

5. **Add the standby dispatch seam.** `KeyDispatcher.tap()` currently reports queue acceptance, so it cannot drive successful power feedback. Add a dedicated standby operation that cancels pending ordinary input and any shortcut, then attempts exactly one `SamsungProtocol.key("KEY_POWER")` through the existing send callback. Return send acceptance/failure; do not retry.

   Do not immediately call `session.disconnect()` after acceptance: it cancels the socket and may discard the queued WebSocket frame. Acceptance means “Standby sent,” not acknowledged shutdown. Keep ordinary controls disabled under the requested-off intent until a deliberate wake/reconnect establishes a new usable session. Duplicate presses during an operation are inert; apply a short power-only duplicate-activation guard so a double tap cannot immediately reverse the action.

6. **Extract and connect wake orchestration.** Reuse `TvWake`, `awaitAwake`, the existing three-packet burst, MAC checking, and 30-second/1.5-second polling configuration. Disconnect before waking as today; reconnect only for the same selected TV while foregrounded. If readiness succeeds but connection fails, show “TV responded; remote connection failed,” with reconnect/pair access.

   Power takes priority over an active shortcut and cancels it. Selection, backgrounding, explicit reconnect/re-pair, and replacement setup operations cancel power work and clear transient state. Ordinary taps/shortcuts cannot start during power operations. General status callbacks must not erase power failures.

7. **Build the dark remote layout.** Use this vertical order:
   - TV name/picker and overflow, followed by the connection indicator.
   - Full-width power control, minimum 64 dp high, with power icon and explicit action text.
   - Centered 216–248 dp D-pad: four directions around a distinct 64 dp OK button.
   - Back, Home, Source row.
   - Volume rocker, central Mute button, channel rocker; label both rockers.
   - Netflix, Prime, Disney+, Plex shortcut row, followed by calibration/sequence feedback.

   Use 16 dp outer margins, 8–12 dp control gaps, charcoal background, lighter control surfaces, high-contrast text, and one restrained accent. Use color and text together for states. Keep header/status/power visible while the lower controls scroll when height or font scaling requires it; never shrink targets to fit. At 360 × 800 dp with normal text, fit the complete primary remote without scrolling. Let shortcuts wrap at large text sizes.

   Move discovery, import, manual IP, pairing, calibration editing, and updates out of the default remote surface. Preserve Exit as a secondary action. Remove the owner-edition parent-feature teaser; both flavors retain identical remote behavior.

8. **Apply interaction and accessibility behavior.** Give enabled activations one brief platform haptic, respecting system settings; provide pressed/ripple feedback immediately. Do not add long-press repeat or delays to ordinary keys. Use descriptive TalkBack actions, disabled semantics, accessible power state/progress, and polite announcements for status changes. Uncalibrated shortcuts remain visibly disabled with an adjacent calibration entry.

## 4. Tests

Paths below use the package directory `com/dirtyredz/householdremote/` under their stated source set.

| Test file | Required coverage |
|---|---|
| **New** `src/test/java/…/power/PowerControllerTest.kt` | Connected selects standby; unavailable selects wake; one wake invocation; timeout/error feedback; readiness followed by connection failure; duplicate activation; selection/background cancellation; stale completion cannot reconnect another TV; unrelated messages cannot clear errors. |
| **New** `src/test/java/…/remote/StandbyDispatchTest.kt` | Exactly one power frame; pending taps/shortcut canceled; send rejection reported; no retry; cancellation cannot replay old input. |
| **Changed** `src/test/java/…/SessionTest.kt` | Adapt typed callback callers and verify connection-state transitions using existing loopback fixtures. |
| **New** `src/androidTest/java/…/ui/remote/PowerControlTest.kt` | Every table state: labels, action/state semantics, progress, enabled state, error visibility, and callback count. Duplicate clicks while busy do nothing. |
| **New** `src/androidTest/java/…/ui/remote/RemoteScreenTest.kt` | Required controls and key mappings; connection states; unavailable input; disabled uncalibrated shortcuts; cancellation access; target bounds; compact width and large-text layout. |

Retain existing `CoreTest`, `PowerTest`, and `ShortcutCancellationTest` coverage.

Compose tests must mount pure composables with fake state/callbacks, never `RemoteApp`, the production ViewModel factory, or `ReleaseUpdater`. Automated verification must send no keys to real TVs.

New test dependencies require owner authorization: `androidx.compose.ui:ui-test-junit4` and `androidx.compose.ui:ui-test-manifest`, constrained by the existing Compose BOM, plus explicitly pinned AndroidX instrumentation runner/support dependencies. Configure a blank test host and `AndroidJUnitRunner`. Do not substitute JVM state tests for the requested Compose acceptance. [Compose testing guidance](https://developer.android.com/develop/ui/compose/testing)

## 5. Verification

No implementation, build, test, or phone verification was performed for this plan.

The implementer must run the **complete, unchanged fast-gate block in `docs/VERIFY.md`**, including its temporary-directory and isolated daemon-registry setup:

- Inspect other Android build processes first; retain in-process Kotlin compilation and avoid global daemon stops.
- Run the documented offline Gradle invocation covering `ktlintCheck`, both debug unit-test tasks, both release lint tasks, both flavors’ debug/release APK assemblies, and both release bundles.
- Run the documented Node test command covering household export/privacy, release contract/publication, and script-run tests.
- Run `node src/android/icons/generate-icons.mjs --check`.
- Run `powershell -NoProfile -File scripts/release/signing-smoke.ps1`.
- Run `dk check`.

After dependency authorization, separately run `connectedRemoteDebugAndroidTest` and `connectedOwnerDebugAndroidTest` on an available test device using the same isolated Gradle setup. This is additional acceptance, not part of the fast gate.

Complete an attended visible check on the owner’s phone:

- Power is immediately discoverable; text, status, and primary controls are readable without setup clutter.
- Standby visibly turns off the selected TV; one subsequent power activation wakes it.
- Wake progress, unavailable Wi-Fi, timeout, and connection/pairing failure are understandable.
- Switching TVs or backgrounding during wake never affects the previous TV afterward.
- Navigation, rockers, mute, haptics, calibrated shortcuts, and cancellation behave correctly.
- Dark startup/system chrome, TalkBack, large text, and short-screen scrolling remain usable.

Record phone/model/build evidence and unresolved device behavior in the work item; release the claim when implementation work finishes.

## 6. Docs

- **`STRUCTURE.md`**: extend `## Layout` bullets with `ui/remote`, `ui/setup`, `ui/shortcuts`, `ui/settings`, `ui/theme`, power-operation ownership, and `src/android/app/src/androidTest/`.
- **`docs/ARCHITECTURE.md`**: document stateless controls, lifecycle route, power coordinator, and typed connection presentation boundary.
- **`docs/DECISIONS.md`**: record inferred power state, one-shot standby acceptance versus physical confirmation, power priority over shortcuts, and any authorized test artifacts.
- **`docs/FEATURES.md`**: describe the visible power toggle, dark remote, connection indicator, and actual phone acceptance status.
- **`docs/GOTCHAS.md`**: explain that offline does not prove off, standby lacks acknowledgment, and wake still depends on TV/Wi-Fi settings.
- **`docs/VERIFY.md`**: retain the fast gate verbatim; add separate fake Compose instrumentation and attended phone acceptance instructions.

## 7. Open questions / assumptions

- **Test dependency authorization remains required.** The existing BOM does not authorize undeclared Compose test artifacts. Proceed with production restructuring and existing-dependency unit tests; obtain authorization for the concrete test dependency set before adding/resolving it.
- **Connection contract ownership:** assume the parallel reliability work can supply or adopt the small typed event seam. Its scheduling and recovery decisions remain outside this change.
- **Power certainty:** assume the owner accepts “Off or unreachable” and “Standby sent · power off not confirmed.” Existing transport behavior cannot truthfully provide an authoritative binary panel state.
- **Interaction defaults:** assume power cancels an active shortcut, and ordinary controls remain single-tap actions without hold-to-repeat.
- **Phone availability:** the brief requires a visible phone check, while `GOTCHAS.md` says no phone is attached during implementation. Treat device access as an acceptance prerequisite; do not mark physical acceptance complete without it.