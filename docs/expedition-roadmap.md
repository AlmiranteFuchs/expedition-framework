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
| M13 | Dimension compatibility | **Mostly complete** | Adapters, bounded safe search, natural mob pools, fail-safe refusals done. New scope fits: threat behavior must route through `ExpeditionDimensionAdapter`/`HordePools` (dimension-scoped state, per-dimension spawn pools), not a parallel system. Untested against a real third-party modded dimension |
| M14 | Configuration & expansion | **Partial** | TOML + datapack + tags + options cover most parameters. Pressure/microbiome tuning knobs landed with M15. Remaining: per-dimension overrides; structure archetypes deferred by design |
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
  `pressureMax`), `resetPressure` as the test/admin path.
  Escalation reads the counter in `spawnHorde`/`spawnSiegeWave`: horde
  size grows by `pressureHordeBonusPer` steps, and past
  `pressureBuffThreshold` spawned mobs get empowered buffs.
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

## Next recommended milestone

Alpha is feature-complete — every roadmap mechanic has a base
implementation. Remaining work is hardening and feel: client playtest of
the visual layer (trace pulse, membrane, heart colonies), third-party
dimension smoke (M13's open gap), per-dimension config overrides (M14),
and any Big Bad *design* pass beyond the current assault-wave prototype.
