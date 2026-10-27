# Android distribution

Build with the fast gate in [VERIFY.md](VERIFY.md). `src/android/app/build/outputs/apk/{remote,owner}/debug/` contains installable debug APKs; corresponding `release/` folders contain unsigned APKs. AABs are under `outputs/bundle/{remote,owner}Release/`. Debug packages have `.debug` suffix and do not accept production updates. Remote and owner are separate packages and keep separate phone credentials.

## Attended owner steps

No production key exists yet. Do not use disposable smoke output. With installed `op`, `gh`, JDK and Android SDK build-tools 35.0.0:

```powershell
powershell -NoProfile -File scripts/release/provision.ps1 -DryRun
powershell -NoProfile -File scripts/release/provision.ps1 -Vault Personal
```

Provisioning creates a dedicated PKCS12 key and password in 1Password, removes the local key, and records only the public certificate SHA-256 in `src/android/releases/signing-certificate.json`. Keep the two private item references in private owner records. Commit the fingerprint before tagging. Never replace it to fix an update error. Preserve access to both items; losing this key prevents in-place updates.

Run `powershell -NoProfile -File scripts/release/setup-releases.ps1` (supports `-DryRun`) to create or verify the **public**, initially empty `dirtyredz/household-remote-releases` repository. Source remains private. Owner publishes a version tag in the source repository; `.github/workflows/android-release.yml` gates both flavors, builds unsigned APK/AAB, uploads a CI artifact and creates a private draft. Version codes use that workflow's monotonically increasing run number; retries retain the same code. Do not reset the workflow counter or publish older runs after newer ones.

On a clean checkout of the exact release commit, run:

```powershell
powershell -NoProfile -File scripts/release/sign-and-publish.ps1 -DryRun
powershell -NoProfile -File scripts/release/sign-and-publish.ps1 -Tag v0.1.0 -RunId <successful-run-id> -KeyDocument <private-document-id> -PasswordReference op://Personal/<private-password-id>/password -AndroidSdk <installed-sdk>
```

The script checks source repository, workflow path/event/success, tag commit, clean local commit, CI artifact versus private draft provenance and file hashes. It verifies APK package/version, signs both APKs/AABs locally, verifies the permanent signer, uploads only four signed packages plus `update.json` and checksums to a public **draft**, downloads and compares every byte digest, then publishes last. Existing public tags are never overwritten. Failure after draft creation leaves that draft unpublished; inspect and remove that draft before retrying. Failure before draft creation has no public effect. No unattended signing or Worker credentials exist.

The app manually reads the public latest release, validates flavor/package/version and bounded digest-checked download, compares the archive's signer with its installed signer, and delegates installation to Android. Unknown-source permission denial leaves the update pending; retry the install button after granting it. No anonymous access to the private repo is attempted.

## Acceptance still required

CI publication, actual 1Password provisioning and permanent signing are owner-only and not exercised locally. On a phone, install two successive signed releases and confirm pairing survives. Reject wrong signer/flavor and downgrade packages. Test cancellation, unknown-source permission denial, backgrounding and installer return. Offline JVM contract tests and disposable signing smoke are not a substitute for Android package-manager acceptance.
