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
2. Right-click it. The expedition configured for your current dimension
   starts: a route is resolved at a remote landing spot, marker blocks
   appear there, and an **Expedition Portal** spawns above the terminal. In
   unsupported dimensions the terminal reports that no expedition is
   available.
3. Step into the portal. It teleports you to the run's landing spot and
   hands you an **Expedition Compass** that always points at your next
   target — the next checkpoint, then the exit once all are done.
   **Anyone else who steps in while the portal is open joins your party**:
   all members share one run and its progress, and each joiner extends a
   generated route by `expeditions.party.checkpointsPerExtraMember` legs.
   The portal seals (and disappears) when the first checkpoint is hit.
4. Right-click each checkpoint **in order** — out-of-order and duplicate
   activations are rejected. Any member can activate any checkpoint; the
   progress is shared. Each activation rolls a **challenge** — currently a
   hostile horde that spawns near the checkpoint and hunts the nearest
   member (`[expeditions.challenges]`). The horde type rolls from the
   checkpoint biome's own natural spawn table — dimension- and
   biome-appropriate, including mod-registered mobs — and carries the
   expedition's declared buffs. The horde is cleaned up when the
   next checkpoint is reached or the run ends; it cannot outlive the
   expedition.
5. After all checkpoints, right-click the **Exit** to complete the run.
   The reward pool scales with party size
   (`expeditions.party.rewardBonusPerExtraMember`) and is distributed per
   `expeditions.party.splitRewards` (default: split evenly). Every member is
   teleported back to the terminal and compasses are removed.
6. Dying drops you out of the party keeping only banked checkpoint tokens;
   the run fails only when
   the last member dies or leaves. `/expeditions abandon` leaves the run the
   same way. Disconnecting does not end a run — it resumes on rejoin (use the
   portal again if you never entered).

Commands: `/expeditions status`, `/expeditions balance`, `/expeditions abandon`.

## Route configuration

Each dimension has an expedition section in the server config
`run/world/serverconfig/expeditions-server.toml` (per world). The Overworld
expedition **generates its route by default** — a fresh meandering walk is
rolled per run, anchored to the player's position at the terminal:

```toml
[expeditions.overworld]
generate = true   # set false to use the authored anchors below instead
tokens          = 8     # guaranteed tokens, banked per checkpoint reached
bonusTokens     = 4     # extra for finishing within bonusTimeSeconds (0 = off)
bonusTimeSeconds = 300

[expeditions.overworld.generation]
checkpoints = 3      # 1-16 ordered checkpoints per route
minSpacing  = 48     # each leg is 48-96 blocks with up to ±45° turns
maxSpacing  = 96
exitMin     = 96     # exit is another 96-160 blocks past the last checkpoint
exitMax     = 160
maxAttempts = 4      # extra candidate columns retried when an anchor is unsafe (0-8)
entryMin    = 128    # the portal lands 128-256 blocks from the terminal
entryMax    = 256

[expeditions.party]
checkpointsPerExtraMember = 1    # legs appended per member joining (0-4)
rewardBonusPerExtraMember = 0.5  # pool bonus per extra member (0.0-4.0)
splitRewards = true              # false = every member gets the full pool

[expeditions.rewards]
bonusDrops         = true  # item drops alongside tokens on completion
dropValuePerToken  = 1.0   # item-value budget granted per awarded token
maxBonusDrops      = 8     # max stacks a single draw may grant
redeemCost         = 10    # token cost of /expeditions redeem

[expeditions.challenges]
enabled = true                   # roll a challenge on each checkpoint
hordeChance = 1.0                # chance a checkpoint spawns a horde (0.0-1.0)
hordeSize = 4                    # base mob count (1-32)
hordeSizePerExtraMember = 2      # extra mobs per member beyond the first (0-16)
hordeSpawnRadius = 8             # max spawn distance from the checkpoint (2-32)
hordeMob = "minecraft:zombie"    # last-resort mob when neither spawn table nor tag pools apply

# fallback authored route — only used when generate = false
checkpoints = ["48,16", "96,-24", "144,48"]
exit        = "192,0"

[expeditions.nether]
enabled  = true         # false hides the expedition without clearing the route
generate = true         # set false to use the authored anchors below instead
tokens          = 16
bonusTokens     = 8
bonusTimeSeconds = 240

[expeditions.nether.generation]
checkpoints = 3
minSpacing  = 32        # tighter legs than the Overworld — the safe scan
maxSpacing  = 64        # window below the bedrock ceiling is small
exitMin     = 64
exitMax     = 128
maxAttempts = 4
entryMin    = 64        # the portal lands 64-128 blocks from the terminal
entryMax    = 128

# fallback authored route — only used when generate = false
checkpoints = ["24,24", "48,-16", "72,40"]
exit        = "96,0"
```

