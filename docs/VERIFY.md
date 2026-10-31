# Fast gate

From the repository root, with JDK 17/21 and Android SDK 36 already installed. Set `ANDROID_HOME` to that installed SDK for the signing smoke (Gradle also accepts ignored `local.properties`):

```powershell
New-Item -ItemType Directory -Force .tmp/test-temp | Out-Null
$env:TEMP = (Resolve-Path .tmp/test-temp).Path
$env:TMP = $env:TEMP
$daemonRegistry = Join-Path (Resolve-Path .tmp).Path 'gradle-daemon'
.\src\android\gradlew.bat -p src/android --offline --no-daemon "-Dorg.gradle.daemon.registry.base=$daemonRegistry" ktlintCheck testRemoteDebugUnitTest testOwnerDebugUnitTest lintRemoteRelease lintOwnerRelease assembleRemoteDebug assembleOwnerDebug assembleRemoteRelease assembleOwnerRelease bundleRemoteRelease bundleOwnerRelease
node --test src/household/export.test.mjs src/household/privacy.test.mjs src/android/releases/contract.test.mjs src/android/releases/publication.test.mjs tools/script-run/script-run.test.mjs
node src/android/icons/generate-icons.mjs --check
powershell -NoProfile -File scripts/release/signing-smoke.ps1
dk check
```

Use `ktlintFormat` only to format source before the gate. If cached pinned Maven dependencies are missing, rerun without `--offline` only for the owner-authorized Google/Maven Central/plugin portal resolution. Do not download other tools. Set ANDROID_HOME or git-ignored local.properties for an installed SDK. No live TV keys, full tiers, emulator or installed smoke in this gate.

Before a Gradle build inspect `Get-CimInstance Win32_Process` command lines for other Android builds. Use no-daemon and in-process Kotlin compilation; stop only processes started by this work.

Connection recovery is covered inside `testRemoteDebugUnitTest`/`testOwnerDebugUnitTest` by loopback fake peers only (never a TV): `ConnectionRecoveryTest` (real TLS + pin through `FaultProxy`: graceful close, abrupt cut, frozen half-open peer for the heartbeat), `RecoveryPolicyTest` (virtual-time backoff, window, standby/background/selection cancellation) and `PendingKeyTest`. To iterate on just those, add `--tests '*ConnectionRecoveryTest' --tests '*RecoveryPolicyTest' --tests '*PendingKeyTest' --tests '*SessionTest'` to a `testRemoteDebugUnitTest` run.

# Compose UI instrumentation (separate from the fast gate)

Only when a device or emulator is already attached (`adb devices`), with the same temp/daemon-registry setup as above:

```powershell
.\src\android\gradlew.bat -p src/android --offline --no-daemon "-Dorg.gradle.daemon.registry.base=$daemonRegistry" connectedRemoteDebugAndroidTest connectedOwnerDebugAndroidTest
```

These mount pure composables (`ui/remote`) with fake state and recording callbacks; they never start `RemoteApp`, the ViewModel factory, `ReleaseUpdater`, or contact a TV. Without a device, compile them with `compileRemoteDebugAndroidTestKotlin` and report the connected run as not run.

# Attended phone acceptance (owner, while watching the TV)

Record phone, model and build SHA in the Docket item.

- Power is immediately discoverable; the TV name and short connection status share one header line without overlap or status wrapping (a long name ellipsizes first); primary controls are readable without setup clutter; dark startup and system bars.
- With the TV on and "Connected": "Turn off" puts the TV in standby and keeps ordinary keys blocked; the power icon stays unchanged while TalkBack describes its action/state. The next press ("Turn on") wakes it and reconnects.
- During wake, standby and reconnect the power key never moves or swaps to a spinner. The header status is the only state text; no power error, key feedback or app-launch notice appears.
- Switching TVs or backgrounding during "Turning on…" never affects the previous TV afterwards.
- D-pad, Back/Home/Source, volume, mute, channel and haptics behave correctly.
- App buttons (dk-254f8636), on each household TV: the four buttons stay on one row with no "Apps" heading; from Home and from inside another app, Netflix, Prime, Disney+ and Plex each open their app with one press and no transient launch text. There is no Calibrate link and no Cancel-sequence button. A button is greyed out only if that TV lacks the app. While the status reads "Reconnecting…" the app buttons are disabled and nothing opens later on its own. First launch after upgrading from v0.2.0 does not crash.
- Connection recovery (dk-4bbfb674; record times): leave the remote open and idle for 15 minutes, then press a key; it works without Reconnect. Toggle the phone's Wi-Fi off and on with the remote open (dk-28d77a2d): while off, the inline status reads "No Wi-Fi"; tapping it opens the system Wi-Fi panel/settings, keys and power are disabled, and nothing retries even after a minute. When Wi-Fi returns it shows "Reconnecting…", then "Connected" without any tap. With recovery exhausted, the header reads "Unavailable" with no long explanation. A single D-pad press made while it reads "Reconnecting…" moves the TV once and never produces feedback text. "Turn off" leaves the app Offline and it never reconnects on its own. Screen off/on and switching TVs reconnect only the selected TV, and nothing re-pairs.
- No vertical scrolling at any height (owner phone feedback, 2026-10-28): the whole remote fits on the phone at default and at 1.3 font scale; the D-pad takes the remaining height (rockers go horizontal on short screens), and every key stays at least 48 dp. The header contains only the TV picker, short inline status and ⋮; normal TV names such as "family room tv" show in full at 411 dp while the status may ellipsize. The small hot-pink 56 dp power key sits alone on the next row, left-aligned beneath the TV picker, and always shows its static white icon with no adjacent text. There is no separate status or notice row and no bare "Connected" line under the app shortcuts. TalkBack reads the power key as its action (Turn on / Turn off) with the inferred state.

- Live typing (dk-811e7991): in the TV system search, check the phone keyboard auto-opens without a dialog, input box or overlay strip. If absent, tap Keyboard. Type `ab`, delete to `a`, delete to empty, then Unicode; confirm full snapshots replace rather than append, including when the TV field already contains text. Hide the IME with the normal Android keyboard control and Back; verify typing ends once, no highlighted TV key is activated and remote OK is a separate action. In Netflix's profile PIN screen, tap Keyboard and type several digits; confirm each phone digit enters once through KEY_0..KEY_9 and no automatic Enter occurs. Check no delayed text/digits after Wi-Fi loss, background, TV switch or standby. Reconnect once, then open Settings ? TV messages: confirm the earlier entries remain, a `— reconnected —` marker separates attempts, outgoing text/end/digit frames appear, Copy works, and token/identity redaction holds. Record phone/TV model, app and build SHA; no automated test establishes TV protocol support.
