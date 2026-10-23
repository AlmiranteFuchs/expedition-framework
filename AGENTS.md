# AGENTS.md

Guidance for coding agents working on this repository.

## Project

NeoForge mod for **Minecraft 1.21.1** (NeoForge `21.1.x`), Java 21, Gradle wrapper,
**ModDevGradle** (`net.neoforged.moddev`). Mod id: `expeditions`. Base package:
`com.expeditions`. Do not change MC/loader versions or migrate loaders.

## Commands

- Build: `./gradlew build`
- Unit tests: `./gradlew test` (JUnit 5, `src/test/java`, no Minecraft deps)
- In-game tests: `./gradlew runGameTestServer` (flat-world server, exits; tests in
  `com.expeditions.gametest`, template `data/expeditions/structure/arena.nbt`)
- Dev server: `./gradlew runServer`; dev client: `./gradlew runClient`
- Datagen: `./gradlew runData` → `src/generated/resources`

Corporate proxy: TLS interception requires importing the corporate root CA into
`$JAVA_HOME/lib/security/cacerts` before dependency resolution works.

## Architecture boundaries

- `com.expeditions.core` — **pure Java domain** (status, run, definition, route,
  ledger, `SafeColumnSearch`). No Minecraft imports allowed; unit-testable
  without the game.
- `com.expeditions.server.dimension` — `ExpeditionDimensionAdapter` interface +
  Overworld/Nether implementations + `ExpeditionAdapters` registry. This is the
  dimension extension point: adapters own "can a route resolve here and where";
  lifecycle code must never assume terrain. No adapter → unsupported dimension.
- `com.expeditions.server` — server-authoritative coordination
  (`ExpeditionManager`), persistence (`ExpeditionSavedData`, overworld
  `DimensionDataStorage`), game-bus hooks (`ExpeditionEventHandler`).
- `com.expeditions.registry` — `DeferredRegister` wiring (blocks, items, tabs).
- `com.expeditions.block` — blocks; interactions delegate to the server layer.
- Lifecycle is explicit and validated in the domain: `ACTIVE → COMPLETED | FAILED`,
  checkpoints activate strictly in order by resolved position, `complete()`
  requires all checkpoints, `markRewarded(tokens)` records the award exactly
  once. Don't weaken this.
- Runs are per-player, keyed by UUID in `ExpeditionLedger`. Token balances are a
  separate map in the same ledger, persisted with it. Runs record
  `startedGameTime` (`UNKNOWN_START` = legacy/no bonus) and `tokensAwarded`.
  Balances saturate at `Integer.MAX_VALUE`; `restoreTokens` rejects negatives.
- Run state is world-scoped (`SavedData`), not static/global. Mutations must
  `setDirty()`. Runs + balances + live markers live in ONE file — a crash rolls
  all back together, so reward state can never split (this is the duplicate-
  reward guarantee; keep it that way).
- `ExpeditionRun.restore` validates persisted shape (non-empty checkpoints,
  in-range progress, reward only on COMPLETED); `ExpeditionSavedData.load`
  skips+logs malformed records per entry — never let one corrupt record kill
  the whole file.

## Recovery policy (fail-closed)

- `ExpeditionManager.resumeOrFail(player)` — run on login and on terminal use.
  A run resumes only if its dimension exists AND every marker block is still
  present; otherwise it's failed (no reward) and surviving markers are removed.
- Every placed marker is tracked in `ExpeditionSavedData.liveMarkers`; on
  server start `pruneOrphanMarkers` removes tracked markers no active run
  references (crash rolled back the run but not the chunk).
- Disconnect = no reward, no completion; the run stays ACTIVE and is validated
  on next login. One active run per player is enforced by the ledger.

## Gameplay shape (milestone 5)

- Expedition sources: datapack JSON (`data/*/expeditions/*.json` via
  `ExpeditionDefinitionLoader` → `ExpeditionDefinitions` immutable snapshot)
  takes precedence; `expeditions-server.toml` sections
  `[expeditions.overworld]` / `[expeditions.nether]` are the built-in defaults.
  Manager lookup: `ExpeditionDefinitions.forDimension(dimId)`
  `.or(ExpeditionsConfig.definitionFor(dim))`. Multiple datapack defs per
  dimension → lowest id wins.
- The terminal picks the expedition for the player's current dimension; no
  definition or no adapter → refusal message, no world changes.
