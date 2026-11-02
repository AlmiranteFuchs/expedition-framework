# The Charter — inventory art, alpha.16

The final sprites are native **16×16** pixel drawings, not crops or downscales of
the concept illustration. Their editable source is `tools/item_art.py`, called
last by the existing texture pipeline. Pale bark, warm gray and charcoal define
the silhouettes; copper identifies manufactured parts; amber is a small signal.
Top-left light, hard alpha, interrupted grain and grouped pixels replace soft
glow, random texture and bright orange fills.

`concept-alpha16.png` was made with the built-in image-generation tool as a
design reference. `before-alpha15.png` records the actual preceding sprites.
`native-alpha16.png` enlarges the actual final sprites with nearest-neighbor
sampling. `native-sheet-alpha16.png` is the editable native-resolution sheet,
left-to-right in the same order. `inventory-alpha16.png` shows all 24 registered
items in the running Minecraft client. `compass-alpha16.gif` previews all frames.

## Coverage

| Item | Readable identity |
| --- | --- |
| Field journal | Charcoal leather, pale split-bark inset, page edge and amber clasp |
| Expedition compass | Recessed dark dial, pale carved ring, copper winding tab, turning thorn |
| Field assay kit | Hinged sample case, two ampoules and short lancet |
| Disruption charm | Cord and forked wooden tuning toggle |
| Assimilation essence | Amber resin inside a pale bark rind |
| Expedition artifact | Wrapped cargo parcel, crossed binding and dark wax seal |
| Attunement sigil | Broken triangular ring with a small ember notch |
| Dormant flakes | Three separate thin bark chips |
| Dormant knot | Interrupted concentric grain and a deep cold split |
| Severance core | Dark captured heart in pale prongs and a copper collar |
| Resonance cell | Twin terminals, squared accumulator and three-segment gauge |
| Heartglass lens | Dark amber glass, copper rim and pale split handle |
| Living/dormant layer items | Separate tissue-fragment icons; only living tissue has amber points |
| Ten remaining block items | Existing world models, normalized inventory pose and side lighting |

The ten 3D inventory models are terminal, checkpoint, exit, living/dormant hearts,
resonator, condenser, living-heart engine, severance cradle and field ward. Their
open silhouettes remain intact. World textures, approved Bloom atlases, collider
shapes and the compass's tested hand pose/angle ordering are preserved.

## Reproduce and edit

```
python tools/gen_textures.py
python tools/validate_item_art.py
python tools/preview_items.py
```

The preview tool also writes `build/art-exports/items-alpha16.zip`, containing the
individual native item PNGs, native sheet and editable drawing source. Direct PNG
edits should be ported into the source before regenerating. World block materials
and entity UV sheets are outside this item pass.

Validation covers every registered inventory model, native dimensions, nonempty
sprites, hard alpha, direct texture references and all 32 compass frame parents.
The full build, 172 unit tests and 154 core GameTests passed. The client loaded the
entire family without missing model/texture errors; the debug chest shows every
registered item together at real inventory size.

## Concept prompt

Built-in image generation; opaque dark-charcoal reference board, no API/CLI
fallback. The following prompt produced the saved concept:

> Create a professional concept reference sheet for a Minecraft mod inventory
> pixel-art family, called The Stillness / expeditions. This is a DESIGN GUIDE
> for later hand-authored native 16x16 sprites, not a texture atlas to be
> automatically pasted into Minecraft. Twelve isolated item concepts in a clean
> 4-column by 3-row grid on solid dark warm-charcoal background, ample empty
> spacing, no scene, no wall picture, no scenery. Render every item in strict
> chunky pixel art with a visibly low-resolution native 16x16 design logic,
> nearest-neighbor edges, hard opaque clusters, no gradients or glow halos, no
> antialiasing, top-left lighting, economical 5-shade pale-bone ramp, dark
> warm-gray bark/charcoal, restrained weathered copper, tiny dark amber eyes.
> Inspired by Minecraft's Creaking lore: pale split heartwood holding a captive
> amber heartbeat, no green vines, no human anatomy, no realistic fingers.
> Distinct silhouettes, quiet material surfaces, strong readability at tiny
> inventory size. Left to right, top to bottom: (1) thick charcoal leather field
> journal with pale split-bark cover inset, small amber clasp and readable
> layered page edge, NOT green; (2) hand-carved pale heartwood expedition compass
> with dark recessed dial, short copper winding tab and thin amber thorn needle,
> no red vanilla compass; (3) field assay kit: compact open charcoal case with
> two pale glass ampoules, one tiny red blood sample, copper latch, clearly
> medical instrument NOT weapon; (4) disruption charm: cord necklace holding a
> forked pale wooden tuning toggle around a tiny amber gap, asymmetrical NOT a
> gemstone pendant; (5) assimilation essence: small dark amber resin shard with
> pale bark rind, NOT fire or a bright glowing potion; (6) expedition cargo
> artifact: rectangular wax-sealed wrapped sample parcel with crossed copper
> binding and dark red wax seal, clearly cargo NOT battery; (7) attunement sigil:
> carved bone-colored triangular broken ring with copper bindings and an ember
> notch, NO cross or sunburst; (8) dormant flakes: three thin irregular pale bark
> chips, dark undersides and top-left edges; (9) dormant knot: gnarled pale fossil
> seed with concentric interrupted growth rings and a deep dark split, no glow;
> (10) severance core: charcoal captive heart suspended in three pale prongs and
> thin copper collar, only one amber spark; (11) resonance cell: enclosed stout
> copper-and-pale wood accumulator, square shoulders with two terminals and a
> recessed three-segment amber gauge, distinct from parcel and core; (12)
> Heartglass Lens: small tilted copper rim with a dark translucent amber glass
> oval, a pale forked wooden handle, tiny reflection only NOT a bright eye. Use
> subtle small unobtrusive labels beneath each concept if helpful, but focus on
> highly polished coherent Minecraft inventory designs.
