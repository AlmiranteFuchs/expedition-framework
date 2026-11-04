# Expedition roadmap & architecture

Inspiration: Warframe / Helldivers / ARC Raiders — terminal-launched overland
missions on **natural terrain first**, custom structures later. This document
is the contract: architecture, agreed design decisions, milestone status, and
audit notes. `AGENTS.md` covers invariants and build/test conventions; this
covers direction.

## Architecture (as implemented)

| Layer | Code | Role |
|---|---|---|
| Terminal / menu | `block/ExpeditionTerminalBlock`, `server/ExpeditionMenu` | Mission + option selection (vanilla ChestMenu), start surface; `progression/trophies` not yet present |
| Run lifecycle | `server/ExpeditionManager`, `core/ExpeditionRun`, `core/ExpeditionLedger` | Authoritative start/join/checkpoint/complete/fail/abandon; one active run per player; party model |
| Definitions | `core/ExpeditionDefinition`, `server/data/ExpeditionDefinitions(+Loader)`, `ExpeditionsConfig` | Objectives, modifiers, difficulty, rewards; datapack JSON overrides TOML config |
| Route | `core/RoutePlanner`, `core/RouteGeneration`, `server/dimension/*Adapter` | Bounded safe search (`resolveAnchor` + jitter ring, `maxAttempts`, `GENERATED_ROUTE_REROLLS`); resolved positions persisted with the run |
| Objectives / events | `core/ExpeditionObjective`, `ObjectiveKind`, `CheckpointChallenge`, horde in `ExpeditionManager.spawnHorde` | visit/collect/time-limit objectives; horde challenge; modifiers (weather/member+horde effects) |
| Dimension compat | `server/dimension/ExpeditionAdapters` + Overworld/Nether adapters, `DestinationChecks`, `SafeColumnSearch`, `HordePools`, `ItemRewards` | Thin adapter per dimension; natural-spawn horde pools; dimension reward pools |
| Rewards / economy | `ExpeditionLedger` tokens, `ItemValuator`/`ItemRewards`, `/expeditions redeem` | Guaranteed tokens on completion, token sink, tier-scored drops |
| Persistence | `server/ExpeditionSavedData` | ONE file: runs, balances, live markers, options, modifiers — crash-consistent, fail-closed load |

## Agreed design decisions (contract)

- **Rewards**: tokens from completed checkpoints are guaranteed and kept on
  later failure; completion rewards and pending bonuses stay at-risk until
  extraction. Previous balances never lost. Reward processing idempotent
  across interactions, reconnects, restarts.
- **Progression**: keys = permanent unlocks; consumables = charged
  atomically; trophies = persistent records; terminal explains blocked
  missions. Mission artifact ≠ progression key.
- **Artifacts**: real inventory items bound to the run; explicit rules for
  missing/incorrect/dropped/stored/lost/duplicated; vanilla inventory &
  death behavior unless documented otherwise; server-authoritative and
  idempotent delivery.
- **Defense events**: destructible device with explicit health/state,
  stage-gated activation, defined failure behavior, bounded duration/spawns.
- **Dimensions**: best-effort, fail-safe; no hardcoded mod support; bounded
  searches only; validated dimension/route before any world mutation; no
  resource consumption or partial rewards on failed setup.
- **Scope**: natural terrain, no mandatory structures, vertical slice first;
  optional objectives must affect rewards/risk/route; no premature scripting
  or integration frameworks.

## Milestone status — audit 2026-10-26 (mapping refresh)

| M | Feature | Status | Evidence / gaps |
|---|---|---|---|
| — | Foundation (terminal, manager, defs, routes, objectives, horde, modifiers, options, economy, persistence) | **Complete** | 113 unit + 87 game tests green |
| M7 | Physical mission artifact + extraction | **Complete** | Domain latch/delivery + run-bound item + datapack `recover_artifact` + unit/game tests. Distinct from the M12 *progression* artifact: mission cargo is per-run, bound by `run_id`, consumed/stripped at run end — it never unlocks anything |
| M8 | Destructible defense device | **Complete** | `ObjectiveKind.DEFEND_DEVICE` + datapack `defend_device`; `ExpeditionDeviceBlock` (tracked marker); `DefenseState` (hp/maxHp/endTick/nextWaveTick) persisted on the run; stage-gated at its declared checkpoint; exactly-once destruction (`damageDevice` → required fails run, optional marks objective failed); bounded waves/mob cap via config; unit + 6 game tests |
| M9 | Checkpoint economy / reward reliability | **Complete** | Guaranteed `tokens` bank per activation (`checkpointTokenPortion`, balanced split over resolved route); `securedTokens` per member persisted on the run; completion tops members up to the full share (`max(0, share − secured)`) — bonuses/objectives stay at-risk until extraction; credits survive failure/death/restart; 5 unit + 4 game tests |
| M10 | First complete expedition (mandatory checkpoint route + existing objective handlers) | **Complete** | Two bundled defs ship in `data/expeditions/expeditions/`: `field_survey` (entry, grants `field_clear`) and `deep_salvage` (the composed flagship — visit + artifact + defense + collect + time objectives, key/cost/campaign gating, modifiers). `bundledExpeditionsCompose` verifies the shipped JSON parses and starts |
| M11 | Structured feedback + expedition summary | **Complete** | `broadcastSummary` at `endRun`: per-member breakdown — checkpoints/objectives done, banked, extraction top-up, total, balance (complete); cause + kept credits + at-risk estimate (failed). `run.fail(reasonKey)` records the cause at every fail site (died/abandoned/device_destroyed/site_lost/dimension_lost) |
| M12 | Terminal progression + dimensional campaign | **Complete** | M12a: per-player unlock keys, `requires`/`grants`/`cost_tokens`, menu lore for locked missions, `/expeditions unlocks`. M12b: `CampaignState` (FRONTIER→ESTABLISHED→CLEARED) per dimension, `min_campaign_state` gating, trophies + `/expeditions trophies`. M12c: `attunement_sigil` crafted artifact grants a key at the terminal without being consumed |
| M13 | Dimension compatibility | **Complete** | Adapters, bounded safe search, natural mob pools, fail-safe refusals done. The extension point is proven: `thirdPartyAdapterHostsExpedition` registers a test-side End adapter exactly as a modded-dimension plugin would and runs a full lifecycle through it. A real third-party modded dim can't be smoke-tested without one installed, but the registration path is verified |
| M14 | Configuration & expansion | **Complete** | TOML + datapack + tags + options cover most parameters; pressure/microbiome knobs landed with M15. Per-dimension overrides ship as the `expedition_dimensions` datapack layer — `data/*/expedition_dimensions/<dim-path>.json` sparsely overrides `microbiome.*`/`pressure.*` for the named dimension (a bundled `minecraft/the_nether.json` example). Structure archetypes deferred by design |
| M15 | Global dimensional threat + Big Bad event | **Complete** | Microbiome heart colonies (below) + per-dimension `dimensionPressure` that escalates horde size and adds empowered spawns past thresholds + `big_bad` defs spawn a boss assault at the final checkpoint; completing a big_bad run flips the dimension to CLEARED, silencing the microbiome |

