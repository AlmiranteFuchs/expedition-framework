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

## Milestone status — audit 2026-10-23

| M | Feature | Status | Evidence / gaps |
|---|---|---|---|
| — | Foundation (terminal, manager, defs, routes, objectives, horde, modifiers, options, economy, persistence, 54 game tests) | **Complete** | 94 unit + 54/54 game tests green |
| M7 | Physical mission artifact + extraction | **Complete** | Domain latch/delivery + run-bound item + datapack `recover_artifact` + 6 unit + 6 game tests |
| M8 | Destructible defense device | Not started | No device concept exists |
| M9 | Checkpoint economy / reward reliability | **Partial** | Rewards are currently all-or-nothing at `completeRun` — "checkpoint tokens guaranteed on later failure" requires per-checkpoint crediting (design change to the reward model); idempotency (`markRewarded`, single-file save) already solid |
| M10 | First complete expedition (beacon → artifact → samples → defense → extraction) | Not started | Blocked on M7+M8 |
| M11 | Feedback + run summary | **Partial** | Chat messages on transitions exist; no structured end-of-run summary (guaranteed/pending/awarded/lost split) |
| M12 | Terminal progression (keys, consumables, trophies) | Not started | No unlocks/trophies; menu is selection-only |
| M13 | Dimension compatibility | **Mostly complete** | Adapters, bounded safe search, natural mob pools, fail-safe refusals done; untested against a real third-party modded dimension |
| M14 | Configuration & expansion | **Partial** | TOML + datapack + tags + options cover most parameters; structure archetypes deferred by design |

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

## Dependencies researched

None added for M7 — the run binding lives in a vanilla `DataComponentType`
(registered in `ModDataComponents`), no library needed. No new dependencies
exist anywhere in the project so far, per `AGENTS.md` rules.

## Next recommended milestone

**M8 (destructible defense device)** — the last missing mechanic for M10's
first complete expedition; the `CheckpointChallenge` enum and horde
infrastructure already give it a seam to hang on.
