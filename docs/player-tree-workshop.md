# Player-authored Stillwood tree

**Captured and integrated in alpha.22.** The final tree moved north of the starting
copy and extends below its floor. Its complete source is
[here](art-direction/player-tree/README.md), with a native `.nbt` and exact states.
Do not reuse the initial bounds below for future captures of this completed tree.

The user wants to improve/build the tree in Minecraft and have that saved as the
actual structure. Preserve their work; do not regenerate the authoring copy or
replace it with a new procedural tree.

- World: `sp:MCP-Debug-Test-20261029` (disposable development world).
- Editable copy: near **1251, 240, 1052**, separate from the protected demo at 1221.
- Build bounds: **1239,240,1041** through **1263,259,1065** (25 × 20 × 25).
- Structure block: **1238,239,1040**, SAVE mode, name
  `expeditions:stillwood_player`; offsets 1,1,1; size 25,20,25; entities ignored;
  bounding box visible. Floor is outside the saved volume.
- The starting copy uses ordinary birch logs/wood and persistent birch leaves.
  It has no protected expedition markers. Two orange stained-glass blocks mark
  the recessed portal's location; the user may move/change the opening.
- Vanilla building materials were added to the existing Creative inventory.
  Player edits may use other blocks, shapes and states. Preserve those states
  when capturing; map materials deliberately during integration.

When the user says the build is ready, capture it as a native Minecraft structure
(`.nbt`) and keep that as the authored source. The configured structure block can
save it. Confirm the resulting file and dimensions, preserve a source copy, and
then integrate the authored footprint/palette with the existing safe placement,
tracking, recovery and reward gates. The current JSON tree is the previous
procedural implementation, not the authority over the player's new art.

The disposable preview datapack contains `expeditions:prepare_tree_workshop`.
It was used once to lay out the initial copy. Do not run it again after the player
starts editing: its setblock commands would overwrite their work.