### Foundation audit — what supports the new requirements

- **Smart hordes** (`HordePools` + natural spawn tables): the threat's
  escalation can raise mob counts/buffs without new spawn machinery —
  `spawnHorde` already centralizes pool selection and `horde_effects`.
- **Objectives** (`ObjectiveKind` + per-run `ObjectiveState`): new event
  kinds (M8 defense, future beacon/sabotage) slot into the same
  latch/persist/pay-out machinery.
- **Persistence** (`ExpeditionSavedData` single file): campaign states,
  unlocks, trophies, and the dimensional threat level can ride the same
  crash-consistent record — no second source of truth.
- **Modifiers** (`ExpeditionModifiers`): threat escalation can inject
  `horde_effects`-style buffs dynamically without new effect plumbing.
- **Dimension handling** (`ExpeditionAdapters` + `portalDimension` vs run
  dimension): campaign/threat state is naturally keyed by dimension id —
  the seam already distinguishes "where the portal is" from "where the
  run is".

## Campaign requirements (new) — mapped to milestones

- **Dimensional campaign states** — persisted per-dimension progression
  (e.g., frontier → established → threatened/cleared) that gates which
  expeditions the terminal offers there. Lives in `ExpeditionSavedData`
  next to balances. → **M12**
- **Mission prerequisites** — expeditions can require keys/tokens/campaign
  state; the terminal must explain what's missing. The menu already lists
  all expeditions, so gating is additive. → **M12**
- **Crafted progression artifact** — a *permanent* crafted item acting as a
  progression key (survives across runs; not consumed per attempt unless a
  mission says so). Explicitly **not** the M7 mission artifact: mission
  cargo is bound to one run and dies with it; the progression artifact is
  account/world-scoped unlock proof. → **M12**
- **Big Bad event unlock + subsequent operations** — completing a campaign
  stage unlocks the Big Bad expedition; completing that unlocks follow-on
  operations. A key/unlock chain, no special mechanics. → **M12**
- **Global dimensional threat** — a per-dimension pressure level that
  escalates (e.g., on player activity or timer) and modifies horde
  behavior dimension-wide; high pressure feeds the Big Bad event. Uses
  `HordePools`/modifiers/dimension keys, not a parallel spawn system. →
  **M15**, with M13 supplying dimension plumbing and M14 the tuning knobs.
- **Big Bad event itself** — the flagship fight/event the unlock chain
  gates. Kept out of M12 so progression infra stays shippable alone. →
  **M15**

## M7 — physical mission artifact (implemented)

A `recover_artifact` objective binds a dedicated item to the run:

```json
{"id": "cargo", "type": "recover_artifact", "checkpoint": 2,
 "required": true, "bonus_tokens": 10}
```

- The checkpoint holds the cargo: activating that checkpoint issues exactly
  one `expeditions:expedition_artifact` — a non-stackable item carrying the
  run id in the `expeditions:run_id` data component. Granted to the activator
  (dropped at their feet if the inventory is full); `artifactIssued` is a
  persisted latch — repeat clicks never re-issue.
- Completion is **delivery**, not pickup: at the exit click the manager
  searches *online* members' inventories for a stack whose run id matches;
  a match consumes the stack and completes the objective. A required
  objective with no delivered artifact refuses like any missing required
  objective. Optional ones only pay their bonus when delivered.
- Rules for edge cases (documented behavior):
  - The artifact is a normal item: it drops on death, can be stored, thrown,
    or picked up by anyone. Only party inventories count at delivery — a
    thief can hold a run hostage; that is intended emergent risk.
  - Artifacts from other/old runs never satisfy (run id must match).
    Duplicates of the same run id can't multiply rewards: one delivery,
    consumed once; remaining copies are stripped from members at run end.
  - Artifacts bound to runs that no longer exist are swept from inventories
    on login (dead-run junk). A live run's artifact always survives.
  - `required` + lost artifact → the run can only fail/abandon.

