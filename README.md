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

1. Place an **Expedition Terminal** (creative tab "Expedition Framework") — it
   works in the Overworld **and the Nether**.
2. Right-click it. The expedition configured for your current dimension starts
   and marker blocks appear there: three **Expedition Checkpoints** and one
   **Expedition Exit**. In unsupported dimensions the terminal reports that no
   expedition is available.
3. The chat tells you the next checkpoint's coordinates. Right-click each
   checkpoint **in order** — out-of-order and duplicate activations are rejected.
4. After all checkpoints, right-click the **Exit** to complete the run and
   receive the guaranteed tokens plus a time bonus when earned.
5. Dying fails the run (no reward). `/expeditions abandon` forfeits manually.
   Disconnecting does not end a run — it resumes on rejoin.

Commands: `/expeditions status`, `/expeditions balance`, `/expeditions abandon`.

## Route configuration

Each dimension has an expedition section in the server config
`run/world/serverconfig/expeditions-server.toml` (per world):

```toml
[expeditions.overworld]
checkpoints = ["48,16", "96,-24", "144,48"]  # ordered "x,z" columns
exit        = "192,0"
tokens          = 8     # guaranteed tokens on completion
bonusTokens     = 4     # extra for finishing within bonusTimeSeconds (0 = off)
bonusTimeSeconds = 300

[expeditions.nether]
enabled  = true         # false hides the expedition without clearing the route
checkpoints = ["24,24", "48,-16", "72,40"]
exit        = "96,0"
tokens          = 16
bonusTokens     = 8
bonusTimeSeconds = 240
```

Positions are X/Z columns; the dimension's **adapter** resolves heights when a
run starts:

- **Overworld** — heightmap surface + upward nudge over obstructions.
- **Nether** — downward column scan (y 121→33) for two free blocks above a
  sturdy, hazard-free floor; unsafe columns refuse the run rather than
  guessing.
- **Any other dimension** — no adapter → expeditions refuse to start.

Choose columns walkable on foot and within a few hundred blocks of spawn.

## Implemented features (milestone 3)

- **Full playable loop** — terminal → ordered checkpoints → exit → token reward,
  in the Overworld and the Nether.
- **Dimension adapters** — `ExpeditionDimensionAdapter` resolves route anchors
  per dimension: Overworld (heightmap), Nether (safe-column scan via pure
  `SafeColumnSearch`), everything else explicitly unsupported. Register new
  adapters via `ExpeditionAdapters.register`.
- **Per-dimension expeditions** — config sections per dimension; the terminal
  starts the expedition matching the player's dimension.
- **Per-player runs** — one active run per player, enforced by the ledger.
- **Server materialized route** — marker blocks placed at run start in the
  run's dimension and removed at run end (kept while another run in the same
  dimension needs them).
- **Server-validated checkpoints** — only the next expected checkpoint advances;
  out-of-order, duplicate and foreign positions are rejected; activation requires
  real block reach.
- **Death failure** — `LivingDeathEvent` fails active runs, no reward.
- **Token rewards** — guaranteed tokens plus an optional time bonus, granted
  exactly once on completion and persisted per player. Balances saturate at
  `Integer.MAX_VALUE` rather than overflowing.
- **Persistence** — runs (incl. resolved route, progress, timing and award),
  token balances and live marker positions in the overworld
  `DimensionDataStorage`. Runs and balances live in **one file**, so a crash
  can only roll both back together — a reward and its `rewarded` flag can
  never disagree.
- **Recovery policy (fail-closed)** — on login and on terminal use, an active
  run is validated against the world: its dimension must exist and every
  marker block must still be present. Valid runs resume; anything else is
  terminated FAILED (no reward) and its surviving markers are cleaned up, so a
  stale run can never strand a player.
- **Malformed-data handling** — each persisted record is validated on load;
  corrupt runs or balances are skipped and logged instead of losing every
  player's state.
- **Orphan markers** — every placed marker is tracked; on server start,
  tracked markers no active run references (e.g. orphaned by a crash) are
  removed.
- **Commands** — `/expeditions balance|status|abandon`.
- **Tests** — 43 unit tests + 12 GameTest in-game tests.

## Verified

