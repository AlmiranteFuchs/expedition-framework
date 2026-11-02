# Resonance power — alpha.14

Resonance is the tension between a dormant heartbeat and the Stillness holding
it short of waking. It uses the normal FE interface at a 1:1 transport ratio;
there is no incompatible second wire network or required energy library.

| Source | Operating condition | Output | Upkeep |
| --- | --- | --- | --- |
| Stillness Resonator | Sheltered Overworld or liberated ground | 20 R/t, shared locally | No ongoing fuel |
| Living-heart Engine | A captured heart in closed containment | Up to 80 R/t | One essence per 600 running ticks |
| Severance Cradle | An installed core attuned to proven liberation | 120 R/t | No fuel |

All banks hold 16000 R. Generator outputs distribute a total budget fairly over
accepting faces; empty banks cannot starve the other neighbors. Receivers accept
up to 80 FE/t; generators can push buffered energy up to 160 per tick. Redstone
pauses production, while stored power remains available. A full bank pauses
living-heart upkeep. Unfueled containment simply goes dormant.

## Starter workshop

Four dormant flakes craft a Dormant Knot. Craft the Stillness Resonator around
it with vanilla iron, copper and redstone. The knot is built into the instrument;
it does not consume raw flakes. A neighboring condenser uses one flake and 4000 R
to make one essence over ten seconds. Its sole starter source supplies that rate.
The journal and assay kit remain available before refined essence.

Resonators within six blocks share their field. Two nearby collectors each draw
10 R/t; four each draw 5. Give them at least seven blocks between centers for
independent output. Twenty-one crowded knots cannot produce a usable current.
This is a local field limit, not a timer that consumes home protection.

A dormant collector inside an open Stillness breach stops collecting because its
quiet field is absent. Stored power can still flow. Use a living-heart engine,
a severed core, or energy brought from outside for sustained experiments.

## Captured heartbeat

Recover a portable assimilation heart from a frontier colony. The Living-heart
Engine recipe incorporates that heart, a resonator and refined essence into a
closed chamber. Feed essence through its slot or an item pipe. It has no automatic
world breach, outbreak or failure explosion. Opening the world's protection is
still a separate deliberate condenser/sigil action.

One essence can fund up to 48000 R at full draw. Unused capacity and remaining
upkeep persist in the block entity. Breaking a machine does not refund already
burned fuel or preserve its energy as an item battery.

## Power earned by liberation

Craft an Unattuned Severance Core, then use it on a terminal in a dimension with
an accepted bound-guardian victory AND successful extraction. This attunes the
actual held item to that dimension. Install it in a Severance Cradle. The core
can travel home or into a new frontier; its source's proven liberation remains
necessary for new generation.

Proof is world-scoped in the same SavedData as the ledger and cleared flags.
A normal completion count, admin cleared mark, wandering hunting echo or dimension
string copied into an item cannot provide it. Core generation rechecks server
proof. Resetting a campaign stops new generation without deleting already stored
power. Historical completed boss trophies can migrate known old victories; missing
or unknown history fails closed. Binding creates no free item or split inventory
reward transaction, so full/offline inventories do not lose the unlock.

## Art and compatibility

The old dynamo registry id is preserved for placed worlds and recipe compatibility,
but its display name and model are now Stillness Resonator. Existing stored power
and unburnt inventory remain. The fork holds an animated suspended knot and small
flexing ribs, rather than a flywheel. Living chambers pulse; the severed remnant
is calmer. All moving parts use normal world lighting and client render geometry,
not terrain edits or per-frame block-state changes.

Wood sides and end grain have separate textures. Face UVs align grain to each
beam, including angled members; metal bands and chamber glass have their own
mapping. Calm material fields replace generic stamped panels. Native textures
remain 16x16, the palette stays pale heartwood/copper/charcoal/amber, and approved
Bloom surface atlases and editable art exports are untouched.

Standard NeoForge energy and item capabilities support other FE generators,
storage and cables. This is not a claim of Create kinetic-network integration.
No new runtime dependency was added.

## Verification

Build/alphaBundle, 168 unit tests and 148 core GameTests pass (151 reported with
three optional biome probes). Six resonance interaction tests cover field sharing,
paid containment, full-bank behavior, real terminal binding, proof/save/reset,
bootstrap recipes and fair multi-face output. The existing canceled-death guardian
regression now checks that power proof is awarded only after extraction.
