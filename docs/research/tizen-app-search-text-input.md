# Research: typing search text into Prime Video, Netflix and YouTube on a 2023 Tizen TV

Date: 2026-10-30. Method: adversarial research (optimist vs pessimist researchers, two rounds, director spot-checked the load-bearing sources). Research only; no code or TV was touched.

## Question & decision at stake

How can Household Remote (Kotlin/Compose; paired on `wss://<tv>:8002/api/v2/channels/samsung.remote.control`; sends `ms.remote.control` `SendRemoteKey`; launches apps by REST `POST :8001/api/v2/applications/<id>` with an `ed.apps.launch` `DEEP_LINK` fallback) put a search such as "honey i shrunk" into Prime Video, Netflix and YouTube on the household's UN65CU8000 (2023, Tizen 7, `ImeSyncedSupport=true`, `TokenAuthSupport=true`)?

Observed on 2026-10-30: with Prime Video's search screen open the TV emitted no `ms.remote.imeStart`, and `SendInputString` had no effect.

The decision: choose between (a) driving each app's letter grid with arrow and OK presses, (b) keeping text-sending only (works only where Samsung's system keyboard appears), (c) dropping the keyboard feature, or something else this research turns up.

What would settle it: primary 2022–2026 evidence (source code, dated issue threads, vendor docs, field tests on Tizen 6–9) showing a channel that accepts text or a search query in these apps, plus an attended test on this TV.

## Bottom line

**Confidence: High on the negatives, Medium on the recommended build.**

- **No network channel accepts free text, or a search query, in any of the three apps on 2022+ Tizen.**
  - Samsung's IME path (`imeStart` / `SendInputString`, which is what the SmartThings phone keyboard uses too) does not fire in these apps.
  - No search deep link works:
    - Netflix's search DIAL body was tested and ignored.
    - No YouTube search route exists on Tizen.
    - Prime Video has no search link at all.
  - The YouTube Lounge (cast) API has no search command.
- **Option (a), letter-grid automation, is the only route that reaches the search box, and it is proven in a shipping product for YouTube and Netflix on Samsung TVs.** makeevapps "Remote for Samsung TV" (guide dated 2026-06-04) has dedicated YouTube and Netflix typing modes. It asks the user to put focus on a fixed anchor key first.
- **Prime Video grid typing is unproven.** No product or source documents it, so treat it as best-effort.
- **For YouTube there is a better-than-typing path**: search on the phone, then play the chosen video on the TV via the Lounge API `setPlaylist`. It is proven on Tizen, but it costs runtime internet calls, a YouTube Data API key (or ToS-grey scraping), and pairing that Google breaks from time to time. It is an enhancement, not the first build.

**Recommendation: build (a) grid typing for Netflix and YouTube first, with a user-confirmed focus anchor; add Prime as experimental; keep (b) as the generic fallback; do not do (c).**

## The case for (claims that survived scrutiny)

1. **Grid typing works on Samsung TVs for YouTube and Netflix. Strong.**
   - A commercial Android app does exactly this. makeevapps' guide (2026-06-04) describes a "Direct" mode "when the TV app already has a normal text cursor" (the IME path), plus separate **YouTube** and **Netflix** modes.
   - Each mode needs an anchor: YouTube "move the selection to the top-left key on the TV keyboard"; Netflix "place the TV selection on the A key".
   - The keyboard language must match.
   - Director spot-checked: https://makeevapps.com/blog/how-to-type-faster-on-samsung-tv/
   - Quality: secondary vendor doc, current. It proves feasibility and the anchor workaround, but not reliability numbers.
