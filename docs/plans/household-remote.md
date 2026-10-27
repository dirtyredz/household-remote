**1. Goal**

Build an Android household remote that controls Samsung TVs directly over the LAN. Only the enrolled owner’s phone can change per-TV AdGuard restrictions through an ANU helper; ANU owns timer expiry.

V1 includes discovery, pairing, navigation, volume, standby/Wake-on-LAN, calibrated app shortcuts, and timed parental controls. Defer other TV brands, cloud access, voice, casting, arbitrary macros, recurring schedules, and automatic token sharing.

**2. Placement**

**Keep this inside `tv-tuneup`.** Device inventory, Samsung findings, and AdGuard operations already belong here. Separate responsibility directories prevent the Android app from growing the existing CLI. A new repository would split changes that need coordinated testing.

Use Kotlin with native Views, thin Android adapters, and independently testable state machines, following Agent Eye. Use an ASP.NET Core .NET 10 Windows service for ANU: native service hosting and Windows credential protection justify introducing another toolchain. [.NET support policy](https://dotnet.microsoft.com/en-us/platform/support/policy/dotnet-core).

Paths below are relative to `tv-tuneup`; grouped names denote separate files.

| Files / directories | Responsibility |
|---|---|
| `src/android/{settings.gradle.kts,build.gradle.kts,gradle.properties,gradlew,gradlew.bat,gradle/wrapper/*,version.properties}`, `app/build.gradle.kts` | Android build, pinned Agent Eye toolchain, `remote` and `owner` product flavors with distinct application IDs. |
| `src/android/app/src/main/java/local/tvtuneup/` | Source root. Separate `discovery/{SsdpDiscovery,TvInfoClient}`, `remote/{SamsungProtocol,RemoteSession,KeyDispatcher}`, `power/WakeOnLan`, `shortcuts/{ShortcutProfile,ShortcutRunner}`, `inventory/{TvRepository,HouseholdImport}`, and `security/{TokenStore,TvTrustStore}` files. |
| Same source root: `ui/{MainActivity,TvListScreen,RemoteScreen,RemoteViewModel}` | Presentation and lifecycle ownership; no networking inside views. |
| `src/android/app/src/owner/.../parent/{ParentApi,OwnerEnrollment,ParentViewModel,ParentScreen}` | Owner-only administration. Remote flavor has no parent screen, enrollment, or admin client. |
| `src/android/app/src/main/{AndroidManifest.xml,res/*}`, flavor manifests | Permissions, backup exclusions, accessibility, resources, flavor-specific entry points. |
| Android `selfupdate/` packages; `src/android/{releases,signing,tooling,icons}/`; `scripts/release/*.ps1`; `.github/workflows/android-{ci,release}.yml` | Adapt Agent Eye’s updater, unsigned release pipeline, local signing, and SVG-derived adaptive icons. Keep its separate transport, validation, download, installation, and signing responsibilities. |
| `src/parent-control/{ParentControl.csproj,Program.cs}` and `Api/`, `Enrollment/`, `AdGuard/`, `Policy/`, `Persistence/`, `Security/` | Thin service composition; separate endpoint validation, device authentication, AdGuard adapter, rule transformation, lease reconciliation, SQLite storage, and protected secrets. |
| `scripts/parent-control/{install,uninstall,enroll,revoke}.ps1`, `remote/*.ps1` | Service deployment, recovery task, owner enrollment and revocation. Reuse the verbatim owner-run harness; keep policy out of PowerShell. |
| `src/household/{export.mjs,export.test.mjs}`, `config/parent-control.example.json` | Explicitly export selected nonsecret TV facts for local phone import; document helper configuration. Existing `config/tvs.json` keys remain stable IDs. |
| Mirrored Android test packages, `src/parent-control.tests/`, release contract fixtures | Offline verification at each boundary. |
| `package.json`, lockfile, `.gitignore`, living docs listed below | Test/release wiring, dependency pins, generated-output exclusions and operational guidance. |

Do not transplant the CLI’s raw frame codec or command dispatcher. Android uses a maintained WebSocket implementation and reproduces the documented Samsung messages. Existing CLI callers remain unchanged; no extraction from them is necessary. Keep every code file below approximately 800 lines.

**3. Sequence**

1. **Establish build and security boundaries.** Scaffold both flavors and the helper; pin Agent Eye’s documented Android toolchain initially. Produce installable debug builds and an unsigned release draft. Household addresses, MACs, tokens and 1Password references must not enter public release assets.

   **Acceptance:** both flavors build; remote flavor contains no administration entry point; existing CLI tests pass.

2. **Deliver discovery, pairing and basic remote.** SSDP runs asynchronously, with a scoped multicast lock, manual-IP fallback and bounded REST probes. Restore the last selected TV immediately instead of waiting for discovery. Match devices by MAC/available stable identity, not IP alone.

   Maintain one foreground WebSocket session for the selected TV. Preserve client name **`TV Tuneup`**, the 90-second initial approval window, token refresh, and explicit denied/re-pair states. Serialize keys; cancel pending input on disconnect or TV change, and never replay old presses after reconnect. Ordinary taps must not inherit the CLI’s 800 ms delay.

   Encrypt each phone’s per-TV tokens using an Android Keystore key; exclude credentials from backup and logs. Phones pair independently. Share only an explicitly exported local TV manifest containing IDs, names, models, MACs and address hints; discovery remains available without it. [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

   Accept a TV’s self-signed certificate only during explicit first pairing, then pin it per TV. Certificate changes require re-pairing. Keep TV trust handling isolated from helper and GitHub clients; plaintext REST is limited to TV information probes.

   **Acceptance:** both known TVs work after app/process restart; two phones can pair and reconnect without invalidating each other or the desktop client.

3. **Add power and calibrated shortcuts.** Power-on sends a bounded Wake-on-LAN burst using the TV MAC and active LAN interface, then waits for connectivity. Standby sends the Samsung power key once; a timeout must not trigger repeated power toggles.

   Netflix, Prime, Disney+ and Plex shortcuts use per-TV Home-bar profiles established while someone watches. Each profile contains navigation and delays; execution is cancellable and exclusive with ordinary input. Disable unconfigured shortcuts. Display “sequence sent,” not an unverified claim that the app opened.

   **Acceptance:** wake works from standby on both models; each shortcut succeeds from Home and an already-running app. If navigation cannot reliably establish position, ship that TV with ordinary remote controls and disabled shortcuts.

4. **Implement narrow ANU authority.** Bind helper HTTPS to `<anu-ip>:8443`, subject to port availability, and firewall it to the household subnet. Run under a dedicated restricted service identity. The helper calls AdGuard’s existing `<anu-ip>:3000` endpoint locally; no phone receives its admin password.

   Bootstrap the password through the attended 1Password/SSH workflow into DPAPI-protected, service-ACL-restricted storage. Never pass it in command-line arguments or logs. This deliberately extends the existing “only a password hash reaches ANU” decision because authenticated API calls require a reusable credential.

   Owner enrollment starts from the owner’s workstation: provide the helper certificate fingerprint out of band, then a six-digit code valid for two minutes, single use, burned after five total failed guesses. Register a phone-generated, non-exportable client key for mutual TLS. Allow one owner enrollment; replacement requires local revocation/re-enrollment. Protect parent access with device authentication. Installing the owner APK alone grants no authority.

   Expose only authenticated status and bounded policy operations for registered TV IDs. Reject arbitrary domains, URLs, client IPs and raw AdGuard payloads.

   **Acceptance:** unenrolled devices and the remote build cannot administer anything; certificate mismatch, expired codes and revoked keys fail closed.

5. **Implement durable restrictions and timers.** Use AdGuard client updates for YouTube and custom-rule updates for the four store/update hosts. Preserve unrelated client fields, services, rules, the Samsung QBE exception and TV Plus access. The API exposes these operations separately; do not treat them as one atomic transaction. [AdGuard v0.107.79 API](https://raw.githubusercontent.com/AdguardTeam/AdGuardHome/v0.107.79/openapi/openapi.yaml).

   Migrate the existing shared `$client=` host rules into separately managed per-TV rules before enabling controls. Use the existing inventory IDs and verified DHCP reservations; never silently redirect a restriction after discovering a different IP.

   Store each control’s baseline, temporary override, UTC expiry, revision and operation ID in SQLite **before** applying it. Store/update access defaults blocked, with temporary unlocks of 1–120 minutes. YouTube supports persistent allow/deny or a timed override returning to its baseline. A new override replaces the previous one.

   Serialize writes, merge against fresh AdGuard state, verify results and retry pending operations idempotently. Reconcile at startup and expiry. Configure Windows service recovery plus a one-minute scheduled reconciliation command sharing the same lock and state.

   Status distinguishes requested policy, verified AdGuard configuration, expiry, last observation and pending/error state. Phone countdowns are informational; expiry never depends on Android execution.

   **Acceptance:** one TV can be unlocked while the other remains blocked; expiry survives phone sleep, force-stop, helper crash and ANU reboot.

6. **Finish distribution and household acceptance.** Adapt Agent Eye’s unsigned APK/AAB → draft release → attended local signing → publication flow, including provenance checks, downloaded-byte verification and publication last. Use this app’s own signing identity and flavor-specific assets. Copy Command Deck’s release checks, without its private Worker or unattended-signing infrastructure.

   Updates are manually initiated from public GitHub releases, credential-free, package/flavor/signer checked, and forward-only by version code.

   **Acceptance:** two successive signed releases update in place and retain pairing; wrong-flavor, wrong-signer and downgrade packages are rejected.

**4. Tests**

- **Android JVM tests:** SSDP deduplication and IP drift; exact Samsung messages; pairing denial/token refresh; reconnect without key replay; queue cancellation; shortcut timing; WOL packet construction; manifest validation.
- **Android integration tests:** fake REST/WSS servers with self-signed and changed certificates; storage/backup exclusions; flavor isolation; permission denial; lifecycle transitions; updater contract fixtures.
- **Helper tests:** fake AdGuard HTTP server, injected clock and temporary SQLite database. Cover per-TV rule migration, preservation of unrelated settings, concurrent/retried operations, crash boundaries, expiry replacement, reboot catch-up, clock jumps, unavailable AdGuard and partial application.
- **Security tests:** enrollment exhaustion/replay, unregistered/revoked client keys, certificate mismatch, unauthorized TV IDs, excessive durations and secret redaction.
- **Release/deployment tests:** disposable signing key, flavor/package checks, service recovery and reconciliation-task installation. No physical TV is required for these suites.

**5. Verification**

Nothing was built or tested during planning. The implementer must run:

- Existing: `npm test`.
- Adapted Android checks: `.\src\android\gradlew.bat -p src/android ktlintCheck testRemoteDebugUnitTest testOwnerDebugUnitTest lintRemoteRelease lintOwnerRelease assembleRemoteRelease assembleOwnerRelease bundleRemoteRelease bundleOwnerRelease releaseTools`.
- Helper: `dotnet test src/parent-control.tests`; publish the service for `win-x64`.
- Release checks: `node src/android/icons/generate-icons.mjs --check` and the adapted disposable-key signing smoke.
- Existing AdGuard script suite on the authorized workstation; it includes live SSH probes and is not wholly offline.
- `dk check` before pushing. Before implementation item writes, run `docket guide`, claim the work, and release it when finished.

With the owner watching both TVs, verify two-phone pairing, every remote button, standby/wake, shortcuts, and policy isolation. Test timed expiry with the phone off, helper killed, and ANU rebooted. Confirm Netflix and TV Plus still work and blocked store installation still fails as documented.

Measure release builds: **p95 tap-to-socket-write under 50 ms**, **p95 tap-to-visible-response under 150 ms**, and **p95 cold open-to-ready under two seconds** for an already-paired, powered-on TV on healthy Wi-Fi. Record device/model and sample counts; exclude discovery, initial approval and wake from startup timing.

**6. Docs**

- `STRUCTURE.md`: add Android, helper, household export and release/test responsibilities to enforced Layout bullets.
- `docs/ARCHITECTURE.md`: direct TV path, independent phone credentials, owner enrollment, ANU authority and durable expiry.
- `docs/DECISIONS.md`: monorepo, flavor boundary, helper stack, credential provisioning and timer semantics.
- `docs/FEATURES.md`: phases and verified model capabilities; correct the stale “AdGuard not yet installed” row.
- `docs/GOTCHAS.md`: DNS caching/bypass, DHCP reservations, certificate changes, shortcut drift and helper outages.
- New `docs/{ANDROID-DISTRIBUTION,PARENT-CONTROLS}.md`: exact build/sign/update commands, enrollment, recovery, credential rotation and uninstall.
- `CLAUDE.md`, `docs/TV-CONTROL.md`, `docs/NEW-TV-PLAYBOOK.md`, `README.md`: correct the unidentified/“not paired” parents-TV entries and add household onboarding.

**7. Open questions / assumptions**

- **Comparable apps:** [SmartThings](https://www.samsung.com/us/support/answer/ANS10007047/) supplies the familiar remote layout but requires Samsung account/internet setup. Open-source [Smart TV Remote Control](https://github.com/mazen-salah/Smart-TV-Remote-Control) documents discovery, remembered TVs and WOL; [Universal TV Remote](https://github.com/arman0824/Universal-TV-Remote) documents local Android Samsung pairing. Use these as UX/protocol references; household AdGuard authority is the reason for this app.
- Assume ANU runs continuously and TV IP reservations are established. Discovery cannot safely repair IP-scoped parental policies automatically.
- Independent phone tokens under the shared client name, Wi-Fi wake and reliable Home-bar anchoring are unverified release gates. Do not silently fall back to sharing credentials.
- DNS restrictions do not instantly terminate existing streams and can be bypassed by changing TV DNS/network settings. “Remote-only” removes administration authority; it does not make TV settings inaccessible.
- No concurrent dashboard editing of managed rules during helper writes. AdGuard lacks a cross-operation transaction; unexpected drift must surface as an error.
- If both reconciliation mechanisms are unavailable while AdGuard keeps running, temporary access can outlast its deadline. Recovery restores policy; strict expiry despite total helper failure requires additional infrastructure.
- Initial TV certificate trust assumes a trusted household LAN. Helper trust is established separately out of band.
- Match Agent Eye’s SDK baseline initially and verify LAN permission/denial behavior before increasing the target SDK. [Android LAN protections](https://developer.android.com/privacy-and-security/local-network-permission).
- **Delivery limitation:** this session is read-only and no output-file path was supplied. The spec is provided here; no plan file, code or work items were written.