- `RouteSpec` sealed type: `ExpeditionRoute` (authored anchors) or
  `RouteGeneration` (datapack `"generate"` block / config
  `expeditions.overworld.generation.*`). The built-in Overworld expedition
  GENERATES by default (`generate = true`); `generate = false` falls back to
  the authored config anchors. `RoutePlanner.generate` rolls a seeded random
  walk from the player's position (48-96 legs, ±45° turns by default).
- Destination safety is checked on real block state via shared
  `DestinationChecks` (free pos + headroom, sturdy non-hazard floor: lava/
  magma/cactus/fire/fluids rejected, world border). Generated anchors retry
  up to `RouteGeneration.maxAttempts` (0-8) columns on a fixed ±16 jitter
  ring; authored anchors are strict. One chunk load per candidate — bounded
  and synchronous by design.
- Routes resolve BEFORE any marker is placed.
- Rewards: guaranteed `tokens` + `bonusTokens` when finished within
  `bonusTimeTicks`; computed by `ExpeditionDefinition.totalReward(elapsedTicks)`.
- Marker blocks are placed at run start and removed at run end in the run's
  own dimension unless another active run in that dimension shares them.
  Indestructible by design.
- Expedition compass: `ExpeditionCompassItem` (plain `Item`, NOT `CompassItem`
  so it never competes with block `useItemOn`) granted on start and removed on
  any run end; the server sets `DataComponents.LODESTONE_TRACKER` to the next
  target and `ExpeditionsClient` (`@Mod dist = CLIENT`) registers the `angle`
  property that reads `tracker.target()`.
- `ExpeditionRun` has an immutable `party` member set (singleton owner today)
  persisted in the run tag; ledger lookups (`isMember`) are membership-aware —
  party groundwork, no multiplayer semantics yet.
- Death fails the run (`LivingDeathEvent`). Disconnect keeps the run resumable.

## Testing conventions

- Unit-test domain rules in `src/test` — no Minecraft imports needed there.
  `SafeColumnSearch` is tested with synthetic column predicates; when modeling
  "blocks space but can't be stood on" remember solid blocks ARE floors for
  whatever stands above them.
- In-game behavior goes in `ExpeditionGameTests` (`@GameTest` methods, distinct
  `batch` names since tests share configured marker positions). Interactions are
  simulated through `ServerPlayerGameMode.useItemOn` after teleporting the mock
  player next to the target — don't use `helper.useBlock` for blocks outside the
  test structure bounds (`relativePos` is not a clean inverse there).
- Cross-dimension tests: get the level via
  `server.getLevel(Level.NETHER|END)`, teleport the mock player with
  `player.teleportTo(level, x, y, z, Set.of(), yaw, pitch)`, and pass that level
  to `useItemOn`. Mock players leaked by retries can leave foreign active runs —
  fail them before asserting marker cleanup, and remember the shared ledger
  holds their runs when asserting on save/load contents.
- Persistence tests: `ExpeditionSavedData.save(tag, registryAccess)` →
  `ExpeditionSavedData.load(tag, ...)` round-trips in game tests; corrupt the
  tag in between to test malformed-data handling.
- Datapack tests: simulate reloads via `ExpeditionDefinitions.reload(map)` and
  ALWAYS reset with `reload(Map.of())` in a finally block — the snapshot is a
  shared static and leaks between tests otherwise.
- `ExpeditionGameTests` uses `makeMockServerPlayerInLevel()` (deprecated in
  1.21.1 but functional) — revisit if a better mock player API exists.

## Rules

- Keep the domain free of Minecraft dependencies.
- Write/update unit tests for domain changes; game tests for interaction paths.
- Don't add abstractions without a current consumer (dimension adapters, route
  generators, reward services land with their first consumers).
- All authoritative transitions/rewards on the logical server; never trust
  client-supplied IDs, progress, or amounts.
- No new runtime dependencies without clear need.
- Use `DeferredRegister`/`DeferredHolder`; register in the mod constructor.
- MCP tooling is optional for verification; never a build/runtime dependency.
- `run/` and `build/` are gitignored; `src/generated/resources` is datagen output.

## Verification status

Route-safety/config-generation milestone verified: `build` + 49 unit tests +
20/20 GameTest in-game tests pass (all prior overworld tests now exercise
generated routes implicitly, plus config-default generation, destination
safety contract, and hazard rejection). Human client click-through and real
disconnect not simulated — the compass needle angle itself is client
rendering, untested.
