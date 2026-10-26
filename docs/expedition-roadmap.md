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
| — | Foundation (terminal, manager, defs, routes, objectives, horde, modifiers, options, economy, persistence) | **Complete** | 101 unit + 60/60 game tests green |
| M7 | Physical mission artifact + extraction | **Complete** | Domain latch/delivery + run-bound item + datapack `recover_artifact` + unit/game tests. Distinct from the M12 *progression* artifact: mission cargo is per-run, bound by `run_id`, consumed/stripped at run end — it never unlocks anything |
| M8 | Destructible defense device | **Complete** | `ObjectiveKind.DEFEND_DEVICE` + datapack `defend_device`; `ExpeditionDeviceBlock` (tracked marker); `DefenseState` (hp/maxHp/endTick/nextWaveTick) persisted on the run; stage-gated at its declared checkpoint; exactly-once destruction (`damageDevice` → required fails run, optional marks objective failed); bounded waves/mob cap via config; unit + 6 game tests |
| M9 | Checkpoint economy / reward reliability | **Partial** | Idempotency (`markRewarded`, single-file save) solid; per-checkpoint crediting still missing — rewards are all-or-nothing at `completeRun`. Remaining: credit tokens at each checkpoint activation (guaranteed), keep completion+objective bonuses at-risk until extraction |
| M10 | First complete expedition (mandatory checkpoint route + existing objective handlers) | Not started | Blocked on M8. Composition only — beacon activation, artifact recovery, collection, defense, extraction all reuse existing handlers |
| M11 | Structured feedback + expedition summary | **Partial** | Chat messages on transitions exist; no structured end-of-run summary separating guaranteed/pending/awarded/lost rewards; failure reason not surfaced |
| M12 | Terminal progression + dimensional campaign | Not started | Expanded scope (see below): permanent keys, consumable costs, trophies, mission prerequisites with explanations, dimensional campaign states, crafted progression artifact, Big Bad event unlock, subsequent operation unlocks. No unlock/trophy concepts exist; menu is selection-only |
| M13 | Dimension compatibility | **Mostly complete** | Adapters, bounded safe search, natural mob pools, fail-safe refusals done. New scope fits: threat behavior must route through `ExpeditionDimensionAdapter`/`HordePools` (dimension-scoped state, per-dimension spawn pools), not a parallel system. Untested against a real third-party modded dimension |
| M14 | Configuration & expansion | **Partial** | TOML + datapack + tags + options cover most parameters. New scope: threat tuning knobs (pressure growth, caps, per-dimension overrides). Structure archetypes deferred by design |
| M15 | Global dimensional threat + Big Bad event | Not started | Smallest necessary addition — doesn't fit M12 (progression infra) or M13 (compat plumbing). A persistent per-dimension pressure level that escalates horde pressure (size, effects, spawn presence) across *all* expeditions in that dimension, culminating in an unlockable Big Bad event. Depends: M12 (unlock chain gates it), M13 (dimension-scoped behavior), M14 (tuning surface) |

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

## Dependencies researched

None added for M7 or M8 — run binding lives in a vanilla
`DataComponentType` (`ModDataComponents`); the device is a regular
`DeferredRegister` block. No new dependencies exist anywhere in the
project so far, per `AGENTS.md` rules.

## Next recommended milestone

**M9 (checkpoint economy / reward reliability)** — earliest genuinely
incomplete milestone now that M8 landed. Remaining work is small and
isolated: credit guaranteed tokens at each checkpoint activation instead
of all-or-nothing at `completeRun`, keeping completion + objective
bonuses at-risk until extraction. Then M12 (progression infra) → M15
(threat + Big Bad, needs M12+M13) → M10 (composes everything).
