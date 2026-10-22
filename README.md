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
./gradlew build              # compile, run unit tests, produce build/libs/expeditions-<version>.jar
./gradlew test               # pure-Java unit tests (JUnit 5)
./gradlew runServer          # headless dev server in ./run
./gradlew runClient          # dev client (GUI)
./gradlew runGameTestServer  # in-game tests: boots a flat-world server, runs them, exits
./gradlew runData            # data generation (outputs to src/generated/resources)
```

> **Corporate proxy note:** if `./gradlew build` fails with
> `PKIX path building failed` against `libraries.minecraft.net`, the JVM
> truststore is missing the corporate TLS-inspection CA. Export the root CA
> presented by `openssl s_client -connect libraries.minecraft.net:443` and import
> it into `$JAVA_HOME/lib/security/cacerts` with `keytool -importcert`, then stop
> any running Gradle daemons (`./gradlew --stop`) and retry.

## Playing the expedition

1. Place an **Expedition Terminal** (creative tab "Expedition Framework").
2. Right-click it. An expedition starts and marker blocks appear in the Overworld:
   three **Expedition Checkpoints** and one **Expedition Exit**.
3. The chat tells you the next checkpoint's coordinates. Right-click each
   checkpoint **in order** — out-of-order and duplicate activations are rejected.
4. After all checkpoints, right-click the **Exit** to complete the run and
   receive the guaranteed token reward.
5. Dying fails the run (no reward). `/expeditions abandon` forfeits manually.
   Disconnecting does not end a run — it resumes on rejoin.

Commands: `/expeditions status`, `/expeditions balance`, `/expeditions abandon`.

## Route configuration

The route lives in the expedition **definition**, fed by server config
`run/world/serverconfig/expeditions-server.toml` (per world):

```toml
route.checkpoints = ["48,16", "96,-24", "144,48"]   # ordered "x,z" columns
route.exit        = "192,0"                          # exit "x,z" column
rewards.tokens    = 8                                 # guaranteed tokens on completion
```

Positions are Overworld X/Z columns resolved to the terrain surface (heightmap +
a small upward nudge over obstructions) when a run starts — never fixed Y values,
so the same config adapts to any world's terrain. Choose positions walkable on
foot and within a few hundred blocks of spawn.

## Implemented features (milestone 2)

- **Full playable loop** — terminal → ordered checkpoints → exit → token reward.
- **Per-player runs** — one active run per player, enforced by the ledger.
- **Server materialized route** — checkpoint/exit marker blocks are placed at
  run start and removed at run end (kept while another player's run needs them).
- **Server-validated checkpoints** — only the next expected checkpoint advances;
  out-of-order, duplicate and foreign positions are rejected; activation requires
  real block reach.
- **Death failure** — `LivingDeathEvent` fails active runs, no reward.
- **Token rewards** — deterministic, granted exactly once on completion,
  persisted per player.
- **Persistence** — runs (incl. resolved route and checkpoint progress) and token
  balances stored in the overworld `DimensionDataStorage`; survives restart and
  disconnect. Rejoining mid-run shows a resume hint.
- **Commands** — `/expeditions balance|status|abandon`.
- **Config** — `expeditions-server.toml` (route, reward).
- **Tests** — 23 unit tests + 4 GameTest in-game tests.

## Verified

- `./gradlew build` — passes; `expeditions-0.1.0.jar` assembled.
- `./gradlew test` — 23 unit tests, 0 failures.
- `./gradlew runGameTestServer` — **4/4 game tests pass** on a real dedicated
  server (flat world), driving the genuine `useItemOn` → `useWithoutItem`
  interaction path:
  - full loop completes with reward and marker cleanup,
  - death fails the run with no reward,
  - out-of-order and duplicate checkpoint clicks are rejected,
  - the exit refuses unfinished runs.
- Earlier manual smoke: dev server boots, `setblock`/`summon` confirmed block
  and item registration.

## Known limitations

- Not yet verified by a **human client session** — game tests drive the real
  server interaction path, but no GUI click-through was performed.
- Concurrent players share the configured marker positions (markers are shared
  world blocks); each run still validates its own progress independently.
- A player who abandons mid-run can clean up via `/expeditions abandon`; runs
  left active by a permanently-offline player keep their markers until another
  run ends or an admin intervenes.
- Marker blocks are indestructible by design; the terminal is not protected.
- Placeholder textures.
- Persistence guarantee: a server crash between the completion reward and the
  next world save could, in principle, lose the `rewarded` flag — the run record
  is written by the standard saved-data dirty mechanism.

## Architecture

- `com.expeditions.core` — **pure Java** (no Minecraft imports): `ExpeditionStatus`,
  `ExpeditionDefinition`, `ExpeditionRoute`/`RouteAnchor`/`Waypoint`,
  `ExpeditionRun` (lifecycle + ordered checkpoint activation + reward flag),
  `ExpeditionLedger` (per-player runs + token balances).
- `com.expeditions.server` — `ExpeditionManager` (server facade: start/complete/
  fail, route resolution, marker placement/removal), `ExpeditionSavedData`
  (ledger ↔ NBT in overworld `DimensionDataStorage`), `ExpeditionEventHandler`
  (death + commands).
- `com.expeditions.block` — terminal/checkpoint/exit blocks delegating to the
  manager. `com.expeditions.registry` — `DeferredRegister` wiring.
- `com.expeditions.gametest` — `ExpeditionGameTests` (4 tests, batches a–d).
- `ExpeditionsConfig` — NeoForge server config → `ExpeditionDefinition`.

## Next milestones

1. `ExpeditionDimensionAdapter` interface + per-dimension route resolution so
   other dimensions can declare support or report "unsupported".
2. Optional objectives and time-based bonus rewards (speed as a bonus, never the
   sole reward).
3. Datapack-driven expedition definitions.
4. Token sink (shop or item claim) and additional reward variety.
5. Multiplayer parties / shared runs.
