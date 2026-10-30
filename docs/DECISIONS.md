# Decisions

- Owner overrides the original plan: separate household-remote repo, Compose, phases 1/2/3/6 only. `owner` is a distinct package with identical remote functionality and no administration authority. No helper, enrollment, AdGuard or timer code in v1.
- Command Deck toolchain: AGP 8.13.2, Kotlin 2.3.21, Compose BOM 2026.03.01, SDK 36/min 26, JVM 17. Command Deck has no ktlint Gradle setup; use Agent Eye's ktlint 14.2.0 plugin. JSON suffices; no Room.
- The source repository `dirtyredz/household-remote` is public. CI drafts unsigned artifacts here; attended signing replaces draft assets and publishes this same release last. The app reads this repository anonymously. No separate releases repository or GitHub credentials in the app.
- Household facts and credentials never belong in git. Examples use fictional values; the real inventory is ignored local input. A cheap privacy test scans tracked and unignored candidate files for the household subnet and non-placeholder 1Password references.
- Updates are manual, forward-only, exact package/flavor and installed-certificate matched. Android package installation remains attended. Release Gradle builds never access the permanent key or 1Password.
- App buttons launch by Samsung app ID with REST as the primary transport (ADR below; calibrated sequences were removed 2026-10-28). Success means the launch request was accepted, not confirmed open. Standby is one key with no retry; ordinary taps have no artificial delay.

## ADR: App buttons launch by Samsung app ID; calibrated sequences removed (2026-10-28)

Status: accepted (owner decision 2026-10-28, dk-254f8636). Reverses the earlier decision that app shortcuts are per-TV calibrated key sequences, disabled until calibrated.

Netflix, Prime, Disney+ and Plex open their app directly by Tizen app ID. The IDs live in `apps/TvApp` (Netflix 3201907018807, Prime Video 3201910019365, Disney+ 3201901017640, Plex 3201512006963), read from the household TVs with a read-only GET on 2026-10-28; the older Netflix/Plex IDs return 404 there.

The primary transport is the state-changing `POST http://<tv>:8001/api/v2/applications/<id>` through a private-IPv4-only, short-timeout, no-redirect `RestAppLauncher`, separate from the read-only install probe. With owner permission on 2026-10-28, a plain POST returned HTTP 200 `true` and changed Netflix from `visible:false` to `visible:true`; this is direct evidence that the endpoint launches on the affected TV. `AppController` emits one websocket fallback after a non-2xx response or I/O failure, using the known Samsung shape: `{"method":"ms.channel.emit","params":{"event":"ed.apps.launch","to":"host","data":{"appId":...,"action_type":"DEEP_LINK"}}}`. The existing field names and nesting were correct, but the owner report plus the proven REST result show that a correct websocket request may still be ignored by a TV. All four fallback frames use DEEP_LINK; `NATIVE_LAUNCH` remains available per app.

A launch is one user action: it needs a usable session, is never held while reconnecting (opening an app seconds later, after the moment passed, would surprise whoever pressed it) and is never replayed. Lifecycle stop, TV selection and standby prevent a later fallback. The held-key rule stays for ordinary keys only. The TV can answer the fallback with an `ed.apps.launch` event whose `data` is a bare status; `SamsungProtocol.event` ignores non-object `data` instead of treating it as a protocol error that would end the session. Neither transport confirms that the app visibly opened, and the remote shows no transient launch feedback.

On each new usable session `apps/AppInstallClient` asks the TV `GET http://<tv>:8001/api/v2/applications/<id>` for the four apps (read-only, private LAN addresses only). A 404 greys the button out (TalkBack: "not installed on this TV"); 200 and anything unclear (timeouts, other codes) leave it usable, so a network blip never disables a working button. Results are cleared on TV selection change and before each new-session probe, so a stale 404 cannot keep a button disabled.

Removed: `ShortcutProfile`/`ShortcutStore`/`ShortcutTextCodec`, `CalibrationDialog`, the Calibrate link, the Cancel-sequence row swap, the sequence busy state and power-cancels-sequence wiring. The v0.2.0 `shortcuts` preferences file is deleted once at startup (`apps/LegacyShortcuts`, failures ignored; it was never in backups).

Rejected: keeping calibrated sequences as a fallback (fragile home-bar positions and delays, the reason for the change), websocket launch as the primary path (valid payload but unproven behavior on the affected TV), and holding a launch through recovery.

## ADR: A remote that never scrolls, a red power key, and a Wi-Fi gate (2026-10-28)

