# 0.6.0-alpha.1: playable alpha candidate

Minecraft 1.21.1, NeoForge 21.1.x, Java 21. No additional mod is required.
Optional MCP tooling is development-only; nothing in the shipped mod depends
on MCP, Aether, Bumblezone, Twilight Forest, Patchouli, or a client automation tool.

## Default survival loop

1. Explore until you find a Bloom colony. Harvest attached growth for essence.
   Its live heart is the source; destroy it when you are finished harvesting.
2. Craft the journal from a book and essence. Collecting essence teaches the
   crafting-book recipes for the journal, terminal and attunement sigil.
3. Build a terminal somewhere you can return to. The journal documents its
   recipe. Read the deployment board's briefing and requirements.
4. Complete Field Survey twice to establish the Overworld campaign under the
   default settings. Each successful extraction earns proof; checkpoint tokens
   are banked even if a later run fails.
5. Complete Deep Salvage: recover its physical cargo and protect its extractor.
   Hold the bound cargo when extracting. Read the journal chapters discovered
   along the way.
6. Deploy Heart of the Thicket after earning the salvage proof and its 25-token
   fee. Reach the final checkpoint, defeat the Rootbound Guardian, then extract.
   Its death alone does not pay or complete the run.
7. Explore other installed dimensions through the terminal. Automatic routes
   use loaded dimension IDs and biome-local mobs where eligible. Terrain that
   cannot safely resolve a route is refused before deployment.

The configurable completion threshold is also an alternative campaign-clear
path. Default gates, costs and thresholds can change through server config or
datapacks; the live terminal is the authority on your world's requirements.

## Controls and feedback

- Compass thorn: direction. Hovering ember: target above/below your elevation.
  Hold use to hear and see its elevation attunement.
- Journal use: chapter guide. Dark chapters explain their discovery condition.
  Sneak-use: nonlethal blood sample costing one heart. The assay remains bound
  to that particular book; chapter knowledge belongs to the player.
- Terminal: previous/next mission, left/right click to cycle options forward or
  backward, briefing/requirements tabs, page arrows or mouse wheel for long
  notes. The first checkpoint seals the party.
- Guardian: leave the marked ember circle before its strike. Its core takes
  extra damage while it raises its arms. Below half health it alternates sweeps
  with marked bursts under its target. Reloading gives a fresh warning window.

## Verified

- Build and 137 pure-Java unit tests.
- 109 core GameTests: lifecycle, reward atomicity, party cleanup, dimensions,
  journal save/load and death copy, survival crafting, terminal defaults,
  required route length, guardian binding/death veto/save-load/Peaceful toggle,
  reused portals ignoring ended/sealed parties, remote portal sealing at home,
  read-only diagnostic snapshots, and weighted body-fit retries for
  oversized biome mobs in enclosed terrain.
- Client: compass face/elevation/attunement, journal readability/locked hints/
  keyboard navigation, deployment-board layout, guardian geometry/emissive art,
  both phases, warning pose/circle, boss-bar transition and real survival damage.
- Normal development client loads all three shipped missions and dimension
  tuning. Test suppression is now limited to the automated test-server JVM.
- All three installed-mod probes passed again with actual Aether, Bumblezone
  and Twilight biome data and mob placement. These are distinct from the
  pending live loop.

## Still to validate

- The complete live MCP portal → native horde → extraction loop in each of the
  three optional dimensions. The script previously depended on `save-all`,
  which an integrated server does not expose; it now uses the read-only admin
  `/expeditions diagnostics` snapshot. It has not yet completed a live pass.
- The final deployment-screen tweaks with the restored shipped missions,
  including long objective pages and real mouse interaction at multiple GUI
  scales. The earlier board layout and server click paths are verified.
- The replacement angled journal sprite in the live client after resource
  reload. The 16px silhouette and material clusters have been reviewed locally.
- Human multiplayer balance, disconnect/rejoin with actual clients, shields,
  different equipment tiers, and extended boss kiting in varied terrain.
- Accessibility/localization polish: new guide text and some board copy are
  currently English. There is no claim of release-quality encounter balance.

Client interaction stopped on the physical Escape safety switch during the
remaining live checks. Automated verification continued; no destructive live
test was run against the user's separate `supaflat test` world.

## Reproducing development checks

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer -PbinaryMinecraft -PisolatedGameTests
.\gradlew.bat runGameTestServer -PbinaryMinecraft -PgtNamespaces=expeditions_modded_horde
.\gradlew.bat runClient -PbinaryMinecraft
```

Use the disposable `MCP-Debug-Test-20261029` save for the live tool script. It
checks the world identity before every command and restores mob spawning when
finished. The admin snapshot reads only the invoking player's latest run and
never modifies rewards, progress, or world blocks.
