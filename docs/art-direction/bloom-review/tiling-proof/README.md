# Native material repeat test — draft

The alpha.8 implementation was rejected: eight-block illustrated scars read as
pictures on surfaces. The approved B + a little C visual direction still stands;
it must become a genuine small repeatable material, not a large composition.

## Research applied here

- [Cohen, Shade, Hiller and Deussen, Wang Tiles for Image and Texture Generation (2003)](https://www.cs.ucf.edu/courses/cap6412/spr2003/2003/cohen.pdf): matching edge constraints support continuous tile arrangements; corner constraints handle features spanning multiple edges. The proof below uses a small two-state corner-constrained family, not the full paper's image-synthesis method.
- [Microsoft Minecraft resource-pack tutorial](https://learn.microsoft.com/en-us/minecraft/creator/documents/resourcepack?view=minecraft-bedrock-stable): demonstrates creating block textures at 16×16. This is a reference for native texture scale only; its Bedrock pack instructions do not apply to this NeoForge project.
- [Blob tileset explanation, original Guy Walker material mirrored by BorisTheBrave](https://www.boristhebrave.com/permanent/24/06/cr31/stagecast/wang/blob.html): explains the 47-tile edge/corner subset for coverage boundaries. That boundary treatment is distinct from making an interior material tileable; it is not implemented in this proof.

## Actual files

`generate.py` authors native 16×16 RGBA samples. No large illustration is cropped
or stretched over the terrain. Sixteen corner-label configurations, each with
two interior variants, form 32 material tiles. Shared world-corner labels select
compatible neighbors. Variant detail has exactly zero influence on borders.
The resin sample modifies only a few interior pixels and preserves its borders.

- `repeat-proof.png`: nearest-neighbor review board. Left is one 16×16 tile
  repeated 8×8. Right is a compatible mixed 8×8 arrangement, with two rare resin
  samples. Plain neutral gray underlay, no scene lighting or perspective.
- `corner-compatible-tiles.png`: the 32 native samples, packed without rescaling.
- `grain-0-16.png` through `grain-3-16.png`: selected native samples.
- `resin-16.png`: optional interior-only amber accent.
- `same-tile-8x8.png`, `mixed-tiles-8x8.png`: raw 128×128 stress-test outputs.

Running `generate.py` checks all 512 compatible horizontal/vertical RGBA edge
pairings. These checks prove edge compatibility, not visual quality or freedom
from recognizable repetition. The single-tile repeat deliberately exposes its
periodicity. The mixed material still needs artistic review against the approved
heartwood direction; these are technical drafts, not final assets.

An earlier draft with identical decorated borders was rejected during this
review because its repeated motifs remained obvious. The current files replace
that draft. The player subsequently requested an in-game trial. The working tree
now installs the same 32 native materials, with 32 matching resin variants, in a
128×128 atlas. Each face selects one tile from shared world-corner labels. The
development client is restarted for this trial; the staged alpha package remains
unchanged pending visual review. This authorizes testing, not declaring the art
finished or approved. The restarted client was verified in the disposable
`MCP-Debug-Test-20261029` save: floor and wall show the native repeatable material,
not the eight-block scars. The installed 128×128 atlas passed all 2,048 compatible
RGBA edge pairings; build, 143 unit tests and the full isolated GameTest suite
passed. The client remains open for player review. Trial changes are uncommitted.