- `./gradlew build` — passes; `expeditions-0.1.0.jar` assembled.
- `./gradlew test` — 43 unit tests, 0 failures.
- `./gradlew runGameTestServer` — **12/12 game tests pass** on a real dedicated
  server, driving the genuine `useItemOn` → `useWithoutItem` interaction path:
  - full loop completes with the bonus-inclusive award and marker cleanup,
  - death fails the run with no reward,
  - out-of-order and duplicate checkpoint clicks are rejected,
  - the exit refuses unfinished runs,
  - the Nether expedition resolves inside the safe scan window and its markers
    are placed and removed in the Nether,
  - the End refuses to start a run (unsupported dimension),
  - save→load preserves active runs, progress, terminal states, reward flags
    and balances,
  - malformed run/balance records are skipped without corrupting the rest,
  - a run whose markers vanished fails closed on resume (no reward, cleanup),
  - a valid run resumes on reconnect with progress intact and blocks a second
    concurrent run,
  - a rewarded run cannot be re-completed or re-rewarded after reload,
  - orphaned tracked markers are pruned on startup while live runs keep theirs.
- Earlier manual smoke: dev server boots, `setblock`/`summon` confirmed block
  and item registration.

## Known limitations

- Not yet verified by a **human client session** — game tests drive the real
  server interaction path, but no GUI click-through was performed. Real
  disconnect/reconnect was not simulated either; reconnect behavior is tested
  through `resumeOrFail`, the same path the login handler uses.
- **Crash consistency:** rewards and run state live in one saved-data file, so
  a crash can only roll both back consistently (worst case: a completion paid
  but forgotten — the run is still active or failed on reload, never
  double-paid). Durability beyond Minecraft's own save intervals is not
  guaranteed.
- Concurrent players share the configured marker positions per dimension
  (markers are shared world blocks); each run still validates its own progress
  independently.
- One expedition per dimension; expeditions are config sections, not yet
  datapack-driven, and each dimension needs an adapter to be supported.
- The Nether adapter guarantees a safe marker spot but not a safe *path*
  between markers — routes still need coordinates chosen by hand.
- A permanently-offline player's active run keeps its markers until they next
  log in (recovery policy applies on login) or another run in that dimension
  ends.
- Marker blocks are indestructible by design; the terminal is not protected.
- Placeholder textures.

## Architecture

- `com.expeditions.core` — **pure Java** (no Minecraft imports): `ExpeditionStatus`,
  `ExpeditionDefinition` (dimension, route, rewards incl. time bonus),
  `ExpeditionRoute`/`RouteAnchor`/`Waypoint`, `ExpeditionRun` (lifecycle +
  ordered checkpoint activation + timing + awarded amount), `ExpeditionLedger`
  (per-player runs + token balances), `SafeColumnSearch` (standable-position
  scan used by adapters).
- `com.expeditions.server.dimension` — `ExpeditionDimensionAdapter` +
  Overworld/Nether implementations + `ExpeditionAdapters` registry. This is the
  extension point for dimension support; the lifecycle never assumes terrain.
- `com.expeditions.server` — `ExpeditionManager` (server facade: start/complete/
  fail, per-dimension route resolution, marker placement/removal/tracking,
  `resumeOrFail` recovery, `pruneOrphanMarkers` on start, reward award),
  `ExpeditionSavedData` (ledger + live markers ↔ NBT in overworld
  `DimensionDataStorage`, per-record fault isolation on load),
  `ExpeditionEventHandler` (death + login recovery + startup pruning + commands).
- `com.expeditions.block` — terminal/checkpoint/exit blocks delegating to the
  manager. `com.expeditions.registry` — `DeferredRegister` wiring.
- `com.expeditions.gametest` — `ExpeditionGameTests` (6 tests, batches a–f).
- `ExpeditionsConfig` — NeoForge server config → `ExpeditionDefinition` per
  dimension.

## Next milestones

1. Datapack-driven expedition definitions (route + rewards per JSON) so
   modpacks can add expeditions and dimensions without code.
2. Optional objectives and performance scoring beyond the time bonus.
3. `ExpeditionRouteGenerator` for procedurally anchored routes.
4. Token sink (shop or item claim) and additional reward variety.
5. Multiplayer parties / shared runs.