Status: accepted (owner phone feedback on v0.2.0, 2026-10-28; Wi-Fi part implements dk-28d77a2d).

Layout: nothing scrolls vertically. `RemoteScreen` is a fixed column whose D-pad takes the remaining height (`weight`), clamped to 150..300 dp so every key keeps the 48 dp floor; below 660 dp of window height (plus 200 dp per +1.0 font scale) the volume/channel rockers become one horizontal 48 dp row and the app row uses 48 dp keys. Control labels step their font down on one line instead of wrapping. Under extreme combinations (e.g. a 568 dp window at 2.0 text) the 48 dp floor wins and the D-pad can clip; that is accepted over shrinking targets.

The header row contains the TV picker, a short non-wrapping connection label and overflow action; the TV name gets priority width and the status may ellipsize. The 56 dp red power circle sits on its own row directly beneath the TV picker, left-aligned (owner directive, 2026-10-28), and must never share the header row. The power key always shows the same white icon (disabled alpha may differ); its content description is the action and its state description the inference. The dot plus short header label is the only visible state text, and "No Wi-Fi" itself opens Wi-Fi settings. There is no separate status or notice row: transient key/app feedback, standby confirmation and power errors are not displayed, so the D-pad takes the remaining height.

Wi-Fi: `network/AndroidWifiMonitor` (foreground only) reports whether any Wi-Fi network exists, via a TRANSPORT_WIFI callback without the INTERNET requirement, because the TVs are on the LAN. Without Wi-Fi, `RemoteSession.networkChanged(false)` parks the target as Unavailable ("Phone not on Wi-Fi") with no attempts, timers or window; a connect, including a pairing request, only records the target, so pairing never discards credentials offline. Wi-Fi returning starts one fresh recovery window. Terminal Offline states (after standby, pairing/trust failures) and a running approval are not resumed. Keys and power are disabled while off Wi-Fi; the header's "No Wi-Fi" action offers the system Wi-Fi panel (Android 10+) or Wi-Fi settings. The monitor is its own component, not ViewModel code (dk-63120852).

Rejected: keeping a scroll container for large text (owner ruling), a Snackbar host (it overlays the shortcuts and needs event plumbing for repeated messages), a separate centred status row, a notice slot, gating on the default network only (a VPN or mobile-data default would hide a usable Wi-Fi), and leaving Turn on enabled off Wi-Fi (the wake broadcast cannot leave the phone).

## ADR: Foreground-only automatic reconnect with a heartbeat (2026-10-28)

Status: accepted (implements the dk-4bbfb674 fix spec, `docs/plans/connection-reconnect.md`).

A socket drop used to cancel input and stop: the next tap failed until a manual Reconnect. `RemoteSession` is now the single owner of the socket and its recovery. A transient close or I/O failure retries with the saved token and pinned certificate after 1, 2, 4, 8, then 15 s (capped), one attempt at a time, inside a 60 s window per outage; then the header reports "Unavailable". The failure streak resets only after 30 s of stable connection, so a flapping link keeps backing off. A saved-credential attempt has a 10 s deadline; explicit pairing keeps its 90 s approval window and is never retried. The TV client sends a WebSocket ping every 15 s, so a silent half-open peer fails within about two intervals; no remote-control key is used as a keepalive. These timings are proposed defaults, not measured Samsung requirements.

Not retried: a changed certificate, a missing pairing, denial, unreadable credentials or an invalid protocol reply (typed `ConnectionFailure`; none removes saved credentials). Never retried either: anything in the background (ON_STOP disconnects), the previous TV after a selection change, and the drop that follows a deliberate standby, which the session is told to expect so it does not fight the user. Wake is never automatic.

Input: keys already accepted by the socket, app launches and KEY_POWER are never resubmitted. While reconnecting, ordinary keys stay enabled and exactly one fresh, never-submitted key may wait 5 s; it is sent once when the session returns, else dropped silently. Extra taps are rejected silently and never extend the window; a tap after the window ran out starts one fresh window. Selection, background, explicit reconnect and terminal failures discard the waiting key. Because Samsung gives no per-key acknowledgment, a key sent into an undetected half-open socket can still be lost; only the held fresh key is promised.

Rejected: OkHttp `retryOnConnectionFailure` (an HTTP retry, not WebSocket recovery), a second session manager or foreground service, background keep-alive, automatic WOL, replaying queued input.

## ADR: Inferred power toggle and typed connection status (2026-10-28)