2. **YouTube Lounge `setPlaylist` plays a specific video on Tizen. Strong.**
   - Field notes from a 2024 DU7000 on Tizen 9 (2026-10-27) give the flow: screen id via DIAL, then `pairing/get_lounge_token_batch`, then `bc/bind`, then `setPlaylist` with a video id. It "plays the specific clip". Director spot-checked: https://github.com/MankhongGarden/samsung-tizen-tv-home-notes
   - iSponsorBlockTV's README lists Samsung Tizen as working: https://github.com/dmunozv04/iSponsorBlockTV
   - The pyytlounge command set includes `play_video` (setPlaylist) and `send_dpad_command`: https://pyytlounge.readthedocs.io/en/latest/pyytlounge.html
   - Pairing over the local network, from xdavidhu's gist (2021, older model, still the reference): https://gist.github.com/xdavidhu/cb87f926924bc174e6073b9f0b9d764e
     - `POST http://<tv>:8080/ws/app/YouTube`, body `pairingCode=<uuid>&theme=cl`
     - then `POST https://www.youtube.com/api/lounge/pairing/get_screen` with `pairing_code=<uuid>`, which returns `screenId` and `loungeToken`.
     - If YouTube is already running, `GET :8080/ws/app/YouTube` returns them in `additionalData`.
   - On Tizen 9, DIAL POSTs need the header `Origin: https://www.youtube.com`, or they get a 403 (MankhongGarden).
3. **Prime Video can open a specific title on Tizen 9 from an on-TV app. Medium, with a gap.**
   - The call is `tizen.application.launchAppControl` with `PAYLOAD = {"values":"<gti>"}`, using operation `http://samsung.com/appcontrol/operation/eden_resume` or the default (MankhongGarden, 2026-10-22).
   - The `gti` came from JustWatch's unofficial GraphQL API (`deeplinkURL(platform: TIZENOS)`).
   - **Unverified for a phone remote**: we don't know whether `ed.apps.launch` `metaTag` reaches the app as that same `PAYLOAD`. It is also title-level, not search.
4. **The existing text path, option (b), is right where it applies.**
   - SamMobile (updated 2026-02-05): the SmartThings keyboard works "only when an app uses Samsung's built-in virtual keyboard". Samsung Internet and Jellyfin work; "almost none" of the major apps do.
   - https://www.sammobile.com/news/samsung-already-fixed-tv-typing-but-app-developers-dont-care/
   - Keeping `SendInputString` on `imeStart` stays useful for browser and Jellyfin-style apps, and costs nothing.

## The case against (claims that survived scrutiny)

