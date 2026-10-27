# Reading path

An ordered tour of the codebase for study: each entry names the file and
the **one idea** it's there to teach. Read top to bottom — later files
assume the vocabulary earlier ones establish.

## 1. The pure domain (`core/` — no Minecraft imports)

| File | Key idea |
|------|----------|
| `ExpeditionRun` | A validated state machine: `ACTIVE → COMPLETED \| FAILED`, checkpoints activate strictly in order, rewards record exactly once. Secured vs. at-risk tokens live here. |
| `ExpeditionDefinition` | An immutable spec as a record — tokens, route, objectives, `requires`/`grants`/`costTokens`. Validation happens in the compact constructor so a bad def can never exist. |
| `ExpeditionLedger` | The per-player book of record: runs, token balances, unlock keys. `checkRequirements` returns a `RequirementCheck` — a rule as a value type both gating and UI consume. |
| `SafeColumnSearch` | The whole "find a standing Y in a column" algorithm as pure predicate injection (`isFree`/`isSturdyFloor`/`isHazard`) — unit-testable with synthetic columns, zero world access. |
| `Waypoint`, `RouteAnchor`, `ExpeditionRoute`, `RouteGeneration` | Authored vs. generated routes as a sealed domain model. |
| `CheckpointChallenge`, `Difficulty`, `WeatherModifier`, `ObjectiveState` | Small enums/records that keep the manager's branching declarative. |

## 2. The dimension boundary (`server/dimension/`)

| File | Key idea |
|------|----------|
| `ExpeditionDimensionAdapter` | The extension point: "can a route resolve here, and where." Lifecycle code never assumes terrain. |
| `DestinationChecks` | The shared standability vocabulary both adapters and mob spawning speak. |
| `OverworldAdapter` vs `NetherAdapter` | Same contract, opposite worlds — heightmap vs. `SafeColumnSearch`. The cleanest illustration of why the abstraction exists. |
| `ExpeditionAdapters` | A tiny registry: dimension id → adapter. No adapter, no expedition. |

## 3. The authoritative layer (`server/`)

| File | Key idea |
|------|----------|
| `ExpeditionManager` | The coordinator — read `startRun` (gating → route resolution → markers → charge, in that order so a failure pays nothing), `activateCheckpoint` (credit + challenge), `completeRun`/`endRun` (reward + cleanup + summary), `resumeOrFail` (fail-closed recovery). `findMobSpawn`/`trySpawnMob` show reusing domain contracts for entity spawning. |
| `ExpeditionSavedData` | One SavedData file for runs + balances + markers + unlocks — crash-consistency as a *data layout* decision, plus per-record fault isolation so one corrupt entry can't kill the file. |
| `ExpeditionMenu` / `ExpeditionEventHandler` | Server-driven UI and the game's event surface — thin, everything delegates inward. |
| `HordePools`, `ItemRewards`, `ItemValuator` | Data-driven spawn/reward tables: biome tables → tags → config fallback. |

## 4. The Minecraft surface

| File | Key idea |
|------|----------|
| `registry/` | `DeferredRegister` wiring — how mods declare blocks, items, block entities. |
| `block/AssimilationLayerBlock` | Block lifecycle done right: `FACING` as the covered face's normal, `canSurvive`/`updateShape` as the support contract, drops moved out of the loot table to distinguish harvest from cleanup. |
| `item/` (compass, artifact) | Vanilla components (`LODESTONE_TRACKER`) doing real work; the artifact's `run_id` binding. |
| `client/AssimilationLayerRenderer` + `DesaturationTextures` | Client-only rendering: steal the covered face's sprite, desaturate into a `DynamicTexture`, draw a skin a hair above it. Client code is a guest — nothing authoritative lives here. |
| `server/data/` (`ExpeditionDefinitions`, loader) | Datapack JSON → immutable snapshot; malformed files warn and skip. |
| `ExpeditionsConfig` | TOML server config — every gameplay knob has a name. |

## 5. The tests — the second half of the documentation

- `src/test/java/...` — JUnit over the pure domain. `ExpeditionRunTest`,
  `ExpeditionLedgerTest`, `SafeColumnSearchTest` are the specs for their
  classes, in executable form.
- `src/main/java/com/expeditions/gametest/ExpeditionGameTests` — in-world
  tests through real interaction paths (`useItemOn`, menu clicks), grouped
  in named batches. `dumpArea`/`findEntity`/`pollUntil` show what testing
  async entity state in a live server actually takes.

## 6. Project meta

- `AGENTS.md` — conventions and invariants that aren't in the code.
- `docs/expedition-roadmap.md` — milestone history; each shipped slice
  maps to a commit if you want diffs as lessons.
- `src/SOUL.md` — the design intent the mechanics answer to.