Positions are X/Z columns; the dimension's **adapter** resolves them to safe
destinations against real block state when a run starts — never on heightmap
values alone:

- **Overworld** — heightmap bounds the search, then the column is validated:
  free marker position and headroom, a sturdy non-hazardous floor (lava,
  magma, cactus, fire and fluids are rejected), inside the world border.
- **Nether** — downward column scan (y 121→33) for two free blocks above a
  sturdy, hazard-free floor; unsafe columns refuse the run rather than
  guessing.
- **Any other dimension** — no adapter → expeditions refuse to start.

Generated anchors may retry up to `maxAttempts` nearby candidate columns
(±16-block ring); authored anchors are strict. Resolution is bounded — at
most `1 + maxAttempts` columns per anchor, one chunk load each — and the
whole route resolves **before** any marker is placed, so a route that cannot
resolve cleanly refuses the start with a message and leaves nothing behind.

Choose authored columns walkable on foot and within a few hundred blocks of
spawn. Larger `minSpacing`/`maxSpacing` values make longer routes but cost
more travel time; `maxAttempts` trades resilience against unsafe terrain for
a few extra chunk lookups per run start.

## Datapack expeditions

Modpacks can define expeditions in datapacks at
`data/<namespace>/expeditions/<name>.json` (reloadable with `/reload`):

```json
{
  "display_name": "Frozen Waste Trial",
  "dimension": "minecraft:overworld",
  "difficulty": 3,
  "checkpoints": [[48, 16], [96, -24], [144, 48]],
  "exit": [192, 0],
  "tokens": 25,
  "bonus_tokens": 5,
  "bonus_time_seconds": 60
}
```

Required: `dimension`, `checkpoints` (non-empty `[x,z]` pairs), `exit`.
Optional: `display_name` (defaults to the file path), `difficulty`, `tokens`,
`bonus_tokens`, `bonus_time_seconds` (all default 0), `objectives`,
`modifiers`.

**Objectives** — an optional `objectives` array adds goals that pay their own
`bonus_tokens` on successful completion (each at most once per run; a
`"required": true` objective must be finished before the exit accepts the
run):

```json
"objectives": [
  {"id": "midpoint", "description": "Reach the middle checkpoint",
   "type": "visit_checkpoint", "checkpoint": 2, "bonus_tokens": 3},
  {"id": "tribute", "description": "Bring 4 bones",
   "type": "collect_items", "item": "minecraft:bone", "count": 4,
   "bonus_tokens": 5, "required": true},
  {"id": "firewood", "description": "Gather 2 logs",
   "type": "collect_items", "tag": "minecraft:logs", "count": 2,
   "bonus_tokens": 2},
  {"id": "speedy", "description": "Finish within a minute",
   "type": "time_limit", "seconds": 60, "bonus_tokens": 7},
  {"id": "cargo", "description": "Deliver the artifact",
   "type": "recover_artifact", "checkpoint": 2, "bonus_tokens": 6,
   "required": true},
  {"id": "siege", "description": "Hold the device",
   "type": "defend_device", "checkpoint": 1, "seconds": 60,
   "bonus_tokens": 15, "required": true}
]
```

