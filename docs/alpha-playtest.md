# 0.6.0-alpha.13: playable alpha candidate

Minecraft 1.21.1, NeoForge 21.1.x, Java 21. Patchouli 1.21.1-92-NEOFORGE is required
for the guidebook. `gradlew alphaBundle` stages both required jars in `build/alpha`.
Optional MCP tooling is development-only; nothing in the shipped mod depends
on MCP, Aether, Bumblezone, Twilight Forest, or a client automation tool.

## Default survival loop

1. Explore until you find a Bloom colony. Harvest attached growth for essence.
   Its live heart is the source; destroy it when you are finished harvesting.
2. Craft the journal from a book and essence. Collecting essence teaches the
   crafting-book recipes for the journal, terminal and attunement sigil.
3. Build a terminal somewhere you can return to. The journal documents its
   recipe. Read the deployment board's briefing and requirements.
4. Complete Field Survey twice to establish the Overworld campaign under the
   default settings. Each successful extraction earns proof; checkpoint tokens
   are banked even if a later run fails.
5. Complete Deep Salvage: recover its physical cargo and protect its extractor.
   Hold the bound cargo when extracting. Read the journal chapters discovered
   along the way.
6. Deploy Heart of the Thicket after earning the salvage proof and its 25-token
   fee. Reach the final checkpoint, defeat the Rootbound Guardian, then extract.
   Its death alone does not pay or complete the run.
7. Explore other installed dimensions through the terminal. Automatic routes
   use loaded dimension IDs and biome-local mobs where eligible. Terrain that
   cannot safely resolve a route is refused before deployment.

The configurable completion threshold is also an alternative campaign-clear
path. Default gates, costs and thresholds can change through server config or
datapacks; the live terminal is the authority on your world's requirements.

## Controls and feedback

- Compass thorn: direction. Hovering ember: target above/below your elevation.
  Hold use to hear and see its elevation attunement.
- Journal use: Patchouli guidebook. Browse categories, search and bookmark
  entries; Unwritten pages lists the remaining discovery conditions.
  The journal handles guide knowledge and expedition/survey records.
- Field assay kit: use to inspect the last sealed sample; sneak-use draws a
  fresh sample costing one heart, with no knockback and no lethal draw.
  The kit holds the measured infection status and sampling time.
- Terminal: previous/next mission, left/right click to cycle options forward or
  backward, briefing/requirements tabs, page arrows or mouse wheel for long
  notes. The first checkpoint seals the party.
- Guardian: leave the marked ember circle before its strike. Its core takes
  extra damage while it raises its arms. Below half health it alternates sweeps
  with marked bursts under its target. Reloading gives a fresh warning window.

## Verified

- Build and 155 pure-Java unit tests.
- 120 core GameTests: lifecycle, reward atomicity, party cleanup, dimensions,
  journal save/load and death copy, survival crafting, terminal defaults,
  required route length, guardian binding/death veto/save-load/Peaceful toggle,
  reused portals ignoring ended/sealed parties, remote portal sealing at home,
  read-only diagnostic snapshots, and weighted body-fit retries for
  oversized biome mobs in enclosed terrain. Three new ownership tests cover
  wandering mobs, saved entities/chunk unloads, and slime descendants.
- Four presentation interaction tests cover real terminal placement/rotation,
  portal rim/aperture ray selection and empty collision, guide discovery
  migration, and registry validation of every guide icon, recipe and entity.
- Four additional tests cover reciprocal surface connections on all six face
  orientations, concave/convex corner paths, nonlethal assay refusal and migration
  away from the old nausea/darkness effects. The journal/assay integration test
  verifies separate jobs, paid samples, stable snapshots and no sampling knockback.
- Bloom growth is sparse infection over the host: pale rootlets occupy roughly
  a third of a face, with separate ember knots. The original block gradually
  loses color over a minute while retaining its grain and contrast. Dead hearts
  leave cold scars. There is no raised membrane slab or full beige tile.
  Atlas edge contracts were checked across all 64 tiles. Branches use shared
  neighbour decisions instead of independent face rotations or an all-four-way grid.
- Client: compass face/elevation/attunement, guide pages/recipes/field records,
  deployment-board layout, guardian geometry/emissive art,
  both phases, warning pose/circle, boss-bar transition and real survival damage.
- Alpha.4 client checks: connected wall/floor growth, subdued infected husk/cow
  skins, no nausea/darkness effects, and the kit's unsampled/measured screens.
  A real sneak-use sealed a paid sample without moving the patient. Reading it
  afterward displayed the stored patient, percentage, stage and sample age.
