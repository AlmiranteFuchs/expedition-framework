# Bloom surface art — approval draft

Status: B with a little C approved by the player. Implemented as world-anchored,
overlapping multi-block heartwood scars in alpha.8. The images remain art guides,
not installed runtime textures.

## Diagnosis

Visible texture repetition (tiling artifacts): although edge connections match, nearly every occupied block has a similarly sized pale knot and amber centre. Those repeated high-contrast landmarks reveal the block grid. More local variants alone will not fix a composition that still repeats once per face. This is our assessment of the current mod, not a quotation from a source.

## Research

- [NVIDIA GPU Gems 2, Chapter 12: Tile-Based Texture Mapping](https://developer.nvidia.com/gpugems/gpugems2/part-ii-shading-lighting-and-shadows/chapter-12-tile-based-texture-mapping): discusses nonperiodic arrangements and matching edge patterns to reduce visual repetition while maintaining continuity. The applicable principle is coordinated variation across a surface; no GPU dependency or shader implementation is proposed here.
- [Mojang: Minecraft Snapshot 24w44a](https://www.minecraft.net/en-us/article/minecraft-snapshot-24w44a): resin appears on pale oak near the connected creaking heart; pale oak, heart and resin provide the visual/lore reference. Our terrain assimilation is original mod fiction, not a claim about vanilla behavior. The mod stays on Minecraft 1.21.1.

## Shared art rules

Flat surface art only. Preserve host grain. Pale wood, warm ash and sparse amber; no green vines, raised geometry, fingers or grass sprites. Irregular colonies cross several block faces, with quiet areas, partial fronts and dense mature areas. Avoid a central emblem in every block, regular spacing and a uniform bright outline. Amber should be rare and clustered near mature wounds, not placed on every face. Large-scale variation must work alongside pixel-level detail. Future implementation needs connected variants coordinated across block boundaries; randomly rotating connected UVs would break continuity.

Palette: charcoal #393632, ash #77716a, warm gray #aaa499, pale oak #c6c1b3, chalk #e8e0d1, amber #be792d / #e3a348.

## Candidates

A — Pale Encroachment: irregular bleaching and torn pale grain with exposed host between patches. Recommended base direction.

B — Heartwood Scars: broader broken bark grain. Useful as a restrained secondary detail within A; its strong directional strips should not become a repeated pattern.

C — Resin Faults: sparse branching amber fractures with partial bleaching. Alternative with a stronger amber focal point; keep it rare to avoid a lava/circuit appearance.

Approved refinement: B with a little of C. The [hybrid sketch](heartwood-resin-hybrid-01.png) keeps broken pale heartwood grain dominant, with rare short amber wounds in mature tissue. Uneven widths, interrupted grain and quiet host gaps guide the runtime art. The implementation uses native pixel art in tools/bloom_surface_art.py, rather than shrinking or installing the generated concept sheet.

## Deliverables and limitations

- [Concept directions](concept-directions-01.png): environmental art-direction sheet.
- [2D texture studies](texture-studies-01.png): flat comparative sketches, including quiet/frontier/mature studies.
- [Generation prompts](prompts.md): exact prompts, provenance and reference relationship.

Both images were generated using the built-in image_gen tool. They are visual guides, not pixel-exact, seamless, install-ready 16px assets or a validated atlas. The gray host in the sketch is illustrative; eventual overlay assets must preserve the real in-game host. Do not ship these sheets as textures. No build or gameplay tests were necessary for review-only documents.