Every objective needs a unique `id` and a `type`; `description` (defaults to
the id), `required` (default false) and `bonus_tokens` (default 0) are
optional. `visit_checkpoint` takes `checkpoint` (1-based route index),
`collect_items` takes exactly one of `item` or `tag` plus `count`,
`time_limit` takes `seconds`, `recover_artifact` takes `checkpoint`
(the checkpoint that issues the run's physical artifact item), and
`defend_device` takes `checkpoint` (the checkpoint that deploys the
device) plus `seconds` (the siege duration).

Defense rules: activating the device checkpoint places a destructible
`expeditions:expedition_device` above it (only into replaceable space —
it can never overwrite another run's marker) and starts a bounded siege:
waves spawn up to `defenseMobCap` live mobs that pathfind to the device
and chip it, while the run record holds the authoritative health
(`defenseDeviceHp`, `defenseWaveSeconds`, `defenseWaveSize` configure it).
Survive the timer → objective completes, device stands down, siege is
called off. Destroyed device → the objective is marked failed exactly
once: required fails the run, optional just never pays. A device that
vanishes without a tracked hit (creative break, `/fill`) counts as
destroyed; a siege in progress when the run ends is stood down with the
other markers.

Artifact rules: activating the cargo checkpoint hands the activator a
run-bound `ExpeditionArtifactItem` exactly once (`run_id` data component);
the item follows normal inventory/death/drop behavior while the run is
active. At the exit, any online member must be holding it — an artifact
bound to another run is refused and left untouched, a refused click never
consumes it, and the item is consumed only after the run actually
completes. Run end (complete or fail) strips its artifacts, the login
sweep removes artifacts bound to dead runs, and offline members can't
hand over cargo. Configured
built-in expeditions carry no objectives; datapacks are the configuration
surface.

**Modifiers** — an optional `modifiers` object declares environmental and
combat effects for the expedition's lifetime:

```json
"modifiers": {
  "weather": "thunder",
  "player_effects": [{"id": "minecraft:speed", "amplifier": 1}],
  "horde_effects": [{"id": "minecraft:strength"}]
}
```

`weather` (`clear`|`rain`|`thunder`) is forced on the expedition's dimension
at run start and cleared at run end (a no-op in dimensions without weather,
and only cleared when no other active run still wants it). `player_effects`
apply to each party member for the run's duration — on start and on joining
through the portal — and are stripped at run end (other effects are
untouched). `horde_effects` are permanent buffs baked into every horde mob at
spawn. Effect ids validate against the mob-effect registry, so modded
effects work; `amplifier` defaults to 0. A bad weather name or unknown
effect id fails the whole definition loudly.

**Generated routes** — replace `checkpoints`/`exit` with a `generate` block for
a fresh route rolled per run, anchored to the terminal the player activated:

```json
{
  "dimension": "minecraft:overworld",
  "generate": {
    "checkpoints": 3,
    "min_spacing": 32,
    "max_spacing": 64,
    "exit_min": 48,
    "exit_max": 96,
    "max_attempts": 4
  },
  "tokens": 30,
  "bonus_tokens": 10,
  "bonus_time_seconds": 300
}
```

Each leg is 32–64 blocks with up to ±45° turns (a meandering outward path);
the exit is another 48–96 blocks past the last checkpoint. `max_attempts`
(0–8, default 4) bounds how many nearby candidate columns the adapter retries
when a generated anchor fails safety validation. Generated anchors are
resolved by the dimension adapter like authored ones — same safety checks,
plus the jitter fallback.

Precedence and rules:

- A datapack definition for a dimension **overrides** the config expedition.
- With multiple datapack definitions for one dimension, the lowest
  `<namespace>:<name>` id wins.
- Expeditions still require a dimension **adapter** — a datapack entry cannot
  enable expeditions in an unsupported dimension.
- Malformed files are skipped and logged; they never break other definitions.

### Reward pools

Completion bonus drops and `/expeditions redeem` draw from the item tag
`expeditions:reward_items/<dimension path>` — e.g. `reward_items/overworld`,
`reward_items/the_nether` — falling back to `reward_items/default` for
dimensions without a pool. They are plain tags, so any datapack or mod can
extend them (`"replace": false` merge) or add pools for adapter-supported
modded dimensions:

```json
{
  "replace": false,
  "values": ["mymod:some_supply", "#c:gems"]
}
```

### Horde pools

Hordes are **smart by default**: the pool is the checkpoint biome's own
natural monster spawn table (spawn weights included), so a desert checkpoint
rolls husks, the Nether rolls its natives, and modded biomes/dimensions roll
the mod's registered mobs — nothing to configure. Water/lava-spawn types are
filtered out since the horde fights on land.

Explicit control is still available: a non-empty
`expeditions:horde_mobs/<dimension path>` entity tag **replaces** the natural
pool for that dimension (plain datapack, `"replace": false` merges). If a
dimension's biomes spawn nothing at all, `horde_mobs/default` and finally
the config `hordeMob` id are used.

Draw weights come from `ItemValuator`'s stat score, so pool items rank by
their own components — no per-item rarity table to maintain.

## Supporting a modded dimension

The expedition lifecycle (markers, checkpoints, portal, challenges, rewards,
persistence) never branches on which dimension a run is in — dimension
support reduces to one seam: the **adapter**. Three outcomes per dimension:

- **Supported** — an adapter is registered; any datapack definition (or
  built-in config section) targeting that dimension can start runs.
- **Requires integration** — a dimension mod needs a small adapter class.
- **Unsupported** — no adapter; terminals refuse cleanly, nothing is placed.
  Registry presence alone never implies support.

An addon (or a dimension mod itself) registers an adapter at any point —
lookup happens when a run starts:

```java
ExpeditionAdapters.register(new ExpeditionDimensionAdapter() {
    @Override
    public ResourceKey<Level> dimension() {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("mymod", "mydim"));
    }

    @Override
    public Optional<BlockPos> resolveAnchor(ServerLevel level, RouteAnchor anchor) {
        // Scan for a safe marker spot on real block state. Reuse the shared
        // contract — DestinationChecks.isFree / isSturdyFloor / isHazard —
        // plus the pure-domain SafeColumnSearch for cave-like dimensions.
        // Return Optional.empty() rather than guessing an unsafe spot.
    }
});
```

Then ship (or let modpacks ship) a datapack expedition whose `"dimension"`
is `"mymod:mydim"` — authored anchors or a `generate` spec both work, since
generated anchors resolve through the same adapter. Adapter responsibilities:
respect the dimension's build limits and world border, load the column's
chunk, and validate on real block state — not heightmap assumptions.

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
- **Death failure** — `LivingDeathEvent` fails active runs; members keep
  only tokens already banked at checkpoints, nothing else.
- **Token rewards** — the guaranteed `tokens` are banked per checkpoint
  reached (a balanced split paid into member balances immediately, so they
  survive failure, death and restarts). The completion award — time bonus
  plus each objective's bonus — is the at-risk part: extraction only tops
  members up to their full share, granted exactly once and persisted per
  player. Balances saturate at `Integer.MAX_VALUE` rather than overflowing.
- **Objectives** — optional or required goals (visit a checkpoint, collect
  items by id or tag, finish within a time limit) declared per expedition.
  Progress is tracked per run, survives save/load, and each objective's bonus
  pays at most once — only on successful completion.
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
- **Datapack definitions** — expeditions loadable from
  `data/*/expeditions/*.json`, hot-reloadable via `/reload`, taking precedence
  over config per dimension.
- **Generated routes** — both built-in expeditions generate a fresh route
  per run by default (config-tunable, authored fallback via
  `generate = false`); datapack `"generate"` specs do the same for any
  adapter-supported dimension. `RoutePlanner` rolls a seeded random walk from
  the player's position; `RouteSpec` keeps authored and generated routes
  distinct; restored runs always persist the resolved static form.
- **Dimension integrity** — every run's dimension comes from its definition
  and is persisted; checkpoint/exit interactions are rejected when the
  player isn't in the run's dimension, and portal matching requires a
  dimension match. A restored run whose dimension can't resolve fails
  closed through the standard recovery path.
- **Destination safety** — adapters validate candidates on real block state:
  free position + headroom, sturdy non-hazardous floor (lava/magma/cactus/
  fire/fluids rejected), inside the world border. Generated anchors retry a
  bounded `maxAttempts` ring of nearby columns; failure refuses the start
  cleanly before any marker or run exists.
- **Expedition compass** — granted on portal entry; the server sets its
  `LODESTONE_TRACKER` target so the needle follows the next checkpoint, then
  the exit, and it is removed when the run ends (complete, fail or abandon).
- **Portal entry + parties** — a run starts at the terminal and spawns a
  portal above it; stepping in teleports the player to a remote, validated
  landing spot the route was generated around. Anyone entering while it's
  open joins the party — one shared run, shared progress. The first
  checkpoint seals the party and removes the portal; run end teleports
  members back to the terminal.
- **Configurable party scaling** — `[expeditions.party]`: each member beyond
  the first appends `checkpointsPerExtraMember` legs to a generated route
  (difficulty), the reward pool gains `rewardBonusPerExtraMember`×base per
  extra member, and `splitRewards` chooses even split vs. full payout each.
  Per-run in-game selection at the terminal is future work; the domain
  (`PartyScaling`, `RouteGeneration`) takes its values from config today.
- **Checkpoint challenges** — each activation rolls a challenge
  (`CheckpointChallenge` in the domain, kinds extensible): a horde of the
  configured mob spawns in a ring around the checkpoint, hunts the nearest
  party member, and is tracked on the run by entity UUID. The previous
  challenge is cleaned up when the next checkpoint is reached and whatever
  remains is cleaned up at run end — entities in unloaded chunks are
  force-loaded first, and anything still pending keeps its tracking entry
  so a periodic sweep (once a second) retries when it materializes. The
  sweep only mops up ENDED runs; an active run's tracked entities are its
  live horde and are never swept.
- **Bonus drops** — completed expeditions also grant item supplies: each
  member's token share converts to an item-value budget drawn from the run's
  own dimension's reward pool, so Nether completions pay Netherish loot.
- **Token redeem** — `/expeditions redeem` spends the configured token cost
  for one supply draw from the pool of the dimension the player stands in.
- **Tier-weighted pools** — drop chances weight by `ItemValuator`, which
  scores items from their own data components (rarity, food, durability,
  attribute modifiers) plus the NeoForge `c:` convention tags (gems, ingots,
  raw materials) — modded items rank on the same scale with no per-item
  table. Pool contents live in `expeditions:reward_items/<dimension>` item
  tags (`default` fallback), so modpacks and adapters extend them as plain
  datapack tags.
- **Per-run options** — clicking a terminal opens the expedition menu: pick
  any startable expedition (including other dimensions — the portal stays
  at home while the route resolves remotely), a difficulty tier
  (relaxed→brutal, scaling horde size and rewards), route length and a
  bonus-time window, then hit start. `/expeditions start <id> [difficulty]
  [checkpoints] [time%]` does the same without a GUI. Options persist with
  the run.
- **Expedition modifiers** — datapack `"modifiers"` force weather on the
  expedition's dimension, apply mob effects to party members for the run's
  duration, and bake permanent buffs into spawned horde mobs. Horde types
  roll from the spawn site's natural monster table — dimension-, biome- and
  mod-aware with no configuration; `expeditions:horde_mobs/<dimension>`
  entity tags override explicitly.
- **Run summary** — every member gets a structured end-of-run breakdown:
  checkpoints and objectives done, tokens banked per checkpoint, the
  extraction top-up and total on success; on failure the recorded cause
  (death, abandon, device destroyed, site or dimension lost) plus credits
  kept and what was still at risk.
- **Commands** — `/expeditions balance|status|abandon|redeem|start`.
- **Assimilation layer** (M15 prototype) — `expeditions:assimilation_layer`,
  a thin harvestable membrane that attaches to any sturdy face of a solid
  block (all six directions) and renders the covered face grayscale: the
  client renderer desaturates the face's real sprite at runtime, so the
  underlying block keeps its detail and shading but loses its color —
  without modifying, replacing, or storing the covered block's state.
  Non-sturdy surfaces (air, fluids, fences, slabs, leaves) refuse
  placement; losing the support removes the layer silently. Breaking it
  by hand drops exactly one `expeditions:assimilation_essence`
  (placeholder progression item); creative breaks, support loss and
  world-edit removal drop nothing.
- **Tests** — 106 unit tests + 70 GameTest in-game tests.

## Verified

- `./gradlew build` — passes; `expeditions-0.3.0.jar` assembled.
- `./gradlew test` — 106 unit tests, 0 failures.
- `./gradlew runGameTestServer` — **70/70 game tests passed** on a real dedicated
  server, driving the genuine `useItemOn` → `useWithoutItem` interaction path:
  - full loop completes with the bonus-inclusive award and marker cleanup,
  - death fails the run keeping only banked checkpoint credits,
  - guaranteed tokens bank at each checkpoint activation and survive
    failure, completion tops members up to the full share without
    double-paying, a dropped party member keeps only secured credits, and
    the secured totals survive save→load,
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
  - orphaned tracked markers are pruned on startup while live runs keep theirs,
  - datapack expedition JSON parses into the domain (rewards, bonus ticks),
    shadows the config expedition at the terminal, and malformed files are
    skipped without breaking valid ones,
  - a `"generate"` datapack expedition produces a real route with placed
    markers that clean up on run end,
  - the expedition compass is granted on start, retargets to the next
    checkpoint and finally the exit, and is removed on completion or failure,
  - the built-in Overworld expedition generates its route by default with
    safety-validated destinations, and a hazardous destination refuses the
    run start entirely,
  - the portal spawns above the terminal, teleports to the remote entry and
    grants the compass; party members join through it, extend the route,
    share progress, split the scaled reward, return to the terminal on
    completion, and drop out individually on death,
  - checkpoint activation spawns a tracked horde that hunts a party member;
    reaching the next checkpoint discards it and rolls a fresh one, and run
    end discards whatever remains,
  - a same-coordinates checkpoint in the wrong dimension cannot advance a
    run, while the genuine Nether checkpoint still activates — and the
    Nether expedition generates its route by default,
  - a persisted run whose dimension no longer exists fails closed on resume,
  - objectives progress and pay: checkpoint visits, exact-item and tag
    collection (accumulating, clamped, latched), and a completed time limit —
    the payout is base + each bonus exactly once,
  - untouched optional objectives never block or pay, a required objective
    gates the exit until done, objective progress survives save→load, and
    malformed objective definitions are skipped at reload.
  - the cargo checkpoint issues the run-bound artifact exactly once,
    carrying it through the exit completes the delivery and consumes the
    item, an artifact bound to another run is refused without being
    consumed, a failed run strips its artifact, dead-run artifacts are
    swept on login, and the issued latch survives save→load.
  - a defense checkpoint deploys the device only at its declared stage,
    surviving the siege completes the objective and pays the bonus,
    destroying a required device fails the run exactly once with the siege
    called off, an optional device's destruction leaves the run completable,
    malformed defense objectives are rejected, and defense state survives
    save→load.
  - the assimilation layer attaches to all six faces of a solid support and
    records its direction, refuses non-sturdy supports (fence, slab,
    leaves), survives a sturdy support swap, is silently removed on support
    loss with no drops, drops exactly one essence on survival harvest
    (none in creative), leaves the support block untouched, and exposes a
    1/16 collision membrane with a 2/16 selection outline per direction.
  - item valuation ranks tiers correctly (junk < food < gem < equipment),
    completion grants bonus drops from the run's dimension pool, redeem
    refuses without enough tokens and pays a Nether draw in the Nether,
  - per-run options apply end-to-end: a Nether expedition started from an
    Overworld terminal resolves its route remotely with the portal left at
    home, checkpoint-count and difficulty modifiers reshape the route and
    payout, options survive save/load, the terminal menu's start button
    launches the run, and `/expeditions start` takes the same modifiers.
  - expedition modifiers apply and clean up: member effects land at start and
    are stripped at run end, forced weather sets and clears on the dimension,
    the horde rolls its type from the dimension's entity-tag pool and every
    member carries the declared buffs, modifiers survive save→load, and
    malformed modifier definitions are skipped at reload.
- Earlier manual smoke: dev server boots, `setblock`/`summon` confirmed block
  and item registration.

## Known limitations

- The assimilation layer's grayscale covers **only the attached face** — a
  covered block's other faces stay colored (by design: the layer is per-face).
  Blocks rendered purely by custom renderers/block entities show the membrane
  without the faithful desaturated skin, and animated sprites desaturate from
  their first frame. A shader-based selective desaturator is the documented
  upgrade path if face coverage proves insufficient.
- The assimilation layer's visuals are **client-only and not covered by the
  GameTest server** — verified mechanically (placement, survival, drops,
  shapes), not visually. Manual check: `./gradlew runClient`, place the layer
  on grass/stone/ore faces from all six directions and reload the chunk.
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
  independently. Party runs share one progress counter — members race the
  same checkpoints cooperatively.
- An offline member when the run ends keeps their compass until next login
  (it is swept on join) and is not teleported back.
- One expedition per dimension wins (lowest datapack id or the config
  section); each dimension still needs an adapter to be supported.
- The Nether adapter guarantees a safe marker spot but not a safe *path*
  between markers — generated legs can still span lava lakes or open air.
- A permanently-offline player's active run keeps its markers until they next
  log in (recovery policy applies on login) or another run in that dimension
  ends.
- Marker blocks are indestructible by design; the terminal is not protected.
- Bonus-drop and redeem draws are score-weighted random — budget size sets
  the tier band, not the exact items. A member offline at completion keeps
  only their tokens; item drops are granted to online members only.
- Challenge entities whose chunk was unloaded at cleanup time keep their
  tracking entry until the once-a-second sweep can reach them — worst case
  a horde survives until its chunk reloads.
- Placeholder textures.

## Architecture

- `com.expeditions.core` — **pure Java** (no Minecraft imports): `ExpeditionStatus`,
  `ExpeditionDefinition` (dimension, route, rewards incl. time bonus),
  `RouteSpec`/`ExpeditionRoute`/`RouteGeneration`/`RoutePlanner`,
  `RouteAnchor`/`Waypoint`, `ExpeditionRun` (lifecycle +
  ordered checkpoint activation + timing + awarded amount + tracked
  challenge entities), `ExpeditionLedger` (per-player runs + token balances),
  `SafeColumnSearch` (standable-position scan used by adapters),
  `CheckpointChallenge` (challenge kinds + the roll), `PartyScaling`.
- `com.expeditions.server.dimension` — `ExpeditionDimensionAdapter` +
  Overworld/Nether implementations + `ExpeditionAdapters` registry. This is the
  extension point for dimension support; the lifecycle never assumes terrain.
- `com.expeditions.server` — `ExpeditionManager` (server facade: start/complete/
  fail, per-dimension route resolution, marker placement/removal/tracking,
  `resumeOrFail` recovery, `pruneOrphanMarkers` on start, checkpoint
  challenges + tracked-entity cleanup, reward award),
  `ExpeditionSavedData` (ledger + live markers ↔ NBT in overworld
  `DimensionDataStorage`, per-record fault isolation on load),
  `ExpeditionEventHandler` (death + login recovery + startup pruning + commands),
`ItemValuator` (stat-driven item score for reward weighting), `ItemRewards`
(dimension-aware pools from `reward_items/<dim>` tags + score-weighted
draws), `ExpeditionMenu` (vanilla chest-GUI terminal screen — server-only,
no custom menu type).
- `com.expeditions.block` — terminal/checkpoint/exit blocks delegating to the
  manager. `com.expeditions.registry` — `DeferredRegister` wiring.
- `com.expeditions.server.data` — `ExpeditionDefinitionLoader`
  (`SimpleJsonResourceReloadListener`) + `ExpeditionDefinitions` (immutable
  snapshot, datapack-over-config precedence).
- `com.expeditions.gametest` — `ExpeditionGameTests` (60 tests, batches a–z + art + def).
- `com.expeditions.item` — `ExpeditionCompassItem` (a plain `Item`; its needle
  is driven server-side via `LODESTONE_TRACKER`, so it does not extend
  `CompassItem` and never competes with block interactions) and
  `ExpeditionArtifactItem` (run-bound mission cargo via `ModDataComponents.RUN_ID`).
- `com.expeditions.client` — `ExpeditionsClient` (client-only `@Mod` with
  `dist = CLIENT`) registers the `angle` item property over the tracker target.
- `ExpeditionsConfig` — NeoForge server config → built-in `ExpeditionDefinition`
  per dimension, used when no datapack definition covers the dimension.

## Next milestones

1. Party-size options at the terminal and per-member objective variants.
2. Challenge variety beyond the horde (the `CheckpointChallenge` seam).
