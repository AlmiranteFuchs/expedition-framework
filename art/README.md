# Expeditions — artist workspace

Everything the mod's visuals are made of, in one editable folder. The game
reads identical files from `src/main/resources/assets/expeditions/` — edit
here, then **push** your work into the game to test it.

```bash
python3 tools/art_sync.py push    # copy your edits into the game
./gradlew runClient               # launch Minecraft with the mod
python3 tools/art_sync.py pull    # refresh art/ if game files changed upstream
```

Inside the game press **F3+T** to hot-reload textures — no restart needed for
most pixel tweaks. New/changed *models* need a world reload (leave & rejoin).

## What's here

| Path | What it is | Edit with |
|---|---|---|
| `textures/block/` | 16×16 block faces | Aseprite / Piskel / any pixel editor |
| `textures/item/` | 16×16 item icons (compass has 32 needle frames `_00`–`_31`) | same |
| `textures/entity/` | mob skins (64×64) + `_glow` emissive layers | pixel editor on the UV map, or Blockbench paint |
| `textures/gui/` | `resonance.png` — the attunement screen | pixel editor |
| `textures/mob_effect/` | effect HUD icons | pixel editor |
| `models/` `blockstates/` | JSON geometry + variant tables | Blockbench (JSON model editor) |
| `entity/rootbound_guardian.bbmodel` | the Guardian's mesh | **Blockbench** |
| `sounds/` | `.ogg` SFX (game events) | any audio editor — Audacity is free |
| `lang/en_us.json` | display names / descriptions | text editor |
| `palettes/` | the official color ramps (.gpl + swatch PNG) | import into your editor |

## The style (two sentences)

Bone is structure, dark cavity is depth, **ember is the only light**. Pale
when dormant, ignited when alive — the Pale Garden duality.

- **Palette:** import `palettes/expeditions.gpl` (Aseprite: *Palette → Import*).
  Ramps: Bone, Flesh, Ember, Leather, Metal — shadows hue-shift toward mauve
  (bone) and deep red-brown (ember), never plain gray.
- **Light:** one source, top-left. 3–5 flat tones per material — band, don't
  gradient. Detail in 2–4 px clusters, never single-pixel noise. Dark colored
  outlines (`#261E18`), never pure black.
- **Size:** all item/block textures are 16×16 RGBA — vanilla-plus resolution.
  Entity textures are 64×64.

## Testing in-game

1. `python3 tools/art_sync.py push`
2. `./gradlew runClient` (needs JDK 21 + a display — see repo `AGENTS.md`)
3. Creative world → `/give @p expeditions:<name>` or place the block.
   The Guardian: `/summon expeditions:rootbound_guardian`

## Honest limitations

- **`entity/rootbound_guardian.bbmodel` is a one-way export.** The game builds
  the Guardian from Java code (`GuardianModel.java`), not from this file —
  use it to redesign/repose/paint, then hand the .bbmodel back and the mesh
  gets ported into code. Regenerate it with
  `python3 tools/export_guardian_bbmodel.py`.
- **Sounds:** `.ogg`, any sample rate. A quieter sound file = a quieter
  sound in-game; the engine does the rest.
- **`lang/en_us.json`:** key-value text. Don't rename keys, just values.
- **`.mcmeta` files** (next to a few textures) drive animation — the `frames`
  strip is vertical; keep the frame count consistent if you resize one.