| # | Claim | Evidence | Severity |
|---|---|---|---|
| 1 | SmartThings' phone keyboard uses the same IME mechanism, so it can't type in these apps. | SamMobile 2026-02-05; Samsung support (updated Dec 2023) says keyboards are not supported in the YouTube app and points 2023+ owners to voice: https://ushl.samsung.com/py/support/tv-audio-video/the-keyboard-compatible-with-my-smart-tv-does-not-work-with-the-youtube-application ; the household's own 2026-10-30 capture (no `imeStart` in Prime). | Blocker for option (b) in these apps |
| 2 | Netflix deep links stop at the profile picker, and the search link is ignored. | smartest-tv #20 (UE55CU8000, the EU twin of this TV): after v1.3.3, Netflix "launches, but stops at the Who's Watching? profile screen" (2026-10-24, director spot-checked) https://github.com/hybirdss/smartest-tv/issues/20 . MankhongGarden 2026-10-27: DIAL `m=https://www.netflix.com/search?q=<title>&source_type=4` ignored; title bodies ignored; app-control only opens the app. | Blocker for a Netflix deep-link search |
| 3 | No YouTube search deep link exists on Tizen, and even `v=` is unreliable. | MankhongGarden: DIAL `v=<id>` ignored (a running app resumes; a cold start goes home). smartest-tv's "search-string via DIAL ✅" row has no format, and its code (`dial.py` `youtube_body`) builds only `v=<id>`; its own table is labelled "anecdotal, needs confirmation": https://raw.githubusercontent.com/hybirdss/smartest-tv/HEAD/docs/reference/deep-link-support.md . smartest-tv #6 (textninja, S90F) reports a search via "their SmartThings API" with no payload. | Blocker for a YouTube deep-link search |
| 4 | The Lounge API has no search, voice or text command. A query needs a separate phone-side search. | pyytlounge method list: play, pause, seek, `play_video`, volume, captions, `skip_ad`, `send_dpad_command`. Phone-side search means the YouTube Data API `search.list` (100 quota units per call, about 100 searches a day on the default 10,000 units, plus an API key in the APK) or undocumented InnerTube scraping (ToS risk). | Major (effort, internet dependency, key) |
| 5 | Lounge pairing breaks without notice. | iSponsorBlockTV #508 (opened 2026-10-17, open and pinned): two unnamed Samsung Tizen TVs bind with `loungeScreenDisconnected {"reason":"compromisedReceiver"}`; the official YouTube Android app also fails to pair with the same TV. One reporter; director spot-checked. Earlier: #450 (switch to 64-digit screen ids broke existing pairings), #457, #316, #179. | Major (reliability) |
| 6 | Grid typing has no published layouts and no focus feedback. | No published key map for the Netflix, YouTube or Prime TV keyboards. makeevapps' anchor step implies focus doesn't reset to a known key. Netflix rolled out a new TV UI in May 2025 (gigazine 2025-05-08), so layouts can change with app updates. `SendRemoteKey` is fire-and-forget, so one dropped key shifts every later letter. | Major (needs per-app maps captured on this TV, plus a recapture path) |
| 7 | Key pacing is unverified. | samsungtvws defaults `key_press_delay = 1` s (https://github.com/xchwarze/samsung-tv-ws-api, `remote.py`); one Home Assistant user needed 2000 ms on their set (https://community.home-assistant.io/t/improving-samsung-tv-control/151922?page=4). No source verifies 0.3 s. "honey i shrunk" is about 50–80 key presses: 15–25 s at 0.3 s, 50–80 s at 1 s. | Major for felt speed; minor for correctness |
| 8 | Prime Video is the least cooperative app. | Roku's phone keyboard reportedly works in YouTube and Netflix but not Prime (https://community.roku.com/t5/Roku-setup/Roku-iOS-app-for-remote-problems-with-searching-I-m-Prime/m-p/977527 , undated); Samsung EU forum 2020: keyboard ignored in Prime. No product documents a Prime grid mode. | Major for Prime |
| 9 | Voice, Bixby, the SmartThings cloud and `ed.*` events don't carry text. | Samsung staff on voice: convert speech to key codes yourself (https://forum.developer.samsung.com/t/samsung-tv-remote-voice-control-library/13488). The voice remote works over Bluetooth. SmartThings `launchApp` takes only an app id (https://community.smartthings.com/t/api-call-tv-app/247068). `ed.edenApp.get` and `ed.installedApp.get` only list apps. | Blocker for these routes |

## Genuine conflicts & how I weighed them

- **Is grid typing feasible?**
  - The pessimist called it fragile and unproven. Round 2 then found makeevapps shipping it for YouTube and Netflix on Samsung TVs, and conceded.
  - **Verdict: feasible for YouTube and Netflix** (a shipping product beats inference), **unproven for Prime**.
  - Reliability depends on an anchor step and on pacing. Both researchers agree there's no focus feedback.
- **Is the Lounge API "working on Tizen"?**
  - For: the iSponsorBlockTV README, and MankhongGarden's 2026-10-27 test.
  - Against: #508 (one report, 2026-10-17; the official YouTube app also failed, so it looks like a Google-side or per-device lockout, not a protocol change).
  - **Verdict: works today on most Tizen TVs, with real and recurring breakage risk.** It is acceptable as an optional enhancement; it should not be the only YouTube path.
- **The smartest-tv table versus code and field tests.**
  - The table claims YouTube "search-string via DIAL ✅" and "Netflix metaTag honored ≤ Tizen 8".
  - Its own code builds only `v=`; its table is self-labelled anecdotal; and a dated field test plus issue #20 on this exact model line show Netflix stopping at the profile picker.
  - **The field tests and code win.**
- **The smartest-tv #6 "SmartThings search pass-through" report.** One user, a Tizen 9 S90F, no payload. I treat it as an unverified lead, not evidence.

## Where each side overreached

- **Optimist:**
  - The 0.3 s per key and 15–25 s timing is unsourced; the reference library uses 1 s.
  - The round-1 Netflix `m=http://api.netflix.com/catalog/titles/...` deep link turned out to only open the app.
  - Recommending Lounge first underweighted its internet, API-key and ToS costs, and #508.
  - The Prime `gti` result is from an on-TV app on Tizen 9. Carrying it over to `ed.apps.launch` from a phone on Tizen 7 is inference.
- **Pessimist:**
  - Round 1 framed grid typing as speculative; a shipping product already does it for two of the three apps.
  - Round 1 also suggested YouTube and Netflix might raise `imeStart`; it later withdrew that, correctly.
  - #508 is one reporter with unnamed models, and it's presented as broader than the evidence shows.
  - The YouTube Data API quota numbers came from secondary blogs, not Google's quota page.
  - Region and A/B layout variation is plausible but undocumented.
- **Evidence-age flags:**
  - The Samsung EU forum and SmartThings community keyboard threads are 2019–2021 and only supporting evidence.
  - The xdavidhu gist is 2021, on an unknown model. The protocol is still current per pyytlounge and iSponsorBlockTV, but the exact endpoints should be checked on this TV.

## Open questions & what would resolve them

1. **Each app's keyboard layout, starting focus and wrap behaviour on this TV.**
   - Resolve with an attended session using the physical remote: open each app's search and record the grid (rows, columns, special keys), where focus starts, whether focus wraps at the edges, and where Left/Up go off the grid (for example, Netflix's left navigation rail).
   - Repeat after closing and reopening search, to see whether focus is remembered.
