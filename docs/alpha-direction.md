# Alpha direction

The playable alpha should turn discovery into a reason to prepare, travel,
return, and build. `src/SOUL.md` remains the identity reference; unresolved
origins of the organism and ancient infrastructure remain unresolved.

## Working rules

- Explain the next action through readable tools and field notes. Do not hide
  basic controls behind lore. Discoveries unlock explanations and records.
- Keep the mandatory route and extraction stakes. Checkpoint banking survives
  failure; rewards, party state and marker cleanup retain their existing rules.
- Make dimensions feel local: use their terrain, biome creatures and rewards.
  No compatibility guarantee may bypass safe route resolution.
- Threats need a warning, a readable response, and a recovery window. Difficulty
  must not mean silent instant damage or larger health bars alone.
- Use pale heartwood, dark cavities, root bindings and ember ignition as the
  shared visual language. Keep item art crisp at Minecraft scale.
- Keep standalone progression complete. External libraries are welcome when
  they solve a current need, not as mandatory replacements for player discovery.
- Ship in reviewable milestones with build, relevant tests, and a commit.
  Visual features also need an actual client check.

## Immediate milestones

1. **Wayfinding**: custom compass art in every frame; a held elevation ember and
   right-click attunement. Completed in `ed4cedd`.
2. **Field guide**: chapter index, discovery-based unlocks, practical recipes,
   live survey/assay pages, and a matching illustrated journal cover. Preserve
   progress across death and relog; acquiring another book must not erase it.
   Implemented; build, 131 unit tests and 103 core GameTests pass. Client review
   confirmed readable pages, the new cover icon and working keyboard chapter
   navigation. Fixed the vanilla menu blur obscuring the custom book lettering.
   After feedback on the cover, replaced the mask motif with a clearer angled
   16px book silhouette, pale page block, root spine and single amber clasp.
   The replacement is locally pixel-reviewed; its live resource reload remains
   pending while desktop control reports its Escape safety stop.
3. **Terminal**: make mission choice, prerequisites, stakes and progression
   readable. Preserve server authority and keep unsupported routes fail-closed.
   Implemented a dedicated deployment board with briefing/requirements pages,
   readable missing-proof hints, costs/rewards, reverse cycling and safe route
   lengths. Client-checked the layout; automated tests cover defaults and locks.
   The client check also caught test-only suppression leaking into normal play;
   isolated those flags to the automated test JVM and verified all three shipped
   missions load in an ordinary client.
4. **The confrontation**: a dedicated mission with an original root-and-ember
   guardian, custom model, boss bar, telegraphed attacks, and extraction gated
   on defeating the encounter. Treat its name and fiction as alpha vocabulary.
   Implemented as Heart of the Thicket and the Rootbound Guardian. The client
   verified the model, emissive core, both phases, warning circle, raised-arm
   pose, boss-bar transition and real survival damage in a disposable world.
   Unit and GameTests cover footprints, ownership, save/load and canceled death.
5. **Release audit**: survival recipes, progression chain, guide discoveries,
   multiplayer cleanup, optional dimension compatibility, and an honest list
   of remaining gaps. Passing the old roadmap is not proof of finished polish.
   Packaged `0.6.0-alpha.1`: 137 unit tests, 109 core GameTests and three actual
   installed-dimension biome probes pass. Hardening also fixed reused portal
   addresses, remote sealing cleanup and oversized horde rolls. Remaining
   human/client checks are recorded in `docs/alpha-playtest.md`.

## Studied references

- [Patchouli discovery locks](https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/advancement-locking/):
  entries unlock with player progress; clear category navigation gives notes a
  practical structure. Our guide uses server-synced discoveries and a custom
  book interface, so the alpha does not require another mod for instructions.
- [Minecraft's Pale Garden](https://www.minecraft.net/en-us/article/the-pale-garden):
  the visual relationship between an eerie creature, its heart, and the traces
  that reveal it. Inspiration for readable root/ember cues, not a claim that
  the vanilla Creaking exists in Minecraft 1.21.1 or a replacement of its lore.
- [NeoForge entity renderer documentation](https://github.com/neoforged/Documentation/blob/main/docs/entities/renderer.md):
  custom geometry/layer registration. The implementation targets this project's
  1.21.1 API; newer renderer-state APIs are not used.
