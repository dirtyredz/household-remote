# Structure

## Layout

```text
.
|-- config/                 fictional public example
|-- docs/                   living maps, verification, and Docket items
|-- scripts/                owner and attended release entry points
|-- src/
|   |-- android/            app build, resources, release engines, and icon source
|   `-- household/          local manifest export and privacy guard
`-- tools/                  shared script runner and tests
```

- `src/android/`: independent Kotlin/Compose Gradle build; `app/src/main/res/` owns Android resources and `gradle/` owns the pinned catalog and wrapper.
- `src/android/app/src/main/java/com/dirtyredz/householdremote/`: `inventory` owns JSON inventory and repository merging; `discovery` owns SSDP/REST probes; `security` owns encrypted credentials, digests, and TV certificate trust; `remote` owns the foreground session and its bounded recovery state machine (`RemoteSession`, timing in `SessionTiming`), the `TvTransport` socket seam with its OkHttp/heartbeat implementation, serialized input (key taps, websocket app-launch fallbacks, and a batched text-plus-Enter queue entry) with the one held recovery key, the Samsung protocol frames (keys, `SamsungProtocol.text`, and `ed.apps.launch`), current-session IME event forwarding, the one-shot standby send, and the typed `ConnectionStatus`/`ConnectionFailure` contract; `power` owns WOL, identity-checked awake polling, and `PowerController` (power intent, operation state, cancellation, wake orchestration; no Compose); `apps` owns the four app buttons' Samsung app IDs (`TvApp`), the read-only private-LAN install probe (`AppInstallClient`), the state-changing REST launcher (`RestAppLauncher`), and `AppController` (install checks, REST-to-websocket launch fallback, cancellation and stale-session guards; no Compose), plus the one-time removal of v0.2.0 calibration data; `network` owns the phone's Wi-Fi availability (`WifiMonitor`/`AndroidWifiMonitor` over the pure `WifiNetworks` bookkeeping) and the Wi-Fi settings shortcut; `selfupdate` separates release feeds, verified downloads, package inspection, and installation; `persistence` owns shared atomic file writes; `ui` owns the activity, the `RemoteApp` lifecycle route, `RemoteState`, and the ViewModel plus its factory (the composition root).
- `src/android/app/src/main/java/com/dirtyredz/householdremote/ui/remote/`: stateless remote composables (one-line TV/status header, dedicated power row, D-pad, rockers, navigation row, app buttons, shared `RemoteButton`, decorative `HeartShape` with the original circular OK touch target, and one-line `FitText`) and the pure `RemotePresentation` mapping (power and connection labels). `RemoteScreen` owns the no-scroll layout rule (`compactRemote`).
- `src/android/app/src/main/java/com/dirtyredz/householdremote/ui/setup/`: TV management sheet, JSON import, pairing, and phone keyboard dialogs. `TvKeyboardDialog` owns the transient draft plus automatic focus/soft keyboard without importing remote controls; the manual Keyboard button stays with its navigation row in `ui/remote/NavigationActions.kt`.
- `src/android/app/src/main/java/com/dirtyredz/householdremote/ui/settings/`: overflow quick-actions sheet, full-screen settings surface, and the manual update control.
- `src/android/app/src/main/java/com/dirtyredz/householdremote/ui/theme/`: Material 3 black/hot-pink theme; RemoteTokens owns control surfaces, outlines, disabled colours and the D-pad-centred radial background brush. RemotePalette.kt is generated from icons/palette.json.
- `src/android/app/src/test/`: offline JVM and fake-server tests.
- `src/android/app/src/androidTest/`: Compose UI instrumentation tests that mount pure composables with fake state; never the ViewModel, updater or a TV.
- `src/android/icons/`: heart/arrows source SVG, single palette.json colour source, and deterministic generator/check for adaptive/monochrome vectors, Android colour resources and the Compose palette.
- `src/android/releases/`: `.mjs` engines for unsigned staging, provenance, signing, manifest construction, and draft publication.
- `src/household/`: explicit manifest export, private-data guard and offline tests. Real input is ignored `config/household.local.json`; exports stay under `.tmp/`.
- `scripts/release/`: attended `.ps1` entry points for provisioning, signing/publication, and disposable smoke; the implementation engines live in `src/android/releases/`.
- `scripts/owner/owner-run.ps1`: verbatim owner-run 1.0.0 template.
- `tools/script-run/`: verbatim script-run template and tests from ~/.claude/templates/scripts.
- `.github/workflows/`: unsigned Android CI and draft release jobs.
- `config/`: fictional inventory example only; no household data shipped.
- `docs/`: living maps, verification, distribution, original plan, Docket items.

Place code with its owning responsibility; keep files below approximately 800 lines.
