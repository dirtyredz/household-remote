## Chosen scope (lead, 2026-11-22)

Keep: (1) attended IME probe in Jellyfin's field (does `ms.remote.imeStart` fire; replace vs append); (2) Plex->Jellyfin catalog swap (TvApp, 5 test files, README/FEATURES one-liners) with the REAL app id read from the TV first; (3) one GOTCHAS entry with the probe result; (4) fast gate on CI/ANU, one attended post-change check (button launches; `ab`/`A1 é` typed from phone replaces field text).
Cut: blank-ID guards (sequence the id first instead); symbols pages; unrelated stale-doc fixes (filed to Docket); research-doc append; extra publication/dk-check runs; baseline validator run (step 1 is the baseline); cancellation/reconnect validation scenarios.
Deferred (Docket, built only if the probe fails): "Keyboard for" selector/TypingProfile/Settings UI; Jellyfin GridLayout + GridTyping changes (needs attended navigation capture on the TV first).
Validation: attended check on final SHA by the validator only if typing code changes; for a pure catalog swap `Validation: none (catalog constant + tests; launch/typing observed in the attended check above)`.
Blocked on: Jellyfin app id (unknown; store install), TV probing not before ~1:30 PM CT.

---
## Goal

Replace Plex with Jellyfin and support typing in Jellyfin search using Samsung’s existing IME path wherever it works. Implement a separate Jellyfin grid only if attended testing establishes that text snapshots are unusable.

Diagnosis: none (new app support; Jellyfin’s input capability remains an acceptance question).

## Placement

Paths below use `M = src/android/app/src/main/java/com/dirtyredz/householdremote` and `T = src/android/app/src/test/java/com/dirtyredz/householdremote`.

**Always:**

- `M/apps/TvApp.kt`: replace `Plex` with `Jellyfin`; keep the unknown ID in one named `JELLYFIN_APP_ID` constant. Labels currently live here; there is no Plex resource string.
- `M/apps/AppInstallClient.kt`, `M/apps/AppController.kt`, `M/ui/RemoteState.kt`: while that constant is blank, skip its install probe, refuse launch and disable its button. Preserve `Unknown`, rather than falsely reporting “not installed.”
- `M/ui/remote/AppButtons.kt` and `src/android/app/src/main/res/values/strings.xml`: give the temporarily unconfigured button an accurate accessibility description.
- Existing tests listed below and living docs listed under Docs.

**Only if an explicit Jellyfin typing selection is needed:**

- New `M/ui/typing/TypingProfile.kt`: explicit manual typing choice, independent of claimed foreground-app detection.
- `M/ui/typing/TypingInput.kt`: capture the selected profile per keyboard generation; actual TV IME events take precedence.
- `M/ui/RemoteViewModel.kt`, `M/ui/RemoteState.kt`, `M/ui/RemoteApp.kt`: selection state, lifecycle resets and composition-root injection.
- `M/ui/settings/SettingsScreen.kt`: host a small “Keyboard for” selection; strings belong in `res/values/strings.xml`.

**Only for grid outcome B:**

- `M/remote/GridLayout.kt`: navigation graph and path planning.
- New `M/remote/HouseholdGridLayout.kt`: extract the existing shared layout data.
- New `M/remote/JellyfinGridLayout.kt`: Jellyfin keys, verified directed transitions and character actions.
- `M/remote/GridTyping.kt`: track projected focus, page and case; execute the selected layout’s character actions through the existing queue.

No transport or queue replacement is needed. Keep files below approximately 800 lines.

## Sequence

1. **essential — Settle the IME question before grid implementation.**  
   On the existing build, with the owner watching, open Jellyfin’s actual search field. Inspect TV messages for exact `ms.remote.imeStart`. When present, type `ab`, delete to `a`, clear, then type `A1 é`; repeat with existing TV text. Confirm replacement rather than append and that hiding the phone keyboard does not activate Go. Capture sanitized messages, SHA, phone/TV versions and Jellyfin version.

   No event is inconclusive about snapshot support. The existing manual Keyboard opens the household grid, so do not use it to probe Jellyfin text support blindly. The photos establish key labels and arrangement, not navigation or protocol behavior; their Connect/Cancel context also warrants checking the actual search keyboard.

