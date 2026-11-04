# Player-authored Stillwood Hollow

F completed this build in the development world and explicitly chose the mod's
pale bark/amber palette while retaining the exact shape.

- `stillwood_player_source.nbt`: native Minecraft structure, original palette and
  complete block states. 930 non-air blocks; size 17 × 31 × 17 including deep roots.
- `source-blocks.json`: exact authenticated MCP snapshot, with log axes, leaf
  properties and vine attachments. Temporary moss platform/structure block excluded.
- `metadata.json`: captured world origin, entrance anchor and the two orange-glass
  portal markers. The source includes 169 blocks below the workshop floor.
- `original-in-game.png`: original build; `pale-preview-in-game.png`: separate
  protected palette preview. The live original was not modified by the import.

The original save box clipped the canopy and excluded the roots; capture used
expanded bounds and verified the result was complete. The native source was
round-trip checked against every captured position and block state. There are no
source block entities. Source materials include Twilight Forest, but the shipped
runtime template uses only the mod's own palette and needs no Twilight dependency.

The native NBT is the authored authority. `tools/recall_tree_layout.py` reads it;
it must never procedurally rebuild or simplify this tree. `tools/verify_authored_tree.py`
checks source hash, every authored voxel, native runtime placement, preserved old
indices and model references. `tools/gen_textures.py` regenerates the derived
models and native protected template. Original log/wood axes and all three faces
of the captured hanging detail are retained in the material mapping.

Future recaptures require an explicit new player build; the capture tool refuses
to overwrite the source unless `--replace` is supplied. Never run the old workshop
preparation function over this build.
