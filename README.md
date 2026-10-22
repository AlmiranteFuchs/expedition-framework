# Expedition Framework

A Minecraft mod that introduces a universal **expedition system**: players activate
an expedition terminal to begin a structured journey through a dimension with
checkpoints, objectives, difficulty, and guaranteed token rewards.

The long-term goal is to support vanilla dimensions and dimensions added by other
mods, with minimal hardcoded assumptions about terrain, mechanics, or registries.
The mod is intended to work standalone and to be suitable for modpacks.

## Environment

| Component   | Version                    |
| ----------- | -------------------------- |
| Minecraft   | 1.21.1                     |
| NeoForge    | 21.1.264                   |
| Java        | 21                         |
| Gradle      | 9.2.1 (wrapper)            |
| Build plugin| ModDevGradle 2.0.148       |

Mojang official mappings + Parchment (`2024.11.17` for 1.21.1).

## Build and test

```bash
./gradlew build        # compile, run tests, produce build/libs/expeditions-<version>.jar
./gradlew test         # run the pure-Java unit tests (JUnit 5)
./gradlew runServer    # launch a headless dev server in ./run
./gradlew runClient    # launch a dev client (GUI)
./gradlew runData      # data generation run (outputs to src/generated/resources)
```

> **Corporate proxy note:** if `./gradlew build` fails with
> `PKIX path building failed` against `libraries.minecraft.net`, the JVM
> truststore is missing the corporate TLS-inspection CA. Export the root CA
> presented by `openssl s_client -connect libraries.minecraft.net:443` and import
> it into `$JAVA_HOME/lib/security/cacerts` with `keytool -importcert`, then stop
> any running Gradle daemons (`./gradlew --stop`) and retry.

## Implemented features (milestone 1)

- **Mod bootstrap** — `ExpeditionsMod` entry point, `DeferredRegister`-based
  registration for blocks, items, and the creative tab.
- **Expedition Terminal** — `expeditions:expedition_terminal` block + item,
  creative tab, blockstate/block/item models, texture, and English lang entries.
  Right-clicking it reports the server-authoritative expedition status
  ("framework initialized, no active expedition" while none is running).
- **Minimal expedition domain** (`com.expeditions.core`, no Minecraft
  dependencies):
  - `ExpeditionStatus` — `ACTIVE` / `COMPLETED` / `FAILED` with validated
    transitions (terminal states reject everything).
  - `ExpeditionRun` — one attempt; `complete()` / `fail()` enforce the lifecycle.
  - `ExpeditionDefinition` — immutable description (id, display name, difficulty).
- **Server-side state** — `ExpeditionSavedData` persists the current run in the
  overworld `DimensionDataStorage`; `ExpeditionManager` is the server-authoritative
  facade (`activeRun`, `startRun`, `completeActiveRun`, `failActiveRun`).
- **Unit tests** — 10 JUnit tests covering the lifecycle transition rules.

## Verified

- `./gradlew build` — passes (jar assembled with correct `neoforge.mods.toml`).
- `./gradlew test` — 10 tests, 0 failures.
- Dedicated server boot (`runServer`) — mod constructs, block places
  (`setblock ... expeditions:expedition_terminal` → "Changed the block"), item
  summons with localized name ("Summoned new Expedition Terminal"), clean shutdown.

## Known limitations

- The terminal's right-click behavior is not yet verified in a real client
  session (requires a player; verified logic is server-side only).
- One global expedition run tracked at a time (per overworld save data);
  per-player/per-dimension runs come later.
- No route generation, checkpoints, objectives gameplay, rewards, GUI,
  datapack loading, or dimension adapters yet — see below.
- Block texture is a placeholder.

## Next milestones

1. `ExpeditionDimensionAdapter` interface + an Overworld implementation that can
   resolve safe positions; explicit "unsupported" outcome rather than unsafe
   routes.
2. `ExpeditionRoute`/`ExpeditionCheckpoint` model + a trivial fixed or generated
   route, with terminal UI to start a run.
3. `ExpeditionRewardService` — guaranteed token item on completion, wired to run
   lifecycle.
4. Config system (`ExpeditionConfig`) for difficulty, rewards, and dimension
   eligibility.

## Design principles

- **Server authority** — the server owns state transitions and rewards.
- **Pure domain core** — lifecycle rules in `com.expeditions.core` have no
  Minecraft imports and are unit-testable without the game.
- **Persistence** — world-scoped state via `SavedData`; no global mutable state.
- **Extensibility without speculation** — abstractions are added when a real use
  case exists (adapter/route/reward contracts land with their first consumers).
- **Optional MCP tooling** — an MCP tool may be used for in-game verification
  later, but nothing in the build or runtime depends on it.
