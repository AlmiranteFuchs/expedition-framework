# Stillness field engineering

## Current resonance sources (alpha.14)

The starter is now a fork-shaped Stillness Resonator with a suspended knot and
slowly flexing ribs. A contained living heart and a severed-core cradle extend
the same material language. The flywheel design below is historical.

Wood sides/end grain have separate textures and face UVs follow each beam axis,
including angled supports. Calm metal fields, wrapped contacts and specific glass
UVs replace generic stamped panels. The palette and native 16px resolution remain.
All knot motion is client-only, uses world lighting and does not edit terrain.

Actual client previews:

- `power-family-alpha14.png`: source family and chart lectern. The displayed
  severance core is unattuned; its output correctly remains locked.
- `resonator-close-alpha14.png` / `resonator-motion-alpha14.png`: fixed-camera
  motion check; 141 knot-region pixels changed between the sampled frames.
- `resonator-ui-alpha14.png`: native 20 Resonance/t source UI.
- `heart-engine-ui-alpha14.png`: fueled 80 Resonance/t containment UI.

The source generator is still `tools/stillness_art.py`. These refinements were
authored in the editable native generator; no new AI bitmap is pasted onto the
models. See `docs/resonance-power.md` for power and progression rules.

## Alpha.13 concept history

The approved palette/material study is `field-engineering-concept.png`.
The adopted open-frame direction is `open-frame-concept.png`; its real native
models are shown in `in-game-alpha13.png`. The first board is not used as a
texture. None of the generated reference image is pasted onto game blocks.

Materials: pale heartwood, warm bone, charcoal iron, worn copper and small amber
resin highlights. Actual textures remain native 16x16 pixel art, with deliberate
clusters. `tools/stillness_art.py` authors the item sprites, part textures, GUI
and separate cuboid models. The documented master texture generator calls it
last. Approved Bloom surface atlases and user art exports remain untouched.

The dynamo has an exposed octagonal flywheel ahead of an open frame and a banded
coil. The condenser has a glass chamber, offset pipe and separate collection
vial. The terminal is a tilted chart lectern; the diagram appears on its chart
surface. Geometry and selection/collision share native model data, including
rotations and visible open space. Mechanical geometry is currently static;
processing is shown through the synchronized GUI and sound. Spinning assemblies
are a future visual-polish opportunity, not a claimed feature.

Create supplies the construction-language inspiration, not copied assets or a
runtime dependency. Power interoperability is through NeoForge FE, not a claim
of Create kinetic coupling. References:

- [Create model source](https://github.com/Creators-of-Create/Create/blob/mc1.21.1%2Fdev/src%2Fmain%2Fresources%2Fassets%2Fcreate%2Fmodels%2Fblock%2Fwater_wheel%2Fblock.json)
- [NeoForge 1.21.1 capabilities](https://docs.neoforged.net/docs/1.21.1/inventories/capabilities/)
- [NeoForge biome modifiers](https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/)

## Final concept prompt

Built-in image generation edited the first reference board with this prompt:

> Revise this approved Minecraft mod concept sheet. Preserve its beautiful cream/pale heartwood/charcoal/tarnished copper/tiny amber palette, archaeological craftsmanship and crisp Minecraft pixel-art material detail. MAIN CHANGE: the machines must NOT be cubes with decorated faces. Redesign their actual silhouettes using the functional, exposed mechanical construction language of Create mod machinery, while retaining our own pale heartwood and amber identity. Show three hero objects, each with a different unmistakable shape: STILLNESS DYNAMO is a low wooden-and-metal skid on small feet, with a prominent exposed vertical copper flywheel, axle, crank/gear and compact offset generator coil, abundant negative space; ESSENCE CONDENSER is a tall narrow glass distillation chamber with pale protective ribs, copper collar, offset curved blocky pipe and a small output spout above a collection vial, mounted on an open stepped base; EXPEDITION TERMINAL is an inclined parchment chart lectern on two carved pale timber legs, with a small amber pointer and copper hinge/control on one side. Clearly differentiated silhouettes fitting approximately one Minecraft block footprint, built from Minecraft-appropriate simple blocky parts, NO enclosing full cube casing, NO elaborate curving Victorian filigree, NO neon screens, no green vines. Include small front/side/back silhouettes showing their open construction. Keep the lower row approved handheld assay kit, attunement sigil, dormant flakes and amber essence designs, reducing their size if needed to give machines room. Beautiful professional design sheet titled 'THE STILLNESS — OPEN-FRAME FIELD MACHINES'. Reference/concept only, not final game textures. Show visible physical processes, light asymmetry, exposed copper parts and breathing space between structural members.
