---
id: dk-99aecb16
type: task
created: 2026-11-22
status: todo
since: 2026-11-22
area:
priority: P2
rank: zzzzzz
parent:
fixes: []
blocked_by: []
relates: []
---
# Agent-run Android signing for household-remote (broker signer + key in an agent-visible vault)

Signing v0.2.10 on 2026-11-22 needed the owner: sign-and-publish.ps1 uses the op CLI (blocked for agents by op-guard, owner ruling 2026-11-04) and the key items ('Household Remote signing key' DOCUMENT, 'Household Remote signing password') live in the Personal vault, invisible to the broker service account. mcp sign_android_release is hard-wired to Command Deck. Fix: add a generic broker signer (like CD's sign_android_release, parameterised by repo/tag/key item, verifying the draft release and commit) and move the household-remote key to an agent-visible vault, keeping the same certificate (never replace it; see docs/ANDROID-DISTRIBUTION.md), so no owner step is needed. Related: dk-0839b342 (resolve items by title), dk-d5e6b97f.
