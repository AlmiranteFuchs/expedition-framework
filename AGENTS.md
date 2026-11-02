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
  `com.expeditions.gametest`, template `data/expeditions/structure/arena.nbt`).
  The task first deletes `run/world` — the world otherwise persists between
  runs and stale blocks corrupt later results.
- Smoke iteration: `./gradlew runGameTestServer -PgtNamespaces=expeditions_smoke`
  runs only `ExpeditionSmokeTests` (~20s vs ~90s full). Use it mid-change;
  run the full suite before committing.
- Feature namespaces: `-PgtNamespaces=<ns>` (comma-separated) runs only tests
  whose `@GameTest(templateNamespace)` matches. `expeditions_smoke`=1-test
  loop; `expeditions_def`=defense; `expeditions_mic`=microbiome;
  `expeditions_asm`=assimilation; `expeditions_prog`=campaign/sigil;
  `expeditions_art`=artifacts; `expeditions_eco`=economy;
  `expeditions_party`=party/portal; `expeditions_obj`=objectives;
  `expeditions_loot`=drops; `expeditions_opts`=options;
  `expeditions_mod`=modifiers; `expeditions_horde`=nether horde;
  `expeditions_dim`=automatic dimensions; `expeditions`=core lifecycle.
  `expeditions_compass`=compass attunement.
  Each namespace's `arena.nbt` is generated at
  build time from `data/expeditions/structure/arena.nbt` — register new
  namespaces in `gameTestStructureNamespaces` (build.gradle), never copy
  the nbt by hand.
- Dev server: `./gradlew runServer`; dev client: `./gradlew runClient`
- Installed dimension-mod checks: `./gradlew runGameTestServer -PbinaryMinecraft -PgtNamespaces=expeditions_modded_horde`
  uses actual Aether/Bumblezone/Twilight
  biome data when installed (optional probes skip absent biomes). Core suite
  with installed mods that reject mock-player packets: add `-PisolatedGameTests`
  to use `run/gametest-isolated`; its own `world` is cleaned before each run.
- Datagen: `./gradlew runData` → `src/generated/resources`
- Guidebook: Patchouli `1.21.1-92-NEOFORGE` is a required runtime library.
  `python3 tools/gen_guidebook.py` generates its categories/entries and hidden
  advancement gates from `assets/expeditions/journal/chapters.json`.
  Book metadata belongs in `data/expeditions/patchouli_books/field_guide`;
  contents belong in the matching `assets/` directory. `./gradlew alphaBundle`
  stages both required jars in `build/alpha`.
- Textures: `python3 tools/gen_textures.py` regenerates all asset PNGs
  (16×16, bone/ember ramps, top-left light, ordered dither — no noise)
  and the heartwood block face models via `tools/heartwood_art.py`.
  `python3 tools/preview_heartwood.py` builds a before/after board in `build/`.

Corporate proxy: TLS interception requires importing the corporate root CA into
`$JAVA_HOME/lib/security/cacerts` before dependency resolution works.

## Architecture boundaries

- `com.expeditions.core` — **pure Java domain** (status, run, definition, route,
  ledger, `SafeColumnSearch`). No Minecraft imports allowed; unit-testable
  without the game.
- `com.expeditions.server.dimension` — `ExpeditionDimensionAdapter` interface +
  Overworld/Nether implementations + `ExpeditionAdapters` registry. This is the
  dimension extension point: adapters own "can a route resolve here and where";
  lifecycle code must never assume terrain. Unregistered loaded dimensions use
  `AutomaticDimensionAdapter` (bounded column scan, ceiling-aware); explicit
  adapters override it. Unsafe routes still refuse before marker placement.
- `com.expeditions.server` — server-authoritative coordination
  (`ExpeditionManager`), persistence (`ExpeditionSavedData`, overworld
  `DimensionDataStorage`), game-bus hooks (`ExpeditionEventHandler`).
  `MobSpawns` is the shared horde/wave placement (bounded column scan
  around the event's elevation — never the heightmap, which names the
  roof in enclosed dimensions — plus a bounding-box `noCollision` check
  and a deterministic ring sweep fallback). `Microbiome` gates and seeds
  the assimilation-heart spread; `clearedDimensions` in the same
  SavedData file is the per-dimension kill seam (M12b flips it).
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
- Two-tier rewards: `securedTokens` per member are banked into the ledger
  at each checkpoint activation (`checkpointTokenPortion`, balanced split
  of guaranteed `tokens`) and survive failure/death/restart; completion
  only tops members up to the full share (`max(0, share − secured)`).
  `tokensAwarded`/`markRewarded` keep recording the FULL per-member share
  value — never the just-paid top-up — so bonus-drop budgets and the
  COMPLETED-only restore invariant stay intact. Party seals at the first
  checkpoint, so all members are present for every credit.

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
- Rewards: guaranteed `tokens` banked per checkpoint + at-risk `bonusTokens`
  and objective bonuses settled at extraction; totals computed by
  `ExpeditionDefinition.totalReward(elapsedTicks, completedObjectiveIds)`.
