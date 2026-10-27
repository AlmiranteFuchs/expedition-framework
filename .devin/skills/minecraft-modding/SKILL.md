---
name: minecraft-modding
description: Repo recipes for this NeoForge 1.21.1 mod — adding blocks, items, config knobs, game tests, and datapack fields, plus suite pitfalls
---

# Expedition Framework — modding recipes

NeoForge `21.1.x`, MC 1.21.1, Java 21, ModDevGradle, mod id `expeditions`,
base package `com.expeditions`. Read `AGENTS.md` for architecture boundaries
(this file is the how-to; AGENTS.md is the why).

## Iteration commands (fastest → slowest)

```bash
./gradlew compileJava                                        # ~4s  types/syntax
./gradlew test                                               # ~10s domain unit tests (JUnit 5, no MC)
./gradlew runGameTestServer -PgtNamespaces=expeditions_smoke # ~22s 1-test full loop
./gradlew runGameTestServer -PgtNamespaces=<feature_ns>      # ~25s one feature batch
./gradlew runGameTestServer                                  # ~90s full suite — pre-commit gate
```

`runGameTestServer` deletes `run/world` first — the world NEVER persists
between runs; stale blocks used to corrupt results. Results: grep
`required tests` in `run/logs/latest.log`. `-PgtNamespaces` is
comma-separated (`expeditions_mic,expeditions_def`).

### Feature namespaces (template namespace = filter key)

| `-PgtNamespaces=` | Tests |
|---|---|
| `expeditions_smoke` | 1-test end-to-end loop |
| `expeditions_def` | defense devices/waves |
| `expeditions_mic` | microbiome + colonies |
| `expeditions_asm` | assimilation layer/heart |
| `expeditions_prog` | campaign/unlocks/sigil |
| `expeditions_art` | artifacts/keys/trophies |
| `expeditions_eco` | tokens/redeem/valuation |
| `expeditions_party` | party + portal |
| `expeditions_obj` | objectives |
| `expeditions_loot` | bonus drops/pools |
| `expeditions_opts` | options/modifiers sizing |
| `expeditions_mod` | menu/modifiers/effects |
| `expeditions_horde` | nether horde probe |
| `expeditions` | core lifecycle (~30 tests) |

Set per-test with `templateNamespace = "<ns>"` on `@GameTest`. Each ns needs
`data/<ns>/structure/arena.nbt` — generated at build time from the canonical
`expeditions` copy (`gameTestStructureNamespaces` list in build.gradle; add
new namespaces there, never hand-copy).

## Recipe: new block

1. `registry/ModBlocks.java`: `DeferredRegister` + `DeferredHolder`.
2. `registry/ModItems.java`: matching `BlockItem`.
3. `registry/ModCreativeTabs.java`: add to tab.
4. `block/MyBlock.java`: delegate interactions to `server/` layer — never put
   logic in the block.
5. Resources: `data/expeditions/loot_table/blocks/<id>.json`,
   `assets/expeditions/blockstates|models|textures/...`, lang key in
   `en_us.json`. Textures so far are generated procedurally — see existing
   python texture scripts in git history or `client/DesaturationTextures`.
6. With a BlockEntity: `registry/ModBlockEntities.java`, ticker via
   `getTicker`/`serverTicker` pattern from `AssimilationHeartBlock`.

## Recipe: new item

`ModItems` + `ModCreativeTabs` + `assets/.../models/item/<id>.json` +
`textures/item/<id>.png` + lang. Behavior data: `DataComponent` via
`ModDataComponents` (see `attunement_sigil`'s key component — granted at
terminal, not consumed).

## Recipe: config knob

`ExpeditionsConfig.java`: a `ModConfigSpec` section + a public accessor at the
bottom of the file. Section comments document units (ticks!). Read at call
time — tests toggle values via the spec's `.set()`.

## Recipe: game test

```java
@GameTest(template = "arena", templateNamespace = "expeditions_feat",
          timeoutTicks = 400, batch = "expedition_feat")
public static void myTest(GameTestHelper helper) { ... }
```

- `templateNamespace` picks the `-PgtNamespaces` filter bucket; `batch` groups
  tests that run in parallel (distinct batches run sequentially).
- Player interactions: teleport the mock player, then
  `gameMode.useItemOn` — `helper.useBlock` breaks outside structure bounds.
- Datapack-simulating tests: wrap in try/finally with
  `ExpeditionDefinitions.reload(Map.of())` — the snapshot is a shared static.
- Baseline-sensitive tests: reset shared accumulators first —
  `savedData.resetCampaign()` / `resetPressure(dim)` — completions and
  pressure leak across the whole suite.

## Recipe: datapack definition field

`core/ExpeditionDefinition` (record) → `server/data/ExpeditionDefinitions.parse`
→ `RequirementCheck` (gating) → persist on `ExpeditionRun` if needed mid-run →
unit test in `src/test` + a reload-driven game test. Loader:
`ExpeditionDefinitionLoader` scans `data/*/expeditions/*.json`.

## Suite pitfalls (learned the hard way)

- **Mock players tick once at world spawn** before teleporting; any
  `player.tickCount % N == 0` hook fires on tick 0 near the origin. Ambient
  world writes (e.g. microbiome traversal seeding) MUST ship with a
  `suppress*ForTests` seam — pattern: `Microbiome.suppressTraversalForTests`.
- **Authored-route anchors are world-absolute** — every test's route resolves
  to shared columns near the origin. Any persistent block placed there
  (hearts, membrane) breaks later route resolutions (`isFree`/`isSturdyFloor`
  reject it → nudges into the sky → "No safe route").
- Marker blocks are intentionally NOT sturdy — a marker on an anchor column
  fails the next resolution.
- `startRun` refusals are swallowed by the menu (WARN in `latest.log`) —
  "must start a run" assertion failures mean check the log's refusal reason.
- Tests in one batch run in PARALLEL in the same area — never lift a
  suppression flag in a shared batch (`microbiomeColonizesMarkers` has its own
  batch `expedition_mcol` for this reason).

## Docs to update when shipping

`docs/expedition-roadmap.md` (milestone status), `README.md` (feature list,
test counts), `AGENTS.md` (architecture notes + any new convention).