2. **A safe pace for `SendRemoteKey` on this TV.** Resolve with an attended run that types a known string at 300, 500 and 800 ms and checks for dropped keys.
3. **Whether Prime's grid can be typed reliably at all.** Same attended capture as item 1.
4. **Whether `ed.apps.launch` `metaTag` `{"values":"<gti>"}` opens a Prime title on Tizen 7.** One attended launch. Low priority, since it isn't search.
5. **Whether Lounge pairing works on this CU8000.** Resolve with an attended pair, then `setPlaylist` of a known video id. This only matters if the YouTube enhancement is approved.

## Concrete payloads (for reference; none verified on this TV)

- Deep-link launch over the websocket (samsungtvws `remote.py`):
  `{"method":"ms.channel.emit","params":{"event":"ed.apps.launch","to":"host","data":{"appId":"<id>","action_type":"DEEP_LINK","metaTag":"<payload>"}}}`
- Netflix DIAL (ignored for search and titles in 2026 tests):
  `POST http://<tv>:8080/ws/apps/Netflix`, body `m=https://www.netflix.com/search?q=<title>&source_type=4`
- YouTube DIAL (`v=` ignored on Tizen 9):
  `POST http://<tv>:8080/ws/apps/YouTube`, header `Origin: https://www.youtube.com`, body `v=<videoId>`
- Lounge setPlaylist (via pyytlounge `play_video`):
  form POST to the `bc/bind` session with `req0__sc=setPlaylist` and a `videoId` field.
- Prime title, on-TV app control (Tizen 9):
  `PAYLOAD = {"values":"<gti>"}`, operation `http://samsung.com/appcontrol/operation/eden_resume`

## Ranked recommendation for Household Remote

1. **Build first: grid typing for Netflix and YouTube (option a), anchored.**
   - **Layout table:** one per app, captured by hand on this TV, stored as data rather than code so a recapture is a data edit.
   - **Text:** normalise input to the grid's character set (lower-case a–z, 0–9, space; drop punctuation).
   - **Anchor:** before typing, the phone shows "put the highlight on A" (Netflix) or "on the top-left key" (YouTube), the way makeevapps does it. Prefer that over automatic corner-seeking: blind Up and Left presses can leave the grid, for example onto Netflix's left navigation rail.
   - **Pacing:** configurable, defaulting to the slowest rate that passed the attended test.
   - **Type fewer letters:** all three apps search as you type, so default to sending the first few words or about 8 letters ("honey i"), then hand over to the D-pad to pick a result. That halves both the key count and the risk.
   - Size: a light, contained feature (one engine plus per-app layout data).
