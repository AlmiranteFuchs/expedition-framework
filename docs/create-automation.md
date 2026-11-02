# Create automation — tested interfaces and proposed Resonance line

Status: Create item transport and existing-item autocrafting were tested live.
Liquid Resonance, its still, and terminal fluid costs below are a proposal. No
fictional fluid recipe is installed in the game's datapacks.

## Verified dev installation

Downloaded the official [Create 6.0.10 for Minecraft 1.21.1](https://modrinth.com/mod/create/version/UjX6dr61)
to `run/mods/create-1.21.1-6.0.10.jar`. It is a dev-instance addition, not a required
dependency of the Expeditions jar. Its bundled Flywheel, Ponder and Registrate
loaded with NeoForge 21.1.264, existing dimension mods and the Curios test library.
The download's SHA-512 matched Modrinth's version metadata:

```
11cc8fc049d2f67f6548c7abfada6b82a3adb5c7ca410a742de04bbca76e03862c518721b88d806f6e6d768a4d68531fdb903a85859b25d1484d550cc7bafd4b
```

Live checks used only `MCP-Debug-Test-20261029`. Fixtures were stocked with
creative commands; processing and transport then ran through normal game ticks.

- A Create mixer driven through a cogwheel at 64 RPM consumed a book and dormant
  flake in its basin and produced the actual field journal. No compatibility
  recipe was needed for this existing shapeless recipe.
- A top Create chute delivered eight dropped dormant flakes to an Essence
  Condenser. A native Stillness Resonator powered it. A bottom Create chute took
  the output into a chest: eight assimilation essence, with no leftover input.
- A second mixer batch produced an attunement sigil retaining its
  `expeditions:attunement_key=expeditions:attuned` component.

The mixer is at 1200,240,1050 (basin) / 1200,242,1050 (operator). Its powered
cogwheel is beside it at 1201,242,1050. The chute/condenser line is at
1205,240,1050, with its output chest two blocks below. The creative motor is only
a test driver; a survival build supplies adequate rotation and stress capacity.

## Proposed loop

```
expedition salvage chest: dormant flakes
              |
      filtered funnel / chute
              v
      Essence Condenser  <--- native Resonance power
              |               or compatible FE generation
              v
          essence buffer -----> autocrafting branch: equipment / consumables
              |
              v
water pump -> heated basin + mixer <- charcoal from a furnace/tree-farm line
              |
              v
        Liquid Resonance
              |
      pipes + mechanical pump
              v
        bulk fluid tank
              |
       filtered pipe outlet
              v
        expedition terminal
```

One proposed heated mixing batch consumes one essence, one charcoal and 1000 mB
water to make 1000 mB Liquid Resonance. Create supplies mixing, heat, batch
handling and fluid logistics. The same anomaly input is required regardless of
factory size. Optional electrical bridges can power the upstream condenser;
ordinary Create rotation is not directly an FE connector. The author's
[Create Crafts & Additions page](https://www.curseforge.com/minecraft/mc-mods/createaddition)
documents its alternator/motor bridge. That addon is not installed by this study.

The base-mod alternative is a compact **Resonance Still** accepting the same
ingredients, a water bucket and native FE power. An initial target is twenty
seconds and 8000 R per bucket after essence refinement, with manual bucket/item
handling and small buffers. Together with the existing essence step, one bucket
costs one flake, one charcoal, water and 12000 R at the starter workshop. A single
20 R/t source supplies that energy in thirty seconds at full draw.

The Create route should improve batching, throughput and unattended delivery,
without a yield multiplier. Its heated basin requires the corresponding Create
infrastructure; native players can make the fuel before obtaining a blaze.
No additional intermediate slurry item/fluid is necessary for the first version.

## Autocrafting and stock control

Split the essence buffer: one filtered route feeds portal-fuel mixing, another
supplies crafting requests. Keep a minimum essence stock for instruments so the
fuel line cannot consume every sample. Use comparator/threshold stock control
on the liquid tank to stop producing when the reserve is full. The default mod
can use manual stock decisions and buckets; Create automates the same decisions.

Existing shaped equipment recipes can be arranged in Mechanical Crafters, with
packagers/requested stock as an optional later convenience. The mixer was directly
verified for the existing shapeless journal recipe; this is not a claim that all
equipment recipes were individually tested in Mechanical Crafters. Run-bound
artifacts and guardian proof are never craftable substitutes for progression.

## Future implementation contract

- Register the native fluid and bucket independently of Create. Native Still and
  terminal tanks expose NeoForge `IFluidHandler`; inventory ports expose
  `IItemHandler`. Pumps and alternative autocrafters can then use the same ports.
- The standalone path and Create recipe consume the same anomaly inputs and
  yield the same fluid. Faster automation does not replace discovery gates.
- The optional recipe is conditioned on Create being loaded. Its example is
  `automation-examples/create-liquid-resonance.example.json`, copied from the
  installed version's actual ingredient/result schema. It is deliberately outside
  `src/main/resources` until the native fluid exists.
- The terminal quotes one prepaid fluid cost. There is no additional generic
  FE or token toll for the same ordinary deployment. Return/extraction never
  depends on continued pumping or power after a run starts.
- Extra FE should eventually accelerate a single condenser up to a bounded rate,
  as well as enabling parallel machines. Today that condenser remains fixed at
  ten seconds per essence; the transport check does not claim overclocking exists.

See `resonance-deployment-proposal.md` for cost/reward and failure rules.