- Normal development client loads all three shipped missions and dimension
  tuning. Test suppression is now limited to the automated test-server JVM.
- The Patchouli guide loads all four categories and fourteen entries. Illustrated
  journal/terminal recipes, live survey components and discovery-gated opening
  were reviewed in the real client. An essence entry initially stayed locked,
  then opened after collecting a sample through the server observation path.
- All three installed-mod probes passed again with actual Aether, Bumblezone
  and Twilight biome data and mob placement.
- The complete live MCP portal → native horde → extraction loop passes in
  Aether, Bumblezone and Twilight Forest, with 12-token rewards and empty
  challenge tracking after extraction. The final alpha.3 run observed aechor
  plants, cave spiders and wolves respectively, checked against each checkpoint
  biome's installed spawn table. Earlier runs also exercised swets, zephyrs,
  endermen and slimes.
- Remaining legacy art is redrawn: heart, membrane, terminal, checkpoint,
  extraction, device, portal, cargo, sigil and essence. Native 16px clusters
  share heartwood, dark metal and ember materials; blocks have dedicated top
  faces. The portal has a transparent root aperture and ash/ember particles.
  The complete block lineup, deployment-board briefing and angled journal
  sprite/guide were reviewed in the restarted client.
- Terminal screen/casing faces, the smaller mirrored compass grip and sparse
  Bloom roots were reviewed in-world. A comparison wall on stone, oak and dirt
  showed fresh growth retaining host color above established ashen growth.

## Still to validate

- Guidebook mouse/keyboard navigation at additional GUI scales and with resource
  packs. This pass opened specific entries through Patchouli's operator command;
  MCP UI widget control is disabled in the development bridge.

- The final deployment-screen tweaks with the restored shipped missions,
  including long objective pages and real mouse interaction at multiple GUI
  scales. The current briefing and server click paths are verified.
- Human multiplayer balance, disconnect/rejoin with actual clients, shields,
  different equipment tiers, and extended boss kiting in varied terrain.
- Accessibility/localization polish: new guide text and some board copy are
  currently English. There is no claim of release-quality encounter balance.

Live checks used only the disposable debug save. The user's separate
`supaflat test` world was not modified. The Windows helper could not capture
the client during this pass; the development process was restarted and the
Minecraft bridge supplied the live inputs and visual checks.

## Reproducing development checks

Alpha.5 adds cached, branch-aligned bark ribs and resin knots to the connected
surface growth, keeping the relief below its thin selection shape. Client
checks in the disposable debug save covered floor/wall junctions and oblique
views, infected husk/cow skins against a normal cow, and stronger stage-based
spore/ash shedding. Native skin transparency and ordinary lesion lighting stay
intact. Resin fades in RGB as well as alpha for additive rendering, so dead
hearts leave cold scars. Nearby mob particles share a four-per-tick client budget.

Alpha.7 removes all raised layer geometry and the experimental fingers. The
texture-only layer uses connected pale bark tissue, warm gray grain and small
amber fissures. The same palette replaces green across the inventory art, mob
lesions and dust. Connection ports remain reciprocal and unrotated; harvest
targeting and empty collision stay unchanged.
Verified the flat floor/wall rendering in the disposable debug save, all 64 tile
edge contracts and the pale/amber palette. Build, 137 unit tests and the full
GameTest suite pass (120 core tests plus three optional biome probes that skip
absent modded biomes in the isolated run).