2. **Second: Prime Video on the same engine, marked experimental.** Only after its grid is captured and an attended test passes.
3. **Keep: option (b) `SendInputString` on `imeStart`** as the automatic path wherever Samsung's keyboard appears. It's already built.
4. **Later, optional: YouTube "search on phone, play on TV" via the Lounge API.** This needs an owner decision first: runtime internet calls plus a Data API key, or ToS-grey InnerTube. It also needs a fallback to grid typing when pairing breaks (#508).
5. **Skip:**
   - search deep links for Netflix, YouTube and Prime (no evidence; tested negative)
   - Netflix title deep links (profile picker)
   - YouTube DIAL `v=`
   - the SmartThings keyboard and API
   - voice and Bixby
   - `ed.edenApp.get` / `ed.installedApp.get`
   - option (c), dropping the feature.

**Cheapest attended test (about 15 minutes, no code):**

1. On the CU8000, open Netflix search with the physical remote. Note where focus starts, the grid shape, and what Left/Up do at the edges. Repeat for YouTube and Prime.
2. Optionally install makeevapps' "Remote for Samsung TV" on a phone and try its Netflix and YouTube modes on this TV. That is a zero-code confirmation that grid typing works on this model and firmware.
3. Then a single attended run of a hard-coded key sequence for "hon" at 500 ms from a debug button.

Per the repo rules, never run this from automated verification.

## Sources

- makeevapps, "How to Type Faster on Samsung TV", 2026-06-04: https://makeevapps.com/blog/how-to-type-faster-on-samsung-tv/
- MankhongGarden, samsung-tizen-tv-home-notes (DU7000, Tizen 9), 2026-10-21 to 2026-10-27: https://github.com/MankhongGarden/samsung-tizen-tv-home-notes
- smartest-tv #20 (UE55CU8000), 2026-09-10 to 2026-10-24: https://github.com/hybirdss/smartest-tv/issues/20
- smartest-tv #8, 2026-04-25 to 2026-10-01: https://github.com/hybirdss/smartest-tv/issues/8
- smartest-tv #6 (textninja, S90F), 2026-04-24: https://github.com/hybirdss/smartest-tv/issues/6
- smartest-tv deep-link support table (anecdotal): https://raw.githubusercontent.com/hybirdss/smartest-tv/HEAD/docs/reference/deep-link-support.md
- smartest-tv `dial.py`: https://raw.githubusercontent.com/hybirdss/smartest-tv/HEAD/src/smartest_tv/_engine/dial.py
- iSponsorBlockTV README: https://github.com/dmunozv04/iSponsorBlockTV
- iSponsorBlockTV #508, 2026-10-17: https://github.com/dmunozv04/iSponsorBlockTV/issues/508
- pyytlounge docs: https://pyytlounge.readthedocs.io/en/latest/pyytlounge.html
- ytlounge on PyPI: https://pypi.org/project/ytlounge/
- xdavidhu Lounge pairing gist, 2021-04-03 (older model): https://gist.github.com/xdavidhu/cb87f926924bc174e6073b9f0b9d764e
- samsungtvws (`remote.py`, `connection.py`; PyPI release 2026-05-28): https://github.com/xchwarze/samsung-tv-ws-api
- SamMobile, updated 2026-02-05: https://www.sammobile.com/news/samsung-already-fixed-tv-typing-but-app-developers-dont-care/
- Samsung support, YouTube keyboard (updated Dec 2023): https://ushl.samsung.com/py/support/tv-audio-video/the-keyboard-compatible-with-my-smart-tv-does-not-work-with-the-youtube-application
- Samsung EU forum, Prime keyboard (2020, older): https://eu.community.samsung.com/t5/tv/wireless-keyboard-not-working-in-amazon-prime/m-p/1828389
- Roku community, Prime keyboard (undated): https://community.roku.com/t5/Roku-setup/Roku-iOS-app-for-remote-problems-with-searching-I-m-Prime/m-p/977527
- Home Assistant forum, Samsung key delay: https://community.home-assistant.io/t/improving-samsung-tv-control/151922?page=4
- Samsung developer forum, voice: https://forum.developer.samsung.com/t/samsung-tv-remote-voice-control-library/13488
- SmartThings community, launch app API: https://community.smartthings.com/t/api-call-tv-app/247068
- TizenTube #317 (`youtube://search?query=` on Android; not Tizen stock), 2025-12 to 2026-10: https://github.com/reisxd/TizenTube/issues/317
- Cobalt `CobaltActivity.java` (deep-link pass-through): https://github.com/youtube/cobalt
- gigazine, Netflix new TV UI, 2025-05-08: https://gigazine.net/gsc_news/en/20250508-netflix-new-tv-experience-featuring-enhanced-design/
- Household test log: `docs/items/dk-811e7991.md` (v0.2.5 and v0.2.7 reports)