## M8 — destructible defense device (implemented)

A `defend_device` objective deploys a siege at its declared checkpoint:

```json
{"id": "siege", "description": "Hold the device",
 "type": "defend_device", "checkpoint": 1, "seconds": 60,
 "required": true, "bonus_tokens": 15}
```

- `checkpoint` gates the stage: the device (`expeditions:expedition_device`)
  appears above that checkpoint when it is activated — never earlier, never
  twice. Placement only takes replaceable space (bounded upward search):
  two runs in the same dimension can resolve overlapping route positions,
  and clobbering a shared marker would silently murder another run. If no
  replaceable spot exists the siege doesn't start (fail-safe, logged).
- `DefenseState` on the run holds `hp`/`maxHp`/`endTick`/`nextWaveTick` —
  persisted with the run like every other authoritative field, so a
  restart mid-siege resumes correctly.
- Once a second `tickDefenses` (game-bus `ServerTickEvent`): expired timer
  → objective completes + device stands down; missing device block →
  counted as destruction (creative break, `/fill`); due wave → spawns up
  to `defenseMobCap` live mobs (config `defenseWaveSeconds`,
  `defenseWaveSize`, `defenseDeviceHp`). Siege mobs roll from the device's
  own natural pool (`HordePools`) and pathfind to the device
  (`AttackDeviceGoal`), each hit chipping 1 hp via `damageDeviceAt`;
  player punches also chip — the run record is the single hp authority.
- Destruction is exactly once: `damageDevice` clears the state, marks the
  objective failed, and only then does the manager act — a required
  objective fails the run, an optional one just never pays. All siege
  entities are challenge-tracked and cleaned like any horde.
- Run end (death/abandon/completion elsewhere) stands the device down and
  cleans its siege.

## M9 — checkpoint economy (implemented)

The guaranteed `tokens` are no longer all-or-nothing at extraction:

- **Banked per checkpoint** — each activation credits every member the
  balanced portion of the definition's `tokens`
  (`tokens * i / n - tokens * (i-1) / n`, summed exactly) through the same
  scaling/split/difficulty formula as completion. Credits hit the ledger
  immediately — they survive run failure, member drop-out, disconnect and
  restart.
- **At-risk remainder** — bonus time tokens and objective bonuses are only
  settled at `completeRun`. Completion tops each member up to their full
  share (`max(0, share − secured)`), so a member is never paid twice and a
  member whose party shrank after crediting keeps the overage.
- **`securedTokens` per member** lives on the run record and persists —
  old saves default to empty and simply top up fully.
- **Party sealed at first checkpoint** means every member is present for
  every credit — totals equalize naturally; a member who leaves mid-run
  keeps only what they banked.
- `tokensAwarded`/`markRewarded` semantics unchanged: the recorded value
  is the full per-member share (secured + top-up), so bonus-drop budgets
  and restore invariants are untouched.

## M15 prototype — assimilation layer (implemented)

The Big Bad's visual signature: `expeditions:assimilation_layer`, a thin
harvestable membrane that coats sturdy faces of a solid block and renders
them grayscale — without touching the covered block.

- **Attachment** — a `MultifaceBlock` (the sculk-vein model): face
  property `d` means the support at `pos.relative(d)` is covered on its
  `d.getOpposite()` face, so one cell can coat several faces at once and
  corner cells hold floor and wall alike — no bare junction seams.
  Attachment re-validates per face on every neighbor change via
  `isFaceSturdy` (stricter than vanilla's full-face check: leaves refuse);
  air, fluids, fences, slabs refuse too. A lost support sheds only its
  own face through `updateShape`; the block dies with its last face.
- **Geometry** — thin 1/16 outline plates per covered face for aiming,
  and no collision at all: entities pass through the film and collide
  with the support, like sculk veins and lichen.
- **Drops** — `noLootTable()` by design: the support-loss path goes through
  `updateOrDestroy` → `destroyBlock(drop=true)` which *would* roll loot, so
  a real loot table cannot distinguish harvest from cleanup. Exactly one
  `assimilation_essence` (placeholder progression drop) is granted in
  `playerDestroy`, reached only by genuine survival breaks — never in
  creative, never on support loss, piston crush or restoration.
- **Grayscale** — no shader pipeline: the block-entity renderer resolves
  the covered face's actual sprite (face quad → unculled quad → particle
  icon), desaturates it once at runtime into a `DynamicTexture`
  (`client/DesaturationTextures`, keyed by sprite name, session-cached),
  and draws it as an opaque skin a hair above the face inside the layer's
  own cell, with vanilla face shading + cell lighting. The translucent
  membrane model sits on top as the "growth". Works for any block with a
  baked face sprite; BER-only/custom-rendered blocks get the membrane
  fallback. Client-only, zero world data, chunk rebuilds handled
  automatically (the skin lives outside the chunk mesh).
- **The living signature** — over the skin, an amber capillary-vein trace
  (procedurally grown in `client/TraceTexture`, rim-weighted, rotated per
  cell) plus an additive halo. Both ride `core/PulseWave`, a lub-dub
  heartbeat waveform: the wave originates at the colony's heart (each
  layer block entity records its heart at growth time, synced in the
  update tag) and reaches each cell `TICKS_PER_BLOCK` ticks later per
  block of distance — the colony visibly beats outward from its nucleus,
  every colony on its own tempo derived from the heart's position. The
  heart carries its own aura renderer (vein shell + additive halo, first
  and loudest). A dead heart drops its cells to the resting thrum; the
  film stays, the life is gone.