Alpha.8 implements the approved B + a little C art direction: large interrupted
heartwood scars with rare resin wounds. Eight authored eight-block patches use
a world-anchored jittered layout with a maximum of four overlapping patches per
face. The native art density stays 16px/block; the eight patches occupy a
512×256 atlas. Neighboring blocks share patch coordinates instead of repeating
a centered sprite. Fresh tissue fades in through alpha without pulling UV seams
apart. Host bleaching is gentler, retaining more native material detail. Six
pure-Java tests cover stable world sampling, negative coordinates, continuity,
bounded overlap/opacity and layout diversity. The historical 64-port atlas
checks above describe alpha.7 and earlier, not the new patch system.
Verified live floor/wall grain and a rare resin seam crossing a block boundary
in the disposable debug save. The build and 143 unit tests pass; the full isolated
GameTest run passes all 120 core tests (123 reported with optional biome probes).

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer -PbinaryMinecraft -PisolatedGameTests
.\gradlew.bat runGameTestServer -PbinaryMinecraft -PgtNamespaces=expeditions_modded_horde
.\gradlew.bat runClient -PbinaryMinecraft
```

Use the disposable `MCP-Debug-Test-20261029` save for the live tool script. It
checks the world identity before every command and restores mob spawning when
finished. The admin snapshot reads only the invoking player's latest run and
never modifies rewards, progress, or world blocks.

Regenerate native art with `python tools/gen_textures.py` (Pillow required).
`python tools/preview_heartwood.py` creates a before/after review board in
`build/heartwood-art-review.png`; the default reference is commit `724c27a`.
Regenerate the guide and its advancement gates with `python tools/gen_guidebook.py`.


Alpha.9 replaces alpha.8's rejected multi-block mural with the player-approved
native 16px tile material. Frayed eight-neighbor masks crop tissue and bleaching
together. The player approved the isolated and connected appearance in-game.
149 unit tests pass, including mask reconstruction and shared-edge continuity;
121 core GameTests pass, including diagonal boundary updates on all six faces
(124 reported in the isolated suite with three optional modded-biome probes).


Alpha.10: infection now requires actual surface/body contact or direct melee;
heart proximity alone is harmless. The surface contact uses frayed coverage on
all six faces. Short touches queue one exposure rather than being missed between
cadences. A Disruption Charm works in the offhand or a real Curios charm slot;
it grants the same registered effect admins can give to any living host. Existing
infection is not instantly cured. Native infected mobs retain their species and
persist their state, share nearby threats and targets, regroup, move 15% slower
and shiver through the vanilla renderer without freezing damage. Autonomous
heart growth seeks non-player living hosts as well as players.

Eight interaction tests cover the new paths; six pure-Java progression tests
cover contact-only accrual, protection precedence, retained variants and bounds.
The full isolated run passes 132 core GameTests (135 including optional biome
probes); 155 unit tests pass. A separate attempt with real Curios in GameTest
fails on that mod's unsupported mock-player sync packets. Curios compatibility
was instead verified in the actual client with `-PcuriosCompatTest`: wearing the
charm in its slot automatically supplied disruption, direct tissue contact kept
infection at zero, removing it allowed a dose to accumulate, and re-equipping
stopped new exposure. Two infected cows converged on an invulnerable sheep,
which became infected by body contact while retaining its sheep entity type.
The frozen-body shiver mixin applied successfully in the actual render pipeline;
no server frozen ticks or client frost overlay were introduced.


Alpha.11 corrects shared attention: private native targets no longer split a
nearby pack, head tracking is independent of movement, and already-running goals
retarget immediately when an attack alerts the pack. A staggered vanilla AI
schedule could also prevent odd-schedule animals from ever updating their path;
the pursuit goal now ticks each frame. Three additional GameTests exercise
conflicting targets, yaw/pitch tracking, invalidation, damage-event alerts and
real pursuit by animals on both native AI schedules. Shared attention is transient
and server-only. Disruption protects a host from infection, not from being seen.

Live alpha.11 check: a naturally ticking cow, sheep and husk all converged on one
protected pig. Temporarily freezing movement and moving the pig from left to
right made all three heads follow the same host; normal AI was then restored.
The current client is open in the disposable MCP test world for review.


## Alpha.12 frontier progression

See `frontier-progression.md` for the initial balance table. The full isolated
suite passes 137 core GameTests (140 including optional biome probes), and all
159 unit tests pass. Runtime HUD state is owned by world SavedData, not the
stateless manager facade; the live check caught and corrected duplicate bars.
Campaign liberation requires bound-guardian proof and extraction by default.
Physical infection and existing reward/recovery invariants remain in force.

The corrected live HUD shows one attention bar. A disposable timing probe reduced
the guardian threshold to 60 effective seconds; accelerated server ticks verified
hunting escalation, guardian arrival messaging and ownership records. Temporary
resistance protected the player during this check. The probe was disabled and
normal settings restored afterward. This is not a long-form survival balance test.
HUD proof: `build/mcp-verification/screenshot-1793635703076-0.png`.


## Alpha.13 Stillness and starter workshop

The alpha.12 home-liberation requirement is superseded: home is now sheltered by
the Stillness, with safe dormant material and no natural Bloom pressure. The new
starter loop, FE interfaces, local reversal and art are documented in
`stillness-progression.md`. Build and 162 unit tests pass; all 142 core GameTests
pass (145 including optional probes). Five Stillness tests cover bootstrap recipes,
real adjacent FE transfer, external input, processing persistence, cold colony
conversion, local power-loss protection and natural dormant generation.

The actual client produced essence in adjacent machines and displayed the paid
input, output, energy bar and progress. The final wheel geometry and tilted chart
lectern were visually checked. Saved screenshots live in
`docs/art-direction/stillness/`. The wheel geometry is static in this alpha.
