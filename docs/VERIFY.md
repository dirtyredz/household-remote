# Fast gate

From the repository root, with JDK 17/21 and Android SDK 36 already installed:

```powershell
.\src\android\gradlew.bat -p src/android --offline --no-daemon ktlintCheck testRemoteDebugUnitTest testOwnerDebugUnitTest lintRemoteRelease lintOwnerRelease assembleRemoteDebug assembleOwnerDebug assembleRemoteRelease assembleOwnerRelease bundleRemoteRelease bundleOwnerRelease
node --test src/household/export.test.mjs src/android/releases/contract.test.mjs tools/script-run/script-run.test.mjs
node src/android/icons/generate-icons.mjs --check
powershell -NoProfile -File scripts/release/signing-smoke.ps1
dk check
```

Use `ktlintFormat` only to format source before the gate. If cached pinned Maven dependencies are missing, rerun without `--offline` only for the owner-authorized Google/Maven Central/plugin portal resolution. Do not download other tools. Set ANDROID_HOME or git-ignored local.properties for an installed SDK. No live TV keys, full tiers, emulator or installed smoke in this gate.

Before a Gradle build inspect `Get-CimInstance Win32_Process` command lines for other Android builds. Use no-daemon and in-process Kotlin compilation; stop only processes started by this work.