- **Known limits** — desaturation covers only the attached face (full-cube
  desaturation would need per-face layers or a chunk-render hook); the
  desaturated pixel copy does not follow animated sprites (first frame);
  non-square sturdy faces (e.g., stair tops) get a full-square skin.
  Acceptable for the prototype; a shader-based selective desaturator is
  the documented upgrade path if face-coverage proves insufficient.

## Dependencies researched

None added for M7 or M8 — run binding lives in a vanilla
`DataComponentType` (`ModDataComponents`); the device is a regular
`DeferredRegister` block. No new dependencies exist anywhere in the
project so far, per `AGENTS.md` rules.

## M12a — progression keys and prerequisites (implemented)

Definitions can now chain expeditions into sequences:

```json
{"id": "second_op", "requires": ["expeditions:first_op_clear"],
 "grants": ["expeditions:second_op_clear"], "cost_tokens": 25}
```

- **`requires`** — unlock keys the player must hold. `startRun` refuses
  with a named explanation ("requires x, costs y more tokens") before any
  world changes; the terminal menu keeps locked expeditions listed and
  shows what's missing in the button lore (key icon instead of the
  compass).
- **`cost_tokens`** — charged to the starting player only after every
  other validation passed (route resolved, markers placed), so a refused
  or unresolvable start never eats the fee.
- **`grants`** — awarded party-wide on completion: every member earns the
  permanent key, online or not, in the same atomic ledger save as
  balances. `/expeditions unlocks` lists held keys.
- Unlocks live in `ExpeditionLedger` (per-player `Set<String>`), persist
  in the same SavedData file as runs/balances with the same per-record
  fault isolation. `RequirementCheck` is the domain rule — the menu's
  lore and the start refusal read the same data.
- Old saves: no unlock records → players hold no keys; runs without the
  new def fields restore empty/no-cost.

## Microbiome — heart-driven colonies (implemented)

The assimilation layer's growth organ: `expeditions:assimilation_heart`,
a solid amber node — the *only* thing that grows the layer. Discovered
while moving through a dimension, killable to stop the spread.

- **Traversal seeding** — a throttled `PlayerTickEvent` hook rolls
  `microbiome.spawnChance` and calls `Microbiome.trySpawnHeart`: a heart
  lands on a standable column in a ring around the player (config radii
  `spawnRadiusMin/Max`), using the same bounded elevation-window column
  scan as `MobSpawns` — so a heart never seeds onto an enclosed
  dimension's roof. `microbiome.enabled` is the runtime kill-switch for
  both spawning and growth.
- **Heart pulses** — every `microbiome.growthIntervalTicks` the block
  entity applies one uniformly chosen covering act: seed a layer against
  each open face of the heart itself plus the floor face under each
  horizontal neighbor when the colony is empty, else grow — fillet an
  uncovered sturdy face of a member's own cell (the junction fix: corner
  cells coat floor AND wall), walk a covered face sideways along the
  surface, wrap around the support's edge onto its other faces, or seep
  through to the far face of a one-block substrate. The colony's cells
  persist in the block entity so a chunk unload never forgets; dead
  members are pruned each pulse so broken layers free their slot.
- **Bounded colony** — `microbiome.maxColonySize` caps covered faces
  (surface consumed): a local blob, never a world takeover. Removing the
  heart is the counterplay — no ticker, no growth; surviving layers die
  only with their supports.
- **Cleared seam** — `ExpeditionSavedData.clearedDimensions` silences
  hearts and traversal spawning per dimension (`Microbiome.enabled`
  consults it first). The "won the dimension" transition lands with M12b;
  the shutoff is already enforced.

## M12b — dimensional campaign states + trophies (implemented)

Per-dimension campaign progression persisted in `ExpeditionSavedData`:

- **`CampaignState`** — `FRONTIER → ESTABLISHED → CLEARED`, derived from
  `campaignProgress` (completions per dimension) against config
  thresholds (`campaignEstablishedAtCompletions`,
  `campaignClearedAtCompletions`). Each completion calls
  `recordCampaignCompletion`: progress increments, and crossing the
  cleared threshold flips `clearedDimensions` — which is what silences
  the microbiome.
- **`min_campaign_state`** — definitions gate on the dimension's state;
  `RequirementCheck` carries the campaign gap alongside keys/tokens and
  the menu explains it in lore like any missing requirement.
- **Trophies** — completions of defs that `grants` keys also record a
  trophy entry; `/expeditions trophies` lists them. `resetCampaign`
  clears progress + cleared dims + trophies (admin/test path).

## M12c — attunement sigil (implemented)

The crafted progression artifact: `expeditions:attunement_sigil`, a
non-consumable item carrying an unlock key in its
`expeditions:attunement_key` data component. Using it on a terminal
grants the key (idempotent — the artifact is proof, not a charge). The
recipe is a normal datapack recipe, so packs can re-attune it to their
own key ids via the component.

## M15 — dimensional pressure + the Big Bad (implemented)