2. **essential — Replace the app catalog entry.**  
   Change the enum, labels, tests and docs. Use a blank ID constant until the installed Jellyfin ID is supplied and verified; implement the temporary guards above. Retain REST launch followed by one websocket fallback and existing cancellation rules.

   Acceptance: four buttons remain, Jellyfin replaces Plex, and an unknown ID produces no request. Functional launch acceptance remains pending until the constant is filled.

3. **essential — Outcome A: use working snapshots.**  
   If `imeStart` and replacement snapshots work, finish with the catalog change and acceptance documentation; change no typing code.

   If the event is absent, add only the explicit Jellyfin selection to test direct snapshots. Keep observed IME state separate from manually selected snapshot mode. If that attended test succeeds, retain this small override and stop before grid work.

4. **essential — Outcome B, conditional on snapshots failing: capture navigation.**  
   With the physical remote, record directed moves needed to reach every supported letter, space, backspace and shift. Verify bottom-row landings, starting focus, lowercase/uppercase transitions, shift persistence and focus after activation. Check whether physical digit keys insert digits without moving focus.

   Unknown transitions remain unavailable; never infer wrapping or wide-key landings from photographs. Do not enable a layout until verified routes cover its supported character set.

5. **essential — Implement B’s verified letters layout and routing.**  
   Extract household layout data, introduce explicit Jellyfin transitions, and inject the selected layout for each generation. Provide Settings choices for the shared apps and Jellyfin; do not infer the active app from launch success.

   Preserve Jellyfin case through verified shift actions. Support letters, space, backspace and verified direct digits; defer other characters. Keep unsupported characters from causing deletion of text that was never emitted. Gear, globe, Go, macros and caret controls must never become accidental typing targets.

   Changing profile cancels the current generation. Close, session loss, TV switch, background and navigation out of the app invalidate projected focus/page/case. Actual `imeStart` cancels grid work and switches to snapshots. Unreachable targets or rejected batches fail without sending a partial edit.

   Acceptance: exact mixed-case text and deletions work from the verified anchor; household sequences remain unchanged; cancellation prevents stale presses.

6. **optional — Symbols pages.**  
   Add both photographed pages only after verifying their transitions and return focus. They have eleven cells per upper row, versus nine on the letters page. Implement page switching as data and tracked state, not another typing engine.

## Tests

Update existing Plex references in:

- `T/apps/AppControllerTest.kt`
- `T/apps/AppInstallTest.kt`
- `T/remote/AppLaunchTest.kt`
- `T/ui/RemoteStateTest.kt`
- `src/android/app/src/androidTest/java/com/dirtyredz/householdremote/ui/remote/RemoteScreenTest.kt`

Cover blank-ID suppression, configured launch/fallback behavior, Jellyfin semantics and four-button fit.

For conditional routing/grid changes:

- Extend `T/ui/typing/TypingInputTest.kt`: explicit selection, IME precedence, no duplicate digits, profile-switch cancellation and generation resets.
- Add `T/remote/JellyfinGridLayoutTest.kt`: independently recorded adjacency, asymmetric landings, missing edges, shift behavior and reachable supported targets.
- Extend `T/remote/GridTypingTest.kt`: mixed case, deletion after unsupported input, unreachable-target rejection and focus invalidation.
- Retain household exact-sequence and `GridQueueTest.kt` cancellation/pacing coverage.
- If the selector ships, add a fake-state Compose test under `androidTest/.../ui/settings/TypingProfileTest.kt`.

All automated tests use fakes or loopback peers.

## Verification

Implementer runs the **exact fast gate in `docs/VERIFY.md` on CI/ANU**, with both flavors and unsigned release builds; no local Gradle without further instruction. Current CI omits `publication.test.mjs` and `dk check`, so supplement those checks. Compile instrumentation tests; run connected tests only on an available authorized device.

No builds, tests or TV actions were performed during planning.

## Validation