- Marker blocks are placed at run start and removed at run end in the run's
  own dimension, per position — a marker is only removed when no still-active
  run references that exact position (`isReferencedByActiveRun`), so a leaked
  run can never freeze a whole dimension's cleanup. Indestructible by design.
- `recover_artifact` objectives bind a physical `ExpeditionArtifactItem` to
  the run via `ModDataComponents.RUN_ID`: issued once at the declared
  checkpoint (`artifactIssued` latch), delivered by holding it at the exit
  click (consumed only after `complete()` succeeds), stripped at run end
  and swept when its run is dead. Completion mirrors possession — losing
  the item unlatches the objective.
- Expedition compass: `ExpeditionCompassItem` (plain `Item`, NOT `CompassItem`
  so it never competes with block `useItemOn`) granted on portal entry and
  removed on any run end (also swept on login if a run ended while offline);
  the server sets `DataComponents.LODESTONE_TRACKER` to the next target and
  `ExpeditionsClient` (`@Mod dist = CLIENT`) registers the `angle` property
  that reads `tracker.target()`.
  `CompassResonance` renders a floating elevation ember in first-person hand
  space; `CompassElevation` is the pure signed-height rule (two-block level
  tolerance). Right-click starts a 24-tick reading with a 40-tick cooldown;
  no useItemOn override, so checkpoint block interactions still take priority.
  All 32 angle models use the mod's generated heartwood art and shared housing
  display transforms. Invalid or other-dimension trackers stay dormant.
- Portal + party lifecycle: `startRun(player, terminalPos)` resolves the
  route at a remote landing spot (`entryMin/Max` travel distance for
  generated routes; authored routes land on the first checkpoint) and spawns
  `EXPEDITION_PORTAL` above the terminal (tracked in liveMarkers).
  `enterPortal` adds non-members to `run.party` (sealed by the first
  checkpoint: `portalOpen()`), extends generated routes by
  `party.checkpointsPerExtraMember` legs per join, grants the compass and
  teleports to `run.entry`. Orphaned/sealed portal blocks self-remove.
