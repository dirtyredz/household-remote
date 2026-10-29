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
- `src/android/app/src/main/java/com/dirtyredz/householdremote/`: `inventory` owns JSON inventory and repository merging; `discovery` owns SSDP/REST probes; `security` owns encrypted credentials, digests, and TV certificate trust; `remote` owns the foreground session and its bounded recovery state machine (`RemoteSession`, timing in `SessionTiming`), the `TvTransport` socket seam with its OkHttp/heartbeat implementation, serialized key input with the one held recovery key, the Samsung protocol frames (keys and the `ed.apps.launch` fallback), the one-shot standby send, and the typed `ConnectionStatus`/`ConnectionFailure` contract; `power` owns WOL, identity-checked awake polling, and `PowerController` (power intent, operation state, cancellation, wake orchestration; no Compose); `apps` owns the four app buttons' Samsung app IDs (`TvApp`), the private-LAN install probe and primary REST launcher (`AppInstallClient`), and the one-time removal of v0.2.0 calibration data; `network` owns the phone's Wi-Fi availability (`WifiMonitor`/`AndroidWifiMonitor` over the pure `WifiNetworks` bookkeeping) and the Wi-Fi settings shortcut; `selfupdate` separates release feeds, verified downloads, package inspection, and installation; `persistence` owns shared atomic file writes; `ui` owns the activity, the `RemoteApp` lifecycle route, `RemoteState`, and the ViewModel plus its factory (the composition root).
- `src/android/app/src/main/java/com/dirtyredz/householdremote/ui/remote/`: stateless remote composables (header, centred connection indicator, round power key, notice slot, D-pad, rockers, navigation row, app buttons, shared `RemoteButton` and one-line `FitText`) and the pure `RemotePresentation` mapping (power, connection label, notice priority). `RemoteScreen` owns the no-scroll layout rule (`compactRemote`).
- `src/android/app/src/main/java/com/dirtyredz/householdremote/ui/setup/`: TV management sheet, JSON import and pairing dialogs.
- `src/android/app/src/main/java/com/dirtyredz/householdremote/ui/settings/`: overflow quick-actions sheet, full-screen settings surface, and the manual update control.
- `src/android/app/src/main/java/com/dirtyredz/householdremote/ui/theme/`: Material 3 dark theme.
- `src/android/app/src/test/`: offline JVM and fake-server tests.
- `src/android/app/src/androidTest/`: Compose UI instrumentation tests that mount pure composables with fake state; never the ViewModel, updater or a TV.
- `src/android/icons/`: source SVG and deterministic Android vector generator/check.
- `src/android/releases/`: `.mjs` engines for unsigned staging, provenance, signing, manifest construction, and draft publication.
- `src/household/`: explicit manifest export, private-data guard and offline tests. Real input is ignored `config/household.local.json`; exports stay under `.tmp/`.
- `scripts/release/`: attended `.ps1` entry points for provisioning, signing/publication, and disposable smoke; the implementation engines live in `src/android/releases/`.
- `scripts/owner/owner-run.ps1`: verbatim owner-run 1.0.0 template.
- `tools/script-run/`: verbatim script-run template and tests from ~/.claude/templates/scripts.
- `.github/workflows/`: unsigned Android CI and draft release jobs.
- `config/`: fictional inventory example only; no household data shipped.
- `docs/`: living maps, verification, distribution, original plan, Docket items.

Place code with its owning responsibility; keep files below approximately 800 lines.
