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
- `src/android/app/src/main/java/com/dirtyredz/householdremote/`: `inventory` owns JSON inventory and repository merging; `discovery` owns SSDP/REST probes; `security` owns encrypted credentials, digests, and TV certificate trust; `remote` owns foreground sessions and serialized input; `power` owns WOL and identity-checked awake polling; `shortcuts` owns calibrated sequences, persistence, and the steps/text codec; `selfupdate` separates release feeds, verified downloads, package inspection, and installation; `ui` owns Compose/lifecycle and injects the remote controller's collaborators.
- `src/android/app/src/test/`: offline JVM and fake-server tests.
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
