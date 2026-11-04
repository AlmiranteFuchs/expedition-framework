# Alpha.24 validation

Two commits cover machine surface repair/feedback and the first M16 navigation
and report features. No runtime dependency was added.

- Build and alphaBundle pass with the binary Minecraft development artifact.
- 193 JUnit tests pass.
- 176 core GameTests pass; the harness reports 179 including three optional
  modded-biome probes. Installed-mod biome tests are a separate run.
- Four Python surface-mesh tests pass. The geometric audit finds zero overlapping
  coplanar machine face pairs, down from 34.
- The authored tree verifier confirms all 930 original voxels and stable legacy IDs.

Native client: alpha.24 launched with Create, Curios/Accessories and the installed
dimension mods. In MCP-Debug-Test-20261029, campaign records correctly showed
frontier, established and cleared dimensions. The live history check started a
Field Survey on suitable natural terrain, abandoned it, and displayed:

> Field Survey — failed | marks 0/2 | 0 tokens kept | 0:18 | minecraft:overworld — the expedition was abandoned

Refusals from the elevated workshop and watery terrain deployed no run and
created no report. The player was returned to the workshop after testing.
The compass tooltip wiring was verified through compilation and band unit tests;
manual tooltip hover was not exercised. The existing lens measurement remains
paid and precise. Screenshots of the repaired machines and operating panel are
in `docs/art-direction/machines`.

Release jars, including required Patchouli, are staged in `build/alpha`.