- Claim: Jellyfin replaces Plex, launches correctly and accepts the supported phone-typing operations without unintended activation.
- Metric: exact visible app/text result; zero duplicate characters, unintended Go activations or stale presses.
- Method: independent validator follows Sequence 1 on the pre-change SHA, then repeats on the implementation SHA; additionally launches Jellyfin from Home and another app, tests close-mid-input and reconnect cancellation, and checks the unchanged shared-grid apps. Use the owner’s phone/TV, attended throughout. For B, include `Ab z`, backspace, verified digits and every enabled transition.
- Runs: 1 deterministic acceptance pass per SHA.
- Target: all applicable checks pass; Jellyfin ID verified and populated.
- Baseline: pending.

The orchestrator must supply `agents/validator.md`; it was not found in this checkout.

## Docs

- `README.md`, `docs/FEATURES.md`, `docs/ARCHITECTURE.md`: Jellyfin catalog and selected typing behavior; correct stale websocket-only launch descriptions and README’s obsolete “Opening…” feedback.
- `docs/DECISIONS.md`: record IME-first outcome; amend the grid ADR only if B ships.
- `docs/GOTCHAS.md`: verified Jellyfin behavior, anchor/case limitations and supported characters.
- `docs/VERIFY.md`: attended Jellyfin acceptance steps.
- `STRUCTURE.md`: conditional layout extraction/profile ownership.
- `docs/research/tizen-app-search-text-input.md`: append observed results without presenting earlier research as device proof.

## Open questions / assumptions

- **Unknown app ID:** leave one explicit placeholder; do not claim launch completion until populated and observed.
- **Selection tradeoff:** explicit Jellyfin selection adds a manual step but avoids unsupported foreground-app detection. External TV navigation still requires the owner to select the matching profile.
- **Permission regression:** keep `dk-2ddc3ffc` separate. GOTCHAS documents symptoms and a workaround, not a proven permission fix; this is not safely a cheap manifest addition. `dk show` failed because this sandbox was not recognized as a Git worktree.
- **Docket:** implementer runs `docket guide` before item writes, claims/releases work and resolves owner notes through the documented process.
- This response is the plan. No plan or separate report file was created.
---
## Plan update 2026-11-22 (lead, after attended probe + owner clarification)

Probe: blind IME text-send landed in Jellyfin's server-address field only; owner still cannot type in search/login. The owner's required behavior is the established design (ADR "App letter-grid typing"): each phone key tap becomes fast arrow/OK presses walking the TV keyboard, using the existing ordered queue and spacing. So Outcome B is in scope, IME work is out.

Scope (large-ish, ~6 files + tests):
1. `GridLayout.jellyfin` data from the photos (.tmp/jellyfin-keyboard-2026-11-22/): letters page only. Rows 0-3 are 9 columns: [a-g, www., backspace], [h-n, .com, !#1], [o-u, ':', globe], [v-z, ',', '.', '/', shift]; row 4: gear 0..0, space 1..4, Go 5..6, '<' 7, '>' 8. Focus starts on "a". Targets: letters, space, backspace, `. , : /`. Never target gear/globe/Go/arrows/www./.com/!#1/shift. Digits stay KEY_0..9 (verify attended that they insert digits on this keyboard; else follow-up for symbols page 1).
2. `GridTyping`: generic char->label (letters lowercase, space, backspace, and any char that equals a label in the injected layout); unsupported ignored as today.
3. Layout selection: `TypingLayout {Shared, Jellyfin}` in RemoteState, persisted like grid delay; auto-set when an app button is pressed (Jellyfin -> Jellyfin, other apps -> Shared); manual "Keyboard layout" choice in Settings. Changing it resets the typing generation. Existing household sequences unchanged.
4. Tests: layout path tests (independently written adjacency incl. wide space/Go landings), GridTyping punctuation + Jellyfin, selection reset, existing household tests unchanged. No TV key sends in tests.
Deferred to Docket: symbols pages 1/2 + shift/uppercase, wrap-around/edge behavior if the TV differs from the data (confirmed in the attended check).
Validation: attended check with the owner typing a Jellyfin search on the family-room TV (pre-change baseline = the owner's report that the shared grid mis-types there).