- `ExpeditionRun` has a mutable-but-invariant party (never empty — last
  member can't `leaveParty`, the run fails instead), plus persisted
  `terminal`/`portal`/`entry` waypoints. Run end (`endRun`) removes portal +
  markers, strips compasses and teleports online members back to terminal.
- Rewards: `PartyScaling.scaledTotal(base, size, bonusPerExtraMember)` then
  `memberShare(..., split)` from `[expeditions.party]` config — default
  scaled pool split evenly. `markRewarded` stores the per-member share.
- Death drops the member out of the party (`leaveParty`, no reward); run
  fails only when the last member leaves/dies. Disconnect keeps resumable.
- Checkpoint challenges: `activateCheckpoint` discards the previous
  challenge's entities (centered on the checkpoint before this one), then
  rolls `CheckpointChallenge` — `HORDE` spawns `[expeditions.challenges]`
  mobs in a ring, `setPersistenceRequired`, targeting the nearest online
  member; UUIDs are tracked on the run (persisted).
- Entity visibility caveat: `level.getEntity(uuid)` only covers the visible
  entity lookup — entities in sections whose chunk isn't entity-ticking are
  parked (mock players produce NO tickets). `level.getEntities().get(AABB)`
  queries section storage but still requires the section be accessible —
  force-load the covered chunks first (`level.getChunk`), like cleanup does.
  In tests: `findEntity` reads `entityManager.sectionStorage` via reflection
  to see pending entities deterministically; `setChunkForced` + `pollUntil`
  on `level.getEntity(id)` waits for real promotion; keep horde-test players
  in CREATIVE or the force-targeted mobs kill them mid-poll.
- Cleanup/sweep: `untrackChallengeEntity` only removes a UUID once actually
  discarded; entities pending in unloaded chunks keep tracking so the
  once-a-second `sweepChallengeEntities` (ServerTickEvent) retries. The
  sweep MUST only process ENDED runs — an active run's tracked entities are
  its live horde; discarding them is the challenge-killing bug this fixed.
- Generated routes get up to `GENERATED_ROUTE_REROLLS` (8) fresh seeds in
  `startRun` — one bad random walk shouldn't refuse when a re-roll resolves.
- Objectives (milestone 6): `ExpeditionObjective` (stable id, description,
  `required`, kind-specific params, `bonusTokens`) + `ObjectiveKind`
  (`VISIT_CHECKPOINT`, `COLLECT_ITEMS`, `TIME_LIMIT`) on the definition;
  per-run `ObjectiveState` (progress + latched completed) lives on
  `ExpeditionRun` and is persisted with the run record. Visits complete inside
  `activateCheckpoint` (returns the newly-completed list for messages);
  `ItemEntityPickupEvent.Post` → `manager.onItemPickup` feeds collection
  (server-authoritative, any member counts toward the shared run objective);
  timed objectives re-evaluate on each `completeRun` attempt before
  `complete()` — a refused first click must not latch a stale bonus.
  `complete()` refuses while `missingRequiredObjectives()` is non-empty; the
  exit block surfaces those as a distinct message. Reward =
  `definition.totalReward(elapsed, completedObjectiveIds)` — base unchanged,
  each objective bonus at most once, failed runs pay nothing.
- Objective caveat: `visit_checkpoint` objectives always complete on a
  successful run (every checkpoint must be activated anyway) — they're for
  bonus flavor, not optional paths. Only `collect_items` and `time_limit`
  can go unfinished.
- Token economy (milestone 7): `ExpeditionLedger.spendTokens` is the atomic
  sink (deducts only when the balance covers the full cost; callers
  `setDirty()`; negative rejected, zero succeeds). Two consumers: completion
  `grantBonusDrops` (online members only, run's dimension pool) and
  `/expeditions redeem` (`redeemTokens` — pool of the dimension the player
  stands in; checks the pool is non-empty BEFORE spending). Pools are the
  `expeditions:reward_items/<dimension path>` item tags + `default`
  fallback — datapack surface; don't add per-item tables.
- `ItemValuator` scores a stack from data components + NeoForge `c:` tags —
  modded items rank on the same scale automatically. `ItemRewards.draw`
  turns a value budget (tokens × `dropValuePerToken`) into weighted stacks
  that fully consume it (cheapest item once as floor, `maxBonusDrops` cap).
  Draw weights are random by design; the budget is the deterministic part.
- Per-run options (milestone 8): `ExpeditionOptions` (expedition id,
  `Difficulty` tier, checkpoints override, bonus-time percent) is stored on
  the run and persisted. `portalDimension` records the terminal's home
  dimension — it differs from the run's dimension for remote picks, and all
  portal/terminal cleanup+teleport paths must use it (not the run dim).
  `listStartable()` = datapack + unshadowed config defs filtered by
  loaded-dimension + adapter — the menu and `/expeditions start` share it.
  Terminal click opens `ExpeditionMenu` (a `ChestMenu` subclass used as
  read-only slot-sync transport for the custom `ExpeditionScreen` registered
  through `ModMenus`; its client factory never touches a server manager).
  The container is display-only; `clicked` consumes option-row
  slots, `quickMoveStack` blocks insertion). The menu preselects the
  terminal's home-dimension expedition — sorted id order ≠ player intent.
  `pruneOrphanMarkers` must snapshot `liveMarkers` before iterating
  (untrack drops empty keys mid-loop — a real CME once markers span two
  dimension keys), and `isReferencedByActiveRun` matches portals against
  `portalDimension`, not the run's dimension.
- Modifiers (milestone 9): `ExpeditionModifiers` on the definition —
  `WeatherModifier` forced on the run's dimension at start and cleared at
  end ONLY when no other active run in that dimension still wants weather;
  `playerEffects` applied to members at start/`enterPortal` with INFINITE
  duration and stripped at `endRun` (match specs exactly — never remove
  effects the expedition didn't grant); `hordeEffects` baked into horde mobs
  at spawn. Effect ids validate against the registry at parse; runtime
  lookups degrade to no-ops. Mob pools: `HordePools.pick(level, pos, dim, rng)`
  resolves smart-first — all of the biome's non-friendly spawn categories
  (including modded categories), then CREATURE entries implementing
  `NeutralMob` only when no eligible hostiles exist. Passive livestock are
  excluded. Neutral checkpoint/boss mobs receive persistent anger toward
  their party target. Spawn pools retain biome weights and filter water/lava
  placements; they are overridden ONLY by
  a non-empty `horde_mobs/<dim>` entity tag, then `horde_mobs/default`, then
  config `hordeMob`. One type is rolled per horde activation (thematic), not
  per mob. Before settling a wave's type, `MobSpawns.trySpawnFromPool` retries
  up to 16 distinct weighted candidates if an oversized body cannot fit.
  Retries stay within the same resolved pool, including explicit tag overrides;
  the first positioned body is reused, not thrown away. Siege waves share this.
- `cleanupMarkers` removes per position via `isReferencedByActiveRun`, NOT
  the old `anyOtherActiveRunInDimension` dimension-wide guard — that guard
  let one leaked active run poison marker cleanup for the whole dimension,
  and leftover authored-route markers then refused the next run's
  resolveAnchor (found via GameTest cascade). Same guard now protects the
  portal removal in `endRun`.
- `ServerLevel.isRaining()` lerps the visual level per tick — assert
  `level.getLevelData().isRaining()` for the authoritative forced-weather
  flag in tests.
- `ExpeditionMenu.start` catches `IllegalStateException` from `startRun` into
  a chat message AND a warn log — without the log, start refusals are
  invisible to tests and server admins (that hid the cascade above).
  `StringArgumentType.word()` cannot parse `ns:id` strings (stops at ':') —
  use `ResourceLocationArgument.id()` for id parameters.
- Defense events (M8): `defend_device` objectives deploy
  `EXPEDITION_DEVICE` above the just-activated checkpoint and run a siege
  from `DefenseState` on the run (hp/maxHp/endTick/nextWaveTick, persisted).
  `tickDefenses` (once-a-second ServerTickEvent) handles expiry→complete,
  missing block→destruction (fail-safe), and due waves. `damageDevice` is
  the single hp authority — mobs (`AttackDeviceGoal`) and player punches
  both route through `damageDeviceAt`; destruction clears the state,
  marks the objective failed, then required → `run.fail()` / optional →
  just never pays. The device is a tracked marker — it shares
  `isReferencedByActiveRun`/orphan-prune protection and `removeMarkerAt`
  must recognize it. Placement only takes replaceable space (bounded
  upward search): two runs in one dimension can resolve overlapping route
  positions, so never `setBlock` a marker over an existing block.
- Entity-cleanup searches must span the full column vertically
  (`columnBox`): hordes/siege mobs spawn at each column's surface, which
  can sit far below an elevated checkpoint or device — a cubic
  `center.inflate(radius)` box silently misses them and tracked
  persistence-required mobs leak.
- Assimilation layer (M15 prototype): `AssimilationLayerBlock` attaches to
  one sturdy face (`isFaceSturdy` — the ONLY support rule; glass counts,
  slabs/fences/leaves don't), dies with support via `updateShape` → air.
  Support loss goes through `updateOrDestroy` → `destroyBlock(drop=true)`,
  so "harvest-only drops, silent cleanup" CANNOT be a loot table — use
  `noLootTable()` + grant the drop in `playerDestroy` (survival-break
  only; creative never reaches it).
- Client-only code stays in `com.expeditions.client` under the
  `ExpeditionsClient` `@Mod(Dist.CLIENT)` class (mod-bus listeners:
  `FMLClientSetupEvent`, `EntityRenderersEvent.RegisterRenderers`). The
  grayscale trick is `DesaturationTextures` (runtime sprite→gray
  DynamicTexture cache) + a `BlockEntityRenderer` that draws the covered
  face's own sprite desaturated — resolve sprites via face quads, then
  unculled quads, then the particle icon; never mutate atlas sprites or
  covered block state.
  The layer's block model is intentionally empty: its BER draws the host skin,
  the world-anchored scar atlas and its sparse resin mask.
  Do not restore a raised full-face slab. Persisted `born` drives one minute of
  gradual host bleaching; missing legacy birth times render mature. Rootlets
  use world lighting, knots follow the heart, and dead hearts leave cold scars.
  The player approved the native tile material and frayed boundaries in alpha.9.
  `BloomSurfacePattern` derives shared world-corner labels; one 16px tile covers
  one face. `tools/bloom_surface_art.py` builds 32 materials plus resin variants
  in a 128×128 atlas. Never stretch an illustration over multiple block faces.
  `BloomBoundary` caches at most 1024 merged coverage masks (eight neighbors ×
  four fray variants); the same mask clips grain, bleaching and ember light.
  Full interiors keep a single quad. Exposed edges leave original host visible;
  missing diagonals cut concave corners. Shared edge endpoints must agree even
  when neighboring fray variants differ. Keep additive RGB masked with alpha.
  The multiface attachment/spread rules remain unchanged.
  Mob lesions use native-skin alpha masks, normal lighting and sparse, dim ember
  points; dynamic skins with no readable source are skipped rather than painted
  opaque. The bounded texture cache clears on resource reload.
  The assimilation layer is strictly flat, texture-only art: pale bark tissue,
  warm gray grain and sparse amber fissures on the bleached host. No raised
  cuboids, fingers, tubes or proximity bending; no vine-green palette. Keep
  world-anchored grain continuous. The host surface stays the harvest target and
  collision stays empty. Nearby infected mobs shed pale client-side dust/ash with
  a shared four-particle-per-tick budget, 24-block range and stage-based cadence.
  Infection grants neither nausea nor vanilla Darkness. `BloomVignette` caps
  the border pulse at 10% opacity and leaves fog/central vision unchanged.

## Testing conventions

- Portal lookup must filter `portalOpen()` (ACTIVE and unsealed) before matching a portal address.
  Different parties reuse a terminal; an ended party's saved record must not
  shadow the new portal and cause it to self-remove. A sealed but still ACTIVE
  party must not shadow it either. `closePortal` uses `portalDimension`, and
  closed portals are not live marker references. The `portal_reuse` regression
  uses three distinct players at one terminal to prove entry and cleanup.
- `horde_body_fit` uses a real soul-sand-valley spawn table in a three-high
  enclosed room, deliberately rolls a ghast first, then verifies a smaller
  native type fits. Fill the biome over a region, not one block: biome lookup
  samples neighboring quart cells and a one-cell fixture can still read plains.

- Suppression seams are active ONLY with JVM property
  `expeditions.gameTestRun=true`, set by the Gradle GameTest server run.
  NeoForge initializes test classes in ordinary dev clients too; accepting
  their static suppression requests there hid bundled missions and disabled
  Bloom traversal/infection in real play. Do not remove this property guard
  from datapack loaders, Microbiome or Assimilation.

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
  holds their runs when asserting on save/load contents. Wrap every
  `startExpedition` in try/finally → `manager.failRun(player)`: a leaked
  active run keeps its marker positions referenced forever and the next test
  reusing the same authored anchors fails route resolution.
- Persistence tests: `ExpeditionSavedData.save(tag, registryAccess)` →
  `ExpeditionSavedData.load(tag, ...)` round-trips in game tests; corrupt the
  tag in between to test malformed-data handling.
- Datapack tests: simulate reloads via `ExpeditionDefinitions.reload(map)` /
  `ExpeditionTuning.reload(map)` and ALWAYS reset with `reload(Map.of())` in
  a finally block — both snapshots are shared statics and leak between tests
  otherwise. Per-dimension tuning files live at
  `data/*/expedition_dimensions/<dim-path>.json` (resource id = dimension id).
- Bundled datapack defs (`data/expeditions/expeditions/`) and tuning files
  (`data/expeditions/expedition_dimensions/`) are suppressed suite-wide via
  `ExpeditionDefinitionLoader`/`ExpeditionTuningLoader`
  `.suppressLoadingForTests` so shipped data never shadows the config
  defaults tests resolve against; tests that need them reload the real
  JSON from the classpath (`shippedDef`) or inject maps directly.
- Ambient world writes are suppressed for the suite: authored-route anchors
  from every datapack test resolve to shared absolute columns near the world
  origin, and microbiome colonies/hearts there would make later route
  resolutions fail. `Microbiome.suppressSeedingForTests(true)` gates marker
  colonies (lifted only inside `microbiomeColonizesMarkers`'s own batch);
  `suppressTraversalForTests(true)` gates the player-tick spawn path
  permanently — mock players tick once at the world spawn before
  teleporting, so an unguarded roll seeds hearts at the origin. Tests
  exercise `trySpawnHeart`/`seedColonyNear` directly.
- Cross-test shared state that needs baseline resets: `campaignProgress`
  accumulates completions across the whole suite (eventually flipping
  `clearedDimensions`, which silences the microbiome) and `dimensionPressure`
  enlarges hordes. Tests asserting baseline behavior reset via
  `resetCampaign`/`resetPressure` first.
- Tests that start a run MUST wrap post-start work in `try/finally
  failRunIfActive(manager, player)` — authored anchors are absolute world
  coordinates, so a leaked active run keeps its markers at the shared
  positions and every later test resolving those anchors fails. The
  GameTest world persists across `runGameTestServer` invocations (`run/world`),
  so a leak poisons subsequent runs too.
- `pollUntil` only SCHEDULES the async wait — a `finally
  failRunIfActive` after it executes while the poll is still pending and
  kills the very run being polled (this burned a full debug session:
  `FAILED` runs carrying stale `defense` records). Tests that poll on a
  live run pass their cleanup as pollUntil's terminal `cleanup` callback
  (runs on success AND timeout); use a try/catch guard only for the
  synchronous setup before the poll. Prefer unique route anchors in such
  tests so foreign marker-cleanup sweeps (`failLeakedForeignRuns`, which
  only touches runs sharing marker positions with ours) can't reach them.
- Foreign-run sweeps may only fail runs SHARING a marker position with
  the caller's run (`failLeakedForeignRuns`) — a blanket `other.fail()`
  on every active run murders concurrent in-flight tests (mock players
  are NOT in `getPlayerList`, so online checks can't discriminate).
- `ExpeditionGameTests` uses `makeMockServerPlayerInLevel()` (deprecated in
  1.21.1 but functional) — revisit if a better mock player API exists.

## Rules

- Campaign guardians: `bigBad` runs bind a `GuardianEncounter` UUID after the
  final checkpoint, independently of optional horde settings. `complete()`
  refuses until that exact guardian has an accepted death. Cleanup/discard
  never grants proof. `ExpeditionSavedData` persists the encounter with the
  run; malformed live ownership is rejected. Legacy active boss runs already
  past their final checkpoint without an encounter fail recovery safely.
  `RootboundGuardian.die` checks `dead` AFTER `super.die` so a canceled death
  cannot open extraction. The GameTest cancels one lethal hit, resets the
  fixture's same-tick immunity, then verifies a real kill and both save states.
- Guardian skins are UV-mapped 64x64 entity sheets generated by
  `tools/gen_guardian_texture.py`; the 16x16 rule still applies to item/block
  textures. Model, eyes/core glow and both warning phases are client-verified.

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

Challenge entities carry a persistent `expeditions:challenge_run` UUID.
`ChallengeOwnership` rejects ended/orphan ownership on load, retries cleanup on
mob ticks, and retains UUID tracking across chunk unloads. Slime children inherit
ownership through `MobSplitEvent`; only actually ticking children enter tracking,
and ended hordes cannot split. Legacy assimilated tracked mobs gain ownership on
load. The cleanup search checks the global visible UUID lookup before bounded
full-column section storage; never discard a live run's owned horde.

Verified: `build` + 168 unit tests + 148/148 core GameTest in-game
tests pass. The field journal writes its report into the stack's own
`WRITTEN_BOOK_CONTENT` component for Patchouli's `JournalRecordComponent` pages.
`JournalBook` opens the guide through the client callback and remembers the
hand used, so records read the correct physical stack. Keep `broadcastChanges()`
so fresh report data reaches an already-open book. Discovery bits
are explicit stable positions in pure-Java `JournalChapter`, persisted in the
player's `JOURNAL_DISCOVERIES` attachment with `copyOnDeath`; do not reuse bits.
The chapter body resource is `assets/expeditions/journal/chapters.json`.
`JournalDiscovery.reconcileBookDiscoveries` mirrors known bits into the hidden
`expeditions:journal/<chapter>` advancement gates, including legacy players.
The bits remain the authority; migration must never unlock unknown chapters.
Guide components are client-only and read synchronized reports, never mutable
server state. `expeditions_journal` tests discovery migration, referenced guide
items/recipes/entities, placement direction and hollow portal selection alongside
the existing save/load/death/crafting tests. Blood sampling belongs to
`FieldAssayKitItem`, not the journal. Its CustomData holds a paid, dated snapshot;
normal use never silently refreshes the sample. `AssayScreen` paginates small GUIs.
The infection command is operator-only; survival measurements require the kit.
The kit's
`expeditions:blood_letting` damage type rides `bypasses_invulnerability`
because mock players hardcode `isCreative()→true` — generic damage can
never cost them blood (and creative players would get free samples).
Synced attachments MUST gate `.sync` on
`NetworkRegistry.hasChannel(connection, SyncAttachmentsPayload.TYPE.id())`
— GameTest mock players ride embedded channels that never negotiated
payloads, and an unguarded send throws `UnsupportedOperationException`.
Entity appearance flags (e.g. `assimilated`) ride attachments, NOT
`entity.getTags()` — tags never sync to clients. The assimilated overlay
attaches via `EntityRenderersEvent.AddLayers` to every
`LivingEntityRenderer` (vanilla + modded + player skins) — renderers on
custom pipelines (GeckoLib) can't take layers and get the entity-tick
ember/ash fallback instead.
The dimension extension point is proven by
`thirdPartyAdapterHostsExpedition` (test-side End adapter through the
real `ExpeditionAdapters.register` path). Per-dimension tuning loads
from `data/*/expedition_dimensions/<dim>.json` → `ExpeditionTuning`,
with sparse `microbiome.*`/`pressure.*` overrides resolved at each call
site against the global TOML fallback; the bundled nether file is
suppressed in tests like the bundled defs. Human client session still
unverified (visual layer, compass heartbeat, menu lore).

M12a (progression keys) verified: `build` + 111 unit tests + 74/74
GameTest in-game tests pass. Unlock keys are per-player `Set<String>` in
`ExpeditionLedger` — granted party-wide at `completeRun`, checked by
`ledger.checkRequirements` at `startRun` before any world changes;
`cost_tokens` is charged only after route+markers succeed. Locked
expeditions stay in the terminal list (lore explains the gap);
`/expeditions unlocks` is the player-facing view. Menu mock players keep
the locked-lore path unverified visually (same as the summary
broadcast). Human client click-through and real disconnect not
simulated.


## Contact infection / hive milestone

Infection is contact-only. `AssimilationLayerBlock.entityInside` delegates to
`Assimilation.touchLayer`, which intersects the living body's AABB with the
same frayed boundary regions used by the renderer. Dead-heart scars and cleared
worlds cannot expose hosts. A persistent queued contact bit is consumed once on
the configured cadence, so a brief crossing counts without accumulating per
frame. Players retain the staged effects/effigy lifecycle; native mobs retain
their type and persist an infected variant at stage one or later. Legacy horde
attachment flags hydrate their infection dose on tick. Established mob variants
retain stage one unless the dimension is cleared. Armor stands are not hosts.

`AssimilationProtection` accepts the registered disruption effect, an offhand
Disruption Charm, or an actually equipped Curios item via the documented
`curios:item_handler` NeoForge capability. No Curios classes or reflection are
required in the release. Never treat a charm merely packed in inventory as
protection. The charm refreshes a short effect; don't remove separately granted
effects on unequip. The optional `-PcuriosCompatTest` runtime is for real client
verification only: Curios 9.5.1 sends `curios:sync_data` to embedded GameTest
channels and fails before our assertions. The ordinary isolated suite remains
Curios-free. Real Curios slot equip, automatic effect refresh and contact
immunity were verified in the disposable debug client.

`HiveMind` shares nearby threats/targets and regrouping leaders; it keeps no
static cross-world targets or forced chunk tickets. Its stable transient speed
modifier is -15% and must not stack or remove another mod's modifiers. The
priority-4 `HiveMindGoal` preserves priority-1 defense-device behavior and native
combat. Passive hosts gain a weak contact attack. Client-only
`LivingEntityRendererMixin` enables the existing vanilla shiver, without setting
frozen ticks or inflicting freezing damage. Disruption suspends that hive shiver
and coordination; it is not universal immunity to a species' ordinary attacks.

Verified: build + 155 unit tests + 129 core GameTests (132 reported including
three optional biome probes); contact/ward, all six surfaces, dead scars, native
mob persistence, physical carrier transmission, shared targets/non-stacking slow,
autonomous growth and cow-seeking have dedicated interaction checks. The live
client confirmed cows converging on a sheep, body-contact conversion, a 0.17
movement speed from a 0.20 baseline, and Curios-only protection. User art exports
are under ignored `art-exports/`; never regenerate or overwrite those editable
handoffs while iterating runtime art.


## Shared hive focus correction (alpha.11)

`ModAttachments.HIVE_FOCUS` is transient/server-only: no codec or network sync.
Nearby active infected mobs (24-block radius, capped at 32 bodies per decision)
receive one common focus. Refreshes are shared and bounded to ten ticks, fresh
attacks alert immediately, and member-private target goals cannot replace the
live common focus. Validity is checked before reuse: dead, unloaded, converted,
allied or other-world hosts are rejected. Disruption on a target prevents
infection, not detection; disruption on an infected member releases coordination.
`HiveMind.tick` runs after native AI and tracks eye height/head yaw separately
from the movement goal, without locking native combat or priority-1 device goals.
`HiveMindGoal` refreshes its running host instead of retaining an obsolete target.
`Assimilation.onAttacked` preserves the GameTest suppression guard for alerts.
Three interaction regressions cover conflicting targets, private-AI overwrite,
actual head orientation, stale-target removal, damage-event alerts and running
goal retargeting and real pursuit on both native AI tick schedules. The pursuit
goal requires every-tick updates: a tickCount % 10 navigation gate otherwise
never executes for one staggered schedule. Do not serialize entity references
in shared focus state.

Verified alpha.11: build/alphaBundle, 155 unit tests and all 132 core GameTests
(135 reported including three optional biome probes). The real Curios-equipped
client confirmed cow/sheep/husk convergence on one protected pig. With movement
disabled temporarily, all three heads followed that pig from left to right;
normal AI was restored afterward. Local screenshots are
`build/mcp-verification/screenshot-1793470484338-0.png` and
`build/mcp-verification/screenshot-1793470507594-0.png`.


## Frontier progression (alpha.12)

`FrontierAttention` and `FrontierProgression` are pure domain policies.
`FrontierThreat` owns the personal residence clock, native boss-style HUD and
bounded pursuit waves. Its instance belongs to `ExpeditionSavedData` (runtime
only): `ExpeditionManager.get()` constructs a stateless facade on every call,
so putting a stateful HUD service on the manager leaks duplicate boss bars.
Player residence lives under persistent `expeditions:frontier` and copies on
PlayerEvent.Clone. Checkpoints grant 45 effective seconds of relief. Retreat
cools old dimensions; logout/death do not wipe the clock. The Overworld spawn
refuge prevents ambient heart seeding and attention; infection still requires
contact. The default clock reaches noticed/hunted/overrun/reckoning at 2/4/7/10
effective minutes, with Overworld and established-foothold multipliers.
Pursuit mobs carry owner UUID and expiry, reserve tracked capacity across unloads,
and discard when ownership/exposure no longer permits them. They award no drops,
XP or guardian proof. The mission's exact bound guardian remains authoritative.
Routine completions now establish a foothold but cannot clear a dimension unless
`campaign.require_guardian=false` explicitly restores legacy count clearing.
Loaded dimensions without an authored boss receive a generated liberation mission.
Home liberation opens remote deployment for everyone; native portals remain usable.
`Microbiome.trySpawnHeart` checks cleared/refuge state itself, not only its caller.
`expeditions_frontier` adds five GameTests; register its arena through build.gradle.
The frontier suppression seam has the same gameTestRun JVM-property guard as the
existing seams. New guide text generation explicitly reads UTF-8 on Windows.
Verified: build/alphaBundle, 159 unit tests and 137 core GameTests (140 including
three optional biome probes). Balance is an initial alpha pass; long survival
and multiplayer balance sessions remain useful.

Live alpha.12 review used a temporary 60-effective-second threshold and accelerated
server ticks in the disposable MCP world. The corrected HUD displayed one bar;
Reckoning spawned owned infected hunters and recorded the pursuing guardian.
Temporary resistance protected the test player, so this verifies presentation and
escalation, not a human survival balance verdict. The probe datapack was disabled,
normal timing restored, temporary test effects removed, and Creative restored.
HUD proof: `build/mcp-verification/screenshot-1793635703076-0.png`.


## Stillness and essence bootstrap (alpha.13)

The Overworld is sheltered by default, independently of the cleared flag. Live
hearts hibernate into dormant heartwood, remaining supported colony faces become
DormantLayerBlock, and raw flakes replace direct essence harvesting. Natural
DormantScarFeature adds safe traces in new Overworld chunks. Its suppression seam
is guarded by gameTestRun; old authored test columns must not gain ambient traces.
Stillness also suppresses local infection, hive activation and passive pressure.
`ResonanceProcessing` is pure domain policy; the block entities own paid batches.
Vanilla-only Dynamo/Condenser recipes bootstrap the FE chain: 4000 FE from one raw
flake, another flake plus 4000 FE makes one essence over 200 ticks. The standard
NeoForge EnergyStorage and ItemHandler capabilities expose directional energy and
input/output automation. No runtime dependency was added. Feedstock reservations,
power and progress persist; breaking unfinished work returns raw feedstock.
A charged condenser can open a deliberate 24-block local breach with a sigil.
It requires a 4000 FE reserve and drains 20 FE/t, pauses processing, and fails
closed on power loss/removal. Bounded breach positions persist in world SavedData;
loaded machines validate activity without loading chunks. Cleared flags still win.
First survey proof now gates remote deployment; no home boss victory is required.
The journal and assay kit use raw flakes and cannot deadlock on refined essence.
`tools/stillness_art.py` is the final field-equipment art pass, called last by the
master generator. It does not touch approved Bloom atlases or user art exports.
Open-frame models and their rotated selection/collision share native model JSON
through FieldMachineShapes; keep visible open space. The wheel is currently static.
New art and actual screenshots live in docs/art-direction/stillness. The generated
concept is reference only; native game textures remain 16x16. Five new tests use
expeditions_stillness; arena comes from the registered Gradle namespace list.
Verified: build/alphaBundle, 162 unit tests, 142 core GameTests (145 including three
optional biome probes), plus actual client production/UI and final geometry checks.


## Resonance sources (alpha.14)

`ResonancePower` is pure domain policy: sheltered/liberated dormant knots supply
20 R/t divided among resonators within six blocks, contained hearts supply 80 R/t
with 600 paid running ticks per essence, and a proven severed core supplies 120 R/t.
Resonance uses standard FE at 1:1; source types do not open breaches automatically.
The old stillness_dynamo registry id remains for compatibility; its new name and
model are Stillness Resonator. No more raw-fuel burning. Dormant Knot recipes need
only four raw flakes; the condenser still requires one raw sample and 4000 R.
Living and severed blocks share the machine type but have different item validators.
Power output is divided across accepting neighbors with a conserved total budget.
`GuardianLiberations` persists in the same SavedData as win flags/ledger. Only
accepted bound-guardian death plus successful extraction records new proof. A
cleared mark alone is insufficient. Known historical boss trophies may migrate;
unknown history fails closed. An unattuned core is crafted, then bound at a proven
source terminal. Bound items still revalidate their dimension against server proof.
Campaign reset removes proof; stored energy remains, but new generation stops.
No inventory reward is granted at victory, avoiding a separate crash-sensitive
item-award transaction. The source perk is shared world progress.
`ResonanceRenderer` adds world-lit suspended knots and flexing ribs; common logic
never imports it. HAS_CORE and ACTIVE block state synchronize presence/activity;
no per-frame world edits or extra payloads. Menu data now covers source kind/rate;
generators have one accessible slot and condensers have two.
`tools/stillness_art.py` maps side/end grain along actual beam axes and gives glass
its own UVs. The generic stamped panel and flywheel geometry are superseded.
Approved layer atlases and user art exports remain untouched. Six interaction tests
use expeditions_resonance (registered arena namespace); six pure source tests cover
rates and proof/fuel/field conditions. Real guardian extraction also asserts proof.
Verified: build/alphaBundle, 168 unit tests, 148 core GameTests (151 including three
optional biome probes). See docs/resonance-power.md for operating details.

Live alpha.14 checks confirm 20/80 R/t source screens, real refining, moving knots
and the unproven-core refusal. The demo core remains unattuned; proven 120 R/t
production and reset behavior use the terminal interaction test. Empty cradles
exclude the animated-node placeholder from FieldMachineShapes until HAS_CORE.
The native preview/motion/UI images are under docs/art-direction/stillness.