- **Pressure** — `dimensionPressure` per dimension in the SavedData:
  `raisePressure` on run starts and completions (config
  `pressurePerRunStart`/`pressurePerRunCompletion`, capped by
  `pressureMax`), plus **dwell accrual** — every
  `pressure.dwellIntervalTicks` a player spends in an unliberated
  dimension adds `pressure.dwellAmount` (join tick excluded, spectators
  don't count, cleared dimensions stop accruing) — stalling between
  checkpoints or lingering slowly raises the threat.
  `resetPressure` as the test/admin path.
  Escalation reads the counter in `spawnHorde`/`spawnSiegeWave`: horde
  size grows by `pressureHordeBonusPer` steps, and past
  `pressureBuffThreshold` spawned mobs get empowered buffs.
- **Infection** — `Assimilation.onPlayerTick` accrues a per-player
  infection (persistent NBT, survives death/relog) within
  `microbiome.infectionRadius` of live, loaded hearts (a registry on
  `AssimilationHeartBlockEntity.onLoad`/`setRemoved`, no chunk scans).
  Staged debuffs at `infectionStage*At` thresholds; at `infectionMax`
  the player dies to `expeditions:assimilation` damage (bypasses
  invulnerability via the vanilla tag) and an armor-stand effigy —
  player head, worn armor, "Assimilated <name>" — stands in their
  place, tagged `expeditions:assimilated` for a future real model.
  Warding: `infectionWardPerEnchantedPiece` per enchanted armor slot +
  `infectionWardSigil` for a carried sigil, capped at
  `infectionWardMax`. All overridable per-dimension via M14 keys.
- **Big Bad** — a `"big_bad": true` definition turns its final
  checkpoint activation into a boss assault (`triggerCheckpointChallenge`
  picks a bigger, buffed wave). Completing a big_bad run calls
  `setDimensionCleared` — the campaign flip that ends the threat in that
  dimension.

## M10 — the bundled campaign (implemented)

Two definitions ship in `data/expeditions/expeditions/`, making the mod
playable out of the jar and demonstrating the full feature surface:

- **`expeditions:field_survey`** — the entry mission: no requirements,
  generated 3-checkpoint route, required visit + optional collect/time
  objectives, grants `expeditions:field_clear`.
- **`expeditions:deep_salvage`** — the composed flagship: requires
  `field_clear`, costs 15 tokens, gates on `min_campaign_state:
  established`, generated 4-checkpoint route composing visit →
  recover_artifact → defend_device plus optional collect/time objectives,
  rain + night-vision/speed modifiers.

The game-test suite suppresses datapack definition loading
(`ExpeditionDefinitionLoader.suppressLoadingForTests`) so shipped defs
never shadow the config defaults tests resolve against;
`bundledExpeditionsCompose` verifies the real shipped JSON parses,
chains, gates, and starts.

## M14 — per-dimension tuning overrides (implemented)

`data/<namespace>/expedition_dimensions/<path>.json` — the resource id
IS the dimension id it tunes (`minecraft/the_nether.json` →
`minecraft:the_nether`; modded ids and sub-paths work the same way).
Loaded by `ExpeditionTuningLoader` into the `ExpeditionTuning` snapshot
(same immutable-reload pattern as expedition definitions, with the same
test suppression seam).

The file is sparse — only the knobs it changes:

```json
{
  "microbiome": { "enabled": true, "heart_spawn_chance": 0.5,
                  "growth_interval_ticks": 30, "max_colony_size": 64,
                  "infection_accrual": 2, ... },
  "pressure":   { "per_run_start": 8, "per_run_completion": 15,
                  "max": 100, "horde_bonus_per": 25, "buff_threshold": 50,
                  "dwell_amount": 2, "dwell_interval_ticks": 2400 }
}
```

Every `microbiome.*` and `pressure.*` config knob is overridable; absent
keys fall back to the global `expeditions-server.toml` value at the call
site (`ExpeditionTuning.bool/integer/real(dim, path, fallback)`). A
bundled `minecraft/the_nether.json` ships denser/faster assimilation and
steeper pressure in the nether — the harder dimension fights back
harder. `dimensionTuningOverridesGlobals` verifies the resolution, the
fallbacks, and the clamp flowing into `raisePressure`.

## Next recommended milestone

### Automatic dimension compatibility (2026-10-29)

Loaded dimensions now receive an automatic terrain adapter and a generated
exploration mission without requiring an addon. Explicit adapters retain
priority; datapack missions replace automatic defaults for their dimension.
The automatic scanner validates real floors, hazards and headroom, scans below
logical ceilings, and refuses void/unsafe routes without creating platforms.
Automatic mission IDs are `expeditions:auto/<namespace>/<dimension_path>`;
`/expeditions dimensions` lists loaded dimension IDs and available mission IDs.
Four integration tests cover listing, remote expedition start through extraction
and cleanup, void/hazard refusal, and enclosed-dimension placement. A real
third-party mod installation remains a manual compatibility check.

### Remaining work

Compass navigation polish: a signed elevation ember floats over the held
instrument; right-click attunement adds a vertical spark trace and three
ascending/descending notes. The ±2-block level band keeps walkable differences
quiet. All 32 needle frames now retain a carved pale-heartwood housing rather
than substituting vanilla compass art. Two game tests cover either-hand use,
cooldown, unchanged tracker data, and invalid/cross-dimension refusal; four
unit tests cover the elevation rule. MCP client checks verified the held art,
above/below movement, and an active right-click trail in the disposable world.

Horde compatibility follow-up: selection now reads every non-friendly biome
spawn category, including NeoForge extensions, instead of only MONSTER.
Biomes without eligible hostiles fall back to local NeutralMob creatures
before the generic fallback tag; passive livestock remain excluded. Neutral
checkpoint/boss mobs receive anger toward their party target. Three optional
integration probes passed against installed Aether, Bumblezone, and Twilight
Forest biome data and the shared horde placement/finalization path. Since
GameTest worlds load only vanilla dimensions, these probes apply the actual
mod biomes to test platforms. The isolated core-suite option avoids unrelated
Accessories mock-player payload failures without moving installed mod jars.

Alpha is feature-complete — every roadmap milestone is now closed,
including M13's extension-point proof and M14's per-dimension overrides.
Remaining work is hardening and feel: client playtest of the visual
layer (trace pulse, membrane, heart colonies, compass heartbeat), a
smoke run against a real third-party modded dimension if one ever joins
the pack, and any Big Bad *design* pass beyond the current assault-wave
prototype.

## M16 — Onboarding & UX Improvements (in progress)

Improving player onboarding, feedback, and quality of life to reduce complexity
barriers while preserving depth.

### Quick wins (immediate implementation)

**Technical Improvements - Test helper updates**
- Replace deprecated `makeMockServerPlayerInLevel()` with new NeoForge test API
- Eliminates 100+ deprecation warnings in game tests

**Simplified Early Power - Config option**
- Add `expeditions.resonance.simple_generators` config flag
- When true: merge all three generator types into one "Resonance Generator" with reduced output
- Removes fuel, quiet-ground, and core-binding complexity for casual play

**Quality of Life - Expedition history**
- Add `/expeditions history` command showing last 10 expeditions with outcomes
- Useful for debugging failed runs and tracking personal progress

**Better In-Game Feedback - Machine status**
- Click on resonance machines sends chat message with current status
- Add "status check" button in GUI showing full state chain

**Better In-Game Feedback - Compass distance**
- Add distance indicator in compass tooltip ("274 blocks away")
- Reduces reliance on 3D spatial reasoning

### Medium effort (implementation in progress)

**Better In-Game Feedback - Terminal failure tooltips**
- Enhanced tooltip showing full requirement tree for locked expeditions
- Shows campaign state gap, missing keys, token cost in one view
- Hover over locked expeditions for detailed explanation

**Campaign Pacing - Progress bar**
- `/expeditions campaign` command shows progress bar to next state
- "3/5 completions to ESTABLISHED" explicitly
- Visual feedback on campaign progression

**Progressive System Disclosure - Journal chapter gating**
- Field guide chapters unlock as systems are discovered:
  - Chapter 1: Microbiome + dormant flakes (unlocks on first dormant flake pickup)
  - Chapter 2: Resonance machines (unlocks on first resonator build)
  - Chapter 3: Field equipment (unlocks on first cell craft)
  - Chapter 4: Expeditions (unlocks on terminal activation)
  - Chapter 5: Campaign progression (unlocks after first expedition completion)
- Reduces upfront complexity

**Campaign Pacing - Dimension summary**
- Command or GUI showing all visited dimensions with campaign states
- Lists available expeditions per dimension
- Shows which Big Bad is unlocked

**Expedition Onboarding - Solo tutorial expedition**
- "Solo Expedition" option in menu disables party joining
- Shows waypoint arrow instead of requiring compass interpretation
- 2 checkpoints instead of 3-5
- Grants "Expedition Novice" trophy on completion

**Technical Improvements - Chunk-load safety**
- Safety wrapper in `ExpeditionManager` force-loads chunks before entity lookups
- Fallback scans `entityManager.sectionStorage` if entity not found
- Addresses entity visibility caveat for unloaded chunks

### Major features (deferred to future milestones)

**SavedData sharding — superseded**
- The ledger, runs, markers and campaign records stay together so rewards and
  run state roll back atomically. The three-file proposal is closed.

**Gameplay Variety - New objective kinds**
- `SCOUT`: Visit radius around location without triggering mobs (stealth)
- `HARVEST`: Extract X blocks of specific type (field-gathering)
- `RELAY`: Place marker at location (sets up next checkpoint)
- `SURVIVE`: Stay in region for X seconds without taking damage (endurance)
- Requires game tests, balancing, datapack schema updates

**Gameplay Variety - Dynamic objective pools**
- Some expeditions have random objectives from a pool
- Makes repeat runs less predictable
- Increases replay value

**Gameplay Variety - Side objectives**
- Optional mini-tasks awarding bonus tokens without gating completion
- Examples: "Collect 5 different flowers in this biome"
- Encourages exploration without blocking progress

**Player Retention - Post-campaign content**
- Resource expeditions: pure farming, no threat, after dimension cleared
- Mastery expeditions: harder versions with better rewards
- Scavenger hunts: find hidden caches placed by the mod
- Gives reasons to return to cleared dimensions

**Player Retention - Dimensional traits**
- Unique properties unlocked after clearing each dimension:
  - Overworld: Dormant knots spawn in villages (trade with villagers)
  - Nether: Dormant hearts spawn in basalt deltas (special essence variants)
  - End: Severed cores craftable from ender pearls (alternative source)

**Campaign Pacing - Visual landmarks**
- Campaign milestones have visual markers:
  - FRONTIER → ESTABLISHED: Spawn dormant heart cluster near spawn
  - ESTABLISHED → CLEARED: Remove guardian's ember circle
- Visual feedback on campaign progress

**Quality of Life - Quick-save during expeditions**
- Temporary checkpoint marker persists for 10 minutes
- Emergency save point for long expeditions
- Reduces frustration from unexpected disconnects

**Quality of Life - Compass waypoint sharing**
- Party members can see each other's compass targets
- Improves coordination without requiring voice chat
- Optional feature (config toggle)

**Quality of Life - Config presets**
- Include `expeditions-server.toml` templates for playstyles:
  - Casual: simplified generators, reduced threat
  - Hardcore: strict requirements, higher rewards
  - Expert: full complexity, challenging
- Makes modpack configuration easier

### M16 implementation review — alpha.23

Machine surface repair and operating feedback are implemented and tested. The
five machine models retain their shapes and palette; their 34 overlapping face
pairs are now zero. Machine screens show live operating reasons, contextual
hints, collector sharing and actual output. Comparators read source energy or
finished condenser stock. Intermittent power gets a short visual hold without
free processing. `simple_generators` optionally makes all three source variants
produce 10 R/t without fuel, quiet ground or a bound core; normal rules remain
the default. Their distinct registry IDs and saved inventories are preserved.

Implementation decisions: use persistent GUI status instead of repeated chat
messages or a redundant status button. Keep all 14 stable journal discovery IDs
and the always-readable starter instructions. Do not shard SavedData: reward
flags, balances, runs and markers must roll back atomically in one file. The
proposed test-helper replacement is deferred: the supported 1.21.1 helper still
exposes the deprecated server-player method and has no equivalent replacement.
History, compass distance and campaign progress are the next M16 slice.

### M16 personal reports and navigation — alpha.24

Implemented `/expeditions history`: ten personal reports, including outcome,
route progress, secured/full credited share, elapsed time and failure cause.
Reports persist with the ledger in its existing atomic SavedData file; malformed
entries are skipped individually. A member leaving and rejoining an open party
gets one updated report when that run ends. Existing saves begin collecting new
reports without fabricating historical runs.

The compass tooltip shows coarse direct distance bands and the existing ember's
vertical direction. Exact free distance was deliberately avoided: the recovered
Heartglass Lens remains the paid precise horizontal-distance and height tool.
The discovered wayfinding and checkpoint guide pages explain both additions.

`/expeditions campaign` now shows the next completion threshold or required
confrontation, rather than implying ordinary completions always grant liberation.
`campaign all` lists dimensions with campaign/pressure records plus the current
dimension, eligible charter counts and eligible confrontation availability. This
is a records summary, not a new registry of every visited dimension. The terminal
readiness panel already lists key, token and campaign requirements; its liberation
hint now correctly directs players toward a confrontation where required.

Verified: 193 unit tests, 176 core GameTests (179 including optional modded biome
probes), build and alphaBundle. Remaining M16 proposals above are still proposals;
no solo-arrow/tutorial rewrite, new objective kinds or post-campaign rewards are
claimed by this release. Existing journal identities, authored tree geometry and
guardian proof rules are preserved.


### M16 onboarding — alpha.25

First Bearing is a short optional Overworld charter, suggested on the home
terminal for a player without field proof. It offers two witness stumps, a
private entrance and an amber bearing while holding the issued compass.
The cue follows the same synchronized target as the needle, labels the next
mark or hollow and reports height without exposing exact lens measurements.
Natural terrain and ordinary hazards still matter. It teaches the normal
checkpoint and extraction interactions without rolling checkpoint hordes.

Extraction grants the Expedition Novice trophy, field proof and eight tokens.
The payout settles at extraction and the trophy closes the practice charter,
so failed attempts can be retried without turning practice into a repeatable
token source. Field Survey remains an alternative. Ordinary charters can use
the Party access toggle; solo runs preserve their chosen routes and challenges,
and legacy runs default to open party admission.

Terminal readiness is available on the locked deployment control. The guide
introduces practice and private portals through existing discovery chapters.
This slice retains the existing stable journal gates, one-file save model and
chunk ownership cleanup. New objective types and post-campaign charters remain
future work.


### Post-liberation return loop — alpha.27 development

Aftergrowth Surveys are generated for loaded, adapter-supported dimensions
whose campaign is cleared. The two-mark route uses the normal portal, party,
navigation, and extraction lifecycle. It omits checkpoint hordes and timed
bonuses. Extraction yields two Return Knots without advancing campaign counts
or granting Hard/Brutal gear proof. Ordinary creatures and hazards remain.

### Field commissions — alpha.28

Deep Salvage offers an optional bark sample commission alongside the cargo and
siege. The bonus settles at extraction. The assignment rewards gathering on
the route and can be skipped when the party needs to get home.

Datapack authors can use `harvest` with a positive `count` and exactly one
`block` ID or block `tag`. The run records one unit per matching survival
harvest by a party member carrying the field compass in the destination
dimension. Item pickup and Fortune yields do not affect the count. Progress
uses the existing saved objective record.

### Rotating commissions — alpha.29

Deep Salvage now draws one commission at deployment. The Wayroot lists the
possibilities before deployment and announces the selection when the entrance
opens. The cargo and siege remain the charter's main work. Datapacks can add a
`commission_pool` array using the same entries as `objectives`. Pool entries
must be optional and independent of checkpoints. The selected entry is saved
with the run's objectives so reloads never reroll it.

Deep Rock Galactic's mission assignments
and optional challenges are a reference for keeping the main extraction goal
legible while giving the party a reason to detour. Alex's Caves is a reference
for using a discovery in the world to give the guidebook something concrete to
say about the player's next outing.

- [Deep Rock Galactic Season 01](https://www.deeprockgalactic.com/season-01)
- [Alex's Caves project page](https://www.curseforge.com/minecraft/mc-mods/alexs-caves)

### Post-clearance mastery — alpha.31

Lasting Graft is a fixed three-mark Hard charter. A host with a personal
extraction trace can offer eight tokens to open it in a cleared dimension.
Recover a living scion at the second mark and hold the graft at the third
before carrying the scion home. Local threats can answer the defense, while
the dimension stays cleared and its background Bloom pressure stays quiet.
Extraction grants the usual Hard gear proof and two Return Knots.

### Field orders — alpha.32

The survey log in the held field journal records the party's actual orders,
including the commission drawn when the Wayroot opened. Required work,
optional work and gathering progress use the same labels as
`/expeditions status`. Reopen the journal to refresh its snapshot.

### Biome scouting — alpha.33

A scout commission asks the party to find a kind of place. Deep Salvage can
draw a forest bearing as its optional work. Datapacks define a `scout`
objective with exactly one `biome` ID or biome `tag`. The party records the
bearing when a member carrying the field compass reaches a matching biome in
the destination dimension. The bearing is shared and pays at extraction.

The [server presets](server-presets.md) cover a gentler introduction, the
default Charter balance, and a harsher pressure curve with better bonus drops.

### Defense waves — alpha.34

The previous checkpoint's horde is cleared before the siege begins. An active
siege uses its own attackers and reinforcement schedule. Siege mobs approach
the device from an adjacent reachable space and damage it until the defenders
intercept them. The defense suite covers a later checkpoint's opening wave,
device damage from a spawned attacker, and a follow-up wave.

### Defense attendance and recovery — alpha.35

The compass points to the active defense device, then returns to the next
witness or the Hollow when the hold resolves. At least one living party
member must be within 32 blocks to advance the hold. Outside that range,
the survival and reinforcement clocks pause and siege mobs stop attacking
the device. An attacker's device assignment survives entity reload.

### Checkpoint horde approaches — alpha.36

An ordinary witness horde forms on a reachable side of the site. The wave
stages at roughly the witness's height, leaves space around nearby players,
and uses adjacent lanes when its main approach fills. The sound and embers
come from the lead lane. If the terrain closes a lane, the wave tries
another bearing. The biome still supplies the attackers.
Newly spawned attackers briefly show pale bark and bright amber seams before
settling into the normal infected overlay.

### Field caches and pale woodworking — alpha.37

Aftergrowth can place one optional pale cache on a nearby detour when the
destination offers a safe site. The first witness gives a rough compass
bearing. Recover its sealed find and carry it through the Hollow to draw
a curiosity from the destination's salvage pool. Dimensions with no eligible
curiosities yield a Return Knot. The location and one-time claim are saved
with the run. The cache is removed when the route ends.

Infuse two logs with Assimilation Essence to craft two Pale Wood blocks.
Each makes four Pale Planks. Eight Pale Planks and a Dormant Flake craft
the Pale Chest, which uses the standard chest inventory.

### Expedition sound cues — alpha.38

The custom sound set now scores opening and crossing the Wayroot, the
witness's answer, a horde's direction, graft defense deployment and loss,
and the party's return or failure. The horde cue originates at its incoming
lane. Each wired event has a subtitle.

### Horde arrival visibility — alpha.39

Checkpoint waves first look for walkable ground screened by terrain. They
stage roughly 15–18 blocks out at the default range and leave 12 blocks
around players. In open terrain the search begins behind the witness's
activator. A quiet wood crack marks the incoming side. The alpha.38 sound
wiring was removed after the listening pass.

### Infected mob presentation — alpha.40

Infected mobs carry matte pale-bark plates beneath their resin seams. The
plates use the host skin's alpha mask, and the stage controls how strongly
they show. Short body shudders grow stronger with the infection. The hive
reduces native movement and flight speed attributes by 30 percent where
present. The modifier clears when the host is disrupted or cleansed.

### Guardian combat and machine feedback — alpha.41

The Rootbound Guardian alternates a close sweep with a burst at the target's
marked position from the opening phase. Below half health, it adds a ring
with a safe pocket at its center. Each windup has a distinct pose and ground
warning. It approaches more slowly and shudders harder as it enrages.

Resonance generators and the condenser make quiet, spaced work sounds
while generating or refining. Each machine has a distinct cue.

### Unattended verification checkpoint

- Alpha.41 (`e487ac7`) passed 187 required GameTests. In the dev client,
  the Audio Playtest charter opened, the first witness spawned a horde,
  and the second deployed a defense. The unattended device fell and
  returned the player to the Wayroot. Successful extraction, the
  second-phase Guardian, and the machine sound mix still need client checks
  before the cumulative release. The approval service reached its usage
  limit during the Guardian probe.
- The party navigation batch adds `/expeditions bearing`. Hold the issued
  compass in the expedition dimension to share your current position for
  one minute. The shared reading survives reconnects and is shown in
  `/expeditions status`. The route needle keeps its own target.
- Reconnect and `/expeditions status` also report the latest activated
  witness for ten minutes. This bearing refers to the witness already in
  the world and expires on its original clock across reloads.
- Next gameplay batch: relay and endurance commissions. Keep markers and
  their rewards in the existing atomic expedition save.
