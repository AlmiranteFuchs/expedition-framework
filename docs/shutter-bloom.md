# Shutter Bloom — alpha.17

The colony source is a low rosette of four broad folded bark plates. The user
selected concept 3 or 4; this implementation uses 4's shutters with the dark
layered undersides of 3. It replaces the full cube and six-sided glow shell.

## Readable activity

Five staggered poses fold the unequal plates open, hold briefly, then settle
closed. The colony position supplies a stable phase; no animation progress needs
to be saved. A quiet bamboo-wood click marks opening/closing. Native 16px textures
use long interrupted pale grain outside and dark ribs with sparse amber resin
inside. Emission is confined to a small seam in the central bed. There is no
surrounding halo, flame plume, soul particle or anatomical heartbeat sound.

The block model and its rotation are the source for voxelized selection and
collision in every pose. The shell is non-sturdy and has real open space. Cold
specimens use the same folded form with gray lining and no emission. The Stillness
keeps their orientation when it makes them dormant; essence reactivation preserves
that orientation too. The engine contains a miniature of the same folded specimen,
in its world renderer and its inventory model.

## Colony behavior and compatibility

The old cube allowed layers to seed against its six faces. Bootstrap now examines
the 3×3×3 neighborhood and proposes only coverings against actual sturdy host
surfaces. This handles a source on flat ground, perched on a pillar, or embedded
in an older saved floor. An unsupported source suspended in air produces no tissue.
Subsequent surface walking, wrapping, host seeking and the face-count cap keep
their existing rules. Opening a shutter does not replace the block entity, reset
its colony, or restamp tissue age. Removing it still hibernates supported growth.

Registry ids `assimilation_heart`, `dormant_heart` and `living_heart_engine` stay
stable, as do recipe ids, saved colony links and configuration keys. Player-facing
names are Shutter Bloom, Dormant Bloom and Bloom Engine. The journal describes the
source and its captured use without establishing a new origin for the influence.
Ordinary source placement, essence processing, field preparation and accepted
guardian proof remain the existing progression.

## Interaction fix found during the live check

Vanilla crouched use can bypass a block's interaction handler. The old sigil was a
plain item even though breach opening lived in the block's crouch-only handler.
`AttunementSigilItem.useOn` now handles that fallback on the logical server. If
the block already handled the action, its consuming result prevents a second
toggle. The sigil's terminal attunement and component identity are preserved.

## Reproduce

`tools/bloom_source_art.py` writes six geometry models (five live poses and the
cold pose), their blockstates, native textures and captured item forms. It runs
inside the normal `tools/gen_textures.py` pipeline before inventory GUI framing.
The approved flat Bloom surface atlas is unchanged.

The `expeditions_bloom` namespace covers every pose's shape, real-terrain seeding,
floating-source refusal, ownership/save continuity, dormancy, crouched sigil use
and oriented reactivation. Pure tests check the bounded shutter cycle and stable
phase. See `art-direction/bloom-canker/` for the preceding references and the
selected design sheet; rejected early canker sketches remain labelled as rejected.

## Verified release

Build and alphaBundle pass with 176 unit tests and 159 core GameTests (162
reported with three optional biome probes). Live client checks used only the
MCP-Debug-Test world: a powered, manually opened Stillness breach kept the source
active beside a cold specimen; shutters advanced through their poses, the flat
infection colonized grass, the engine displayed its contained specimen, and real
crouched sigil use opened the field. Open and cold/closed views are saved in
`art-direction/bloom-canker/game-*-alpha17.png`. No missing models or textures
were reported. The controlled demo is at 1165,240,1050 with a cold specimen at
1168,240,1050; its powered breach condenser is at 1161,240,1050.