Status: accepted (owner approved the plan's assumptions 2026-10-28).

The phone remote has one prominent power control. A usable session infers the Turn off action and a press sends exactly one standby key; anything else infers Turn on and a press runs the existing wake burst, MAC-checked readiness polling and reconnect. Samsung's remote channel gives no panel power telemetry, so an ordinary disconnect is never treated as confirmed off and accepted standby only records that the socket accepted the frame while keeping ordinary controls disabled until a new session. These inferences are exposed through the power key's TalkBack action/state, not visible feedback text. The session is not disconnected right after standby, because cancelling the socket could drop the queued frame. Power takes priority over queued input and discards it (it used to cancel a running shortcut sequence, removed 2026-10-28); a short power-only guard stops a double tap from reversing the action; nothing retries automatically. Power state lives in `PowerController`, separate from general messages. Ordinary keys stay single-tap with no hold-to-repeat.

`RemoteSession` now emits a typed `ConnectionStatus` (Connected, Connecting, AwaitingApproval, Offline plus a message) at its existing transition sites, including Offline on explicit disconnect. Presentation and power read that, never the message text. Reconnect policy was unchanged by this ADR; the automatic-reconnect ADR above later added Reconnecting/Unavailable to the same seam.

Authorized test artifacts (owner, 2026-10-28): `androidx.compose.ui:ui-test-junit4` and `ui-test-manifest` (Compose BOM versions), `androidx.test:runner` 1.7.0, `androidx.test:core` 1.7.0 and `androidx.test.ext:junit` 1.3.0, for fake-state Compose instrumentation tests only. `ui-test-manifest` is debug-only, so release artifacts are unchanged. Rejected: substituting JVM state tests for Compose acceptance, and an icon library (local vectors instead).

## ADR: Pin TV certificates and protect credentials with Android Keystore

Status: accepted.

Each phone pairs independently. Explicit pairing is the only time a first-seen TV leaf certificate may be accepted; reconnects pin that certificate by SHA-256, and a changed certificate requires an attended re-pair while watching the intended TV. Tokens and pins are AES-GCM encrypted with a non-exportable Android Keystore key and stored in no-backup files, with the TV MAC as authenticated data. This contains a compromised or restored credential file and prevents discovery HTTP or TV-specific trust exceptions from weakening the public update path.

## ADR: Berry gradient, heart OK and matching launcher icon (2026-10-30)

Status: accepted (owner and household choice, dk-c083aca0; mockup C and icon concept 3).

The remote uses a #581B49 to #24133D vertical gradient, #392548 plum surfaces, #FF80BF pink, #BC9AFF lavender and #FFF7FC text. The gradient is drawn before safe-area padding, behind the transparent dark-style system bars; native startup uses its berry top colour. Shared controls use translucent plum and muted but readable disabled content. The OK heart is decorative behind the existing circular Surface, preserving the target size, ripple, OK label and KEY_ENTER action. Its disabled fill is muted plum. The ring and directional arrows use lavender. Power remains the existing red, and all layout and input rules are unchanged.

The launcher uses vector heart/arrows derived from the checked-in SVG on solid purple, plus an Android 13 monochrome layer. Generated resources remain byte-checked by the icon generator. AI reference rasters are not shipped; texture and lighting from the mockups are intentionally simplified into flat vector/Compose artwork. Installed-release visual acceptance remains attended.

## ADR: Phone keyboard submits on Done (2026-10-30)

Implements dk-811e7991; real-TV acceptance remains pending. The paired session forwards ms.remote.imeStart / ms.remote.imeEnd only from its current socket. Start opens a phone text dialog while input is usable; End, dismissal, session teardown, standby, selection and background close it. A Keyboard button in the navigation row opens the same dialog when Samsung/app IME events are absent, without adding height or changing the dedicated power row.

Text is a transient local draft, sent once on Done as UTF-8 base64 in ms.remote.control params (Cmd, DataOfCmd: base64, TypeOfRemote: SendInputString). No Option field is added to text frames; casing and field order follow the existing Samsung key builder. Done queues the text and KEY_ENTER as one batch, preventing interleaving or a partial enqueue when full. Empty text still sends the empty string then Enter. A refused text send stops before Enter. Neither frame is held through recovery or replayed. Socket acceptance is not delivery confirmation.

Rejected: live-per-change text, because replacement versus append behavior is unverified across TV apps and could duplicate input. The draft is neither persisted nor prefilled from TV text. Cancel sends nothing. Owner must verify Unicode, repeated submissions, auto-open/end, manual fallback and Enter in actual TV search fields; custom app keyboards may ignore this channel.
