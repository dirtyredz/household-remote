---
id: dk-fcb5ee83
type: task
created: 2026-10-30
status: done
since: 2026-10-30
area: ui
priority: P2
rank: zzzw
parent:
fixes: []
blocked_by: []
relates: []
---
# Name remote surface tokens and one gradient/colour source in ui/theme


Source: v0.2.4 push review (componentization and abstraction lenses, 2026-10-30), P2.

The pink restyle (1d5cbbd) spells the translucent control surface as bare alpha literals: 0.8f in RemoteButton.kt (twice) and the DirectionPad ring background, 0.6f in RemoteRocker, 0.5f for the ring border, and 0.6f for disabled content in RemoteButton. The gradient Brush is built inline in RemoteScreen from two theme vals, and the background and icon colours are repeated across RemoteTheme.kt, res/values/remote_colors.xml and src/android/icons/generate-icons.mjs, kept in step only by comments.

Direction: expose named surface tokens and a single RemoteBackgroundBrush from ui/theme, and have the remote composables consume them. Pick one source for the gradient endpoints and the icon background (remote_colors.xml, or generator-emitted), and reference RemoteBackgroundBottom instead of repeating 0xFF24133D. If a second accent key appears, extract a HeartOkKey or RemoteButton accent variant that owns fill and content colours, instead of the DirectionPad call-site if/else.

Acceptance: retuning a surface alpha or a gradient colour is a one-place edit; the fast gate passes; there is no visual change.
