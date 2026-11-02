# Field journal voice — alpha.18

The guide should make a player capable without making them omniscient. Its tone
is a practical field copy: observations, recipes and work that has been tried.
The cause of the Bloom and the history of the Charter remain uncertain.

Each entry can begin with a short physical observation, then give the relevant
action plainly. Avoid melodramatic warnings, repeated slogans, invented ancient
quotations, developer terminology and a full campaign itinerary. Explain controls,
costs and counterplay once they matter. Mystery must not conceal a necessary input
or leave a player unable to recover from failure.

All fourteen chapters were rewritten. The first notes describe cold scraps and
the starter workshop; they no longer list the later guardian progression. The
awareness chapter explains the warning bar and ways to manage it without printing
the entire future escalation schedule. Encounter instructions stay useful once
the encounter has actually been discovered.

Recipe placement now follows knowledge:

- **First steps**, always readable: journal, knot, resonator and condenser.
- **Essence**: terminal, assay kit and attunement sigil.
- **Preparation**, after a recovered specimen/instrument: cells, ward, lens and
  the contained Bloom Engine.
- **Liberation**, after that discovery: severance core and cradle.

The stable chapter bits, advancement ids, report components and hand-aware book
opening are unchanged. Existing discoveries are retained; unknown chapters are
not unlocked by this writing pass. The generator keeps crafting pages beside the
appropriate field notes and preserves the preferred pre-alpha.16 journal sprite.

The copy also corrects two old instructions: infection requires contact, and the
assay kit uses a raw dormant flake rather than essence. A blood sample costs one
health heart; it does not consume a bloom specimen.

Build/alphaBundle, 176 unit tests and 160 core GameTests pass (163 reported with
three optional biome probes). The guide's existing registry-reference/migration
tests remain, with a new regression protecting starter access and advanced recipe
gates. Proposed Liquid Resonance machinery is not described as an existing feature
in the player guide.
