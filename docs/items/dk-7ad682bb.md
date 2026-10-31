---
id: dk-7ad682bb
type: task
created: 2026-10-30
status: todo
since: 2026-10-30
area: remote
priority: P2
rank: zzzzt
parent:
fixes: []
blocked_by: []
relates: []
---
# Grid typing seams: keymap into layout, explicit pacing, single keyboard-open owner


Source: v0.2.8 push review (componentization, abstraction and topology lenses, 2026-10-30), P2.

- The grid keyboard vocabulary is hard-coded in GridTyping (the start cell label a, the labels space and backspace, the character-to-cell mapping, KEY_ENTER as OK, and the direction order that sets the BFS tie-break). Direction: move these into GridLayout or a small GridKeymap, so a per-app layout needs no planner change.
- Pacing is inferred from GridPress.target in KeyDispatcher. Direction: use an explicit paced flag on the entry, and give the 8192 grid queue cap a named constant next to CONTROL_LIMIT. Let the queue accept its own entry type rather than the planner GridPress.
- TypingInput.opened and RemoteState.keyboardVisible are two flags for the same fact, and the VM re-derives the open-or-upgrade transitions. Direction: have TypingInput expose onTvIme(active) and onManualOpen(), and have the VM mirror the result.
- Tree: main remote/ holds 10 files, test remote/ holds 12 (at the cap), and ui/ has 10 children. Direction: nest remote/grid/ (main and test) with the next grid file, or split the test recovery files; group the ui composition-root files if more UI areas arrive.

Acceptance: no behaviour change; the fast gate passes.
