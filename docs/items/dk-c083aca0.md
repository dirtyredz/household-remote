---
id: dk-c083aca0
type: feature
created: 2026-10-30
status: wip
since: 2026-10-30
area: ui
priority: P1
rank: zw
parent:
fixes: []
blocked_by: []
relates: []
---
# Pink berry-gradient restyle with heart OK button and new launcher icon


Owner decision 2026-10-30 (with household input): restyle the remote with mockup C, the berry-to-purple gradient, and replace the launcher icon with concept 3, a pink heart surrounded by lavender direction arrows on purple. Both mockups were AI-generated in .tmp/ui-redesign/mockups (git-ignored); the build reproduces them in Compose and vector drawables and does not ship the generated raster.

Target colours: background vertical gradient #581B49 to #24133D; surfaces #392548; primary pink #FF80BF; accent lavender #BC9AFF; text #FFF7FC. The power button stays red.

Scope: restyle only. The layout approved in v0.2.3 is unchanged: the power button sits on its own row under the TV dropdown, and the full TV name stays visible. The d-pad centre OK becomes a heart-shaped button labelled OK, keeping the same hit target (at least 48 dp) and accessibility label. The d-pad ring gets a lavender outline and lavender arrows. Back/Home/Source, the rockers, mute and the app buttons use translucent plum surfaces with light text. Disabled and Offline states stay visibly distinct and keep legible contrast.

Icon: an adaptive icon (vector foreground plus background) with an optional monochrome layer, readable at launcher sizes. The repository icon check passes.

Acceptance: the VERIFY.md fast gate passes; the owner confirms on the installed release that the look matches mockup C, the OK button is a heart, and the new icon appears.

Revision 2026-10-30 (household feedback on v0.2.4): no purple anywhere. Use hot pink, not baby or pastel pink, on black. New palette: a solid black background (no gradient), near-black surfaces, and hot pink (around #FF1493 to #FF2D95) for the primary colour, the heart OK, the arrows, the ring and the accents. Lavender and plum are removed from the UI, the theme and the launcher icon. The icon becomes a hot-pink heart with hot-pink arrows on black. Text stays white or near-white, and the power button stays red. Acceptance: the owner confirms on the installed release that no purple remains, the pink reads as hot pink, and the background is black.

Refinement 2026-10-30: a subtle background gradient is acceptable if it runs from black to a dark rose pink. The black stays dominant, there is still no purple, and hot pink remains the accent. Mockups for the final pick are in .tmp/ui-redesign/mockups-v2.
