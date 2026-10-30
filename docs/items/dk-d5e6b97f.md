---
id: dk-d5e6b97f
type: bug
created: 2026-10-28
status: todo
since: 2026-10-28
area: release
priority: P2
rank: zzzn
parent:
fixes: []
blocked_by: []
relates: []
---
# publish.mjs dies silently on some runs; sign-and-publish needs clean tree and hand-fed IDs

v0.2.2 signing, 2026-10-28:
- Attempt 1 stopped with 'Check out the release commit in a clean worktree before signing', because untracked docs/items files were present. The check could ignore paths outside the build (docs/items), or name the offending files.
- Attempt 2: publish.mjs logged only start and argv, then the parent reported STOPPED about 65 s later. There was no error line or end line in the publish log, so the cause is unknown.
- Attempt 3 succeeded but took 10.5 min, against about 5 min for v0.2.1.
- Each run still needs -KeyDocument, -PasswordReference and -AndroidSdk passed by hand; see dk-0839b342, which asks for resolving 1Password items by title.
Fix: always write an end/error line (catch unhandled rejections and child exits), add per-stage timing to the log, and default -AndroidSdk to %LOCALAPPDATA%\Android\Sdk.

v0.2.4 signing, 2026-10-30: when 1Password was locked, the first attempt stopped with a clear message (desktop app connection timed out). In the second attempt the key document was retrieved, but the password read hit an authorization timeout, and the script then stopped with "You cannot call a method on a null-valued expression" instead of naming the 1Password approval timeout. The owner had to approve two sequential prompts, one for the document and one for the password. Fix: check the op result before using it, and report the timeout plainly; consider a single op run or inject so that one approval covers both. The third attempt succeeded in about 4 minutes.
