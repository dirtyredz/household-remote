---
id: dk-7cb7bf12
type: task
created: 2026-10-26
status: todo
since: 2026-10-26
area:
priority: P2
rank: t
parent:
fixes: []
blocked_by: []
relates: []
---
# Split household import naming and JSON codec

Separate HouseholdImport validation and merge responsibilities from JSON parsing, and replace its publicly exposed Json instance with a narrowly named codec API.
