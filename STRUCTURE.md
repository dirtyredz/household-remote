# Structure

## Layout

- `src/android/`: independent Kotlin/Compose Gradle build; Command Deck pins and wrapper.
- `src/android/app/src/main/java/com/dirtyredz/householdremote/`: `inventory` owns JSON inventory; `discovery` owns SSDP/REST; `security` owns encrypted credentials and TV certificate trust; `remote` owns foreground sessions and serialized input; `power` owns WOL; `shortcuts` owns calibrated sequences; `selfupdate` owns public release validation/install; `ui` owns Compose and lifecycle.
- `src/android/app/src/test/`: offline JVM and fake-server tests.
- `src/android/icons/`: source SVG and deterministic Android vector generator/check.
- `src/household/`: explicit nonsecret manifest export and tests.
- `scripts/release/`: attended provisioning, signing, publication and disposable smoke.
- `scripts/owner/owner-run.ps1`: verbatim owner-run 1.0.0 template.
- `tools/script-run/`: verbatim script-run template and tests from ~/.claude/templates/scripts.
- `.github/workflows/`: unsigned Android CI and draft release jobs.
- `config/`: fictional inventory example only; no household data shipped.
- `docs/`: living maps, verification, distribution, original plan, Docket items.

Place code with its owning responsibility; keep files below approximately 800 lines.
