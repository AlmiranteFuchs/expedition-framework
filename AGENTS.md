# AGENTS.md

Guidance for coding agents working on this repository.

## Project

NeoForge mod for **Minecraft 1.21.1** (NeoForge `21.1.x`), Java 21, Gradle wrapper,
**ModDevGradle** (`net.neoforged.moddev`). Mod id: `expeditions`. Base package:
`com.expeditions`. Do not change MC/loader versions or migrate to other loaders.

## Commands

- Build: `./gradlew build`
- Unit tests: `./gradlew test` (JUnit 5, in `src/test/java`)
- Dev server: `./gradlew runServer` (headless, `run/` dir, offline mode)
- Dev client: `./gradlew runClient`
- Datagen: `./gradlew runData` → `src/generated/resources` (already on the
  resources source path)

Corporate proxy: TLS interception requires importing the corporate root CA into
`$JAVA_HOME/lib/security/cacerts` before dependency resolution will work.

## Architecture boundaries

- `com.expeditions.core` — **pure Java domain** (status, run, definition). No
  Minecraft imports allowed here; it must stay unit-testable without the game.
- `com.expeditions.server` — server-authoritative state (`ExpeditionManager`
  facade + `ExpeditionSavedData` in overworld `DimensionDataStorage`).
- `com.expeditions.registry` — `DeferredRegister` wiring (blocks, items, tabs).
- `com.expeditions.block` — blocks; interactions delegate to the server layer.
- Lifecycle transitions are explicit and validated in the domain (`ACTIVE →
  COMPLETED | FAILED`; terminal states reject all transitions). Don't weaken this.
- Run state is world-scoped (`SavedData`), not static/global. Mutations must
  `setDirty()`.

## Rules

- Keep the lifecycle/domain free of Minecraft dependencies — tests run without
  launching the game.
- Write or update unit tests for domain changes.
- Don't add abstractions without a current consumer (e.g., don't create
  `ExpeditionDimensionAdapter`, route generator, reward service, or config until
  their milestone actually needs them). The planned order is in README.md.
- Don't add runtime dependencies without checking they're needed; prefer vanilla/
  NeoForge facilities.
- Use `DeferredRegister`/`DeferredHolder` for all registrations; register in the
  mod constructor.
- MCP tooling is **optional** for verification; never make it a build or runtime
  dependency.
- The `run/` directory and `build/` are gitignored; `src/generated/resources` is
  the datagen output.

## Verification status

Milestone 1 verified: build + 10 unit tests pass; dev server boots, mod loads,
block places and item summons (see README "Verified"). Right-click interaction is
server-side logic verified by design, not yet exercised by a real client.
