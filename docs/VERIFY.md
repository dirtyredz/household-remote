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

# Compose UI instrumentation (separate from the fast gate)

Only when a device or emulator is already attached (`adb devices`), with the same temp/daemon-registry setup as above:

```powershell
.\src\android\gradlew.bat -p src/android --offline --no-daemon "-Dorg.gradle.daemon.registry.base=$daemonRegistry" connectedRemoteDebugAndroidTest connectedOwnerDebugAndroidTest
```

These mount pure composables (`ui/remote`) with fake state and recording callbacks; they never start `RemoteApp`, the ViewModel factory, `ReleaseUpdater`, or contact a TV. Without a device, compile them with `compileRemoteDebugAndroidTestKotlin` and report the connected run as not run.

# Attended phone acceptance (owner, while watching the TV)

Record phone, model and build SHA in the Docket item.

- Power is immediately discoverable; text, status and primary controls are readable without setup clutter; dark startup and system bars.
- With the TV on and "Connected": "Turn off" puts the TV in standby and shows "Standby sent · power off not confirmed"; the next press ("Turn on") wakes it and reconnects.
- Wake progress, Wi-Fi off, readiness timeout and pairing/connection failure each show an understandable message.
- Switching TVs or backgrounding during "Turning on…" never affects the previous TV afterwards.
- D-pad, Back/Home/Source, volume, mute, channel, haptics, calibrated shortcuts and cancellation behave correctly.
- TalkBack, large text and a short screen remain usable (lower controls scroll; header and power stay put).
