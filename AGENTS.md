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
  ledger). No Minecraft imports allowed; unit-testable without the game.
- `com.expeditions.server` — server-authoritative coordination
  (`ExpeditionManager`), persistence (`ExpeditionSavedData`, overworld
  `DimensionDataStorage`), game-bus hooks (`ExpeditionEventHandler`).
- `com.expeditions.registry` — `DeferredRegister` wiring (blocks, items, tabs).
- `com.expeditions.block` — blocks; interactions delegate to the server layer.
- Lifecycle is explicit and validated in the domain: `ACTIVE → COMPLETED | FAILED`,
  checkpoints activate strictly in order by resolved position, `complete()`
  requires all checkpoints, reward marked exactly once. Don't weaken this.
- Runs are per-player, keyed by UUID in `ExpeditionLedger`. Token balances are a
  separate map in the same ledger, persisted with it.
- Run state is world-scoped (`SavedData`), not static/global. Mutations must
  `setDirty()`.

## Gameplay shape (milestone 2)

- Route lives in `ExpeditionDefinition`, sourced from `expeditions-server.toml`:
  ordered `"x,z"` checkpoint anchors + exit anchor + token reward. Heights are
  resolved to the surface when a run starts — do not introduce fixed Y coords.
- Marker blocks (`expedition_checkpoint`, `expedition_exit`) are placed by the
  server at run start and removed at run end unless another active run shares
  the positions. They are indestructible by design.
- Death fails the run (`LivingDeathEvent`). Disconnect keeps the run resumable.
- Terminal starts a run when none is active; otherwise reports progress.

## Testing conventions

- Unit-test domain rules in `src/test` — no Minecraft imports needed there.
- In-game behavior goes in `ExpeditionGameTests` (`@GameTest` methods, distinct
  `batch` names since tests share configured marker positions). Interactions are
  simulated through `ServerPlayerGameMode.useItemOn` after teleporting the mock
  player next to the target — don't use `helper.useBlock` for blocks outside the
  test structure bounds (`relativePos` is not a clean inverse there).
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

Milestone 2 verified: `build` + 23 unit tests + 4/4 GameTest in-game tests pass
(full loop, death fail, order/dup rejection, unfinished exit). Human client
click-through not performed.
