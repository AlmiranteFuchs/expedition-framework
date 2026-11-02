# Stillness progression — alpha.13

Home is preparation space. The Overworld is sheltered by default, independently
of campaign liberation. It has no natural live hearts, Bloom attention or passive
pressure accrual. The Stillness suppresses hive coordination; existing infection
can linger while it decays. Ordinary vanilla monsters and hazards remain.

## The starter chain

1. Explore for small pale dormant scars. They generate in newly explored natural
   Overworld terrain, roughly one patch per sixteen chunks. They are inert and
   noninfective. Existing sheltered hearts and their loaded colonies also become
   dormant. Destroying a live heart converts its remaining supported tissue into
   the same dormant layer; unsupported faces disappear silently.
2. Harvest dormant flakes. Detached tissue is raw material; breaking layers does
   not award finished essence anymore. A dormant heart yields four flakes.
3. Craft a field journal from a book and a flake. The assay kit also uses raw
   flakes. Collecting raw material unlocks the relevant recipe and guide entries.
4. Build the Stillness Dynamo and Essence Condenser from vanilla materials.
   Neither machine requires essence or another mod. Place them adjacent.
5. Put one flake in each machine. The dynamo converts its flake into 4000 FE;
   the condenser spends that power and its second flake to produce one regular
   assimilation essence over 200 ticks (ten seconds at normal server speed).
6. Refine the essence needed for the expedition terminal. Complete Field Survey
   to open remote deployments; an attuned sigil remains an alternate proof route.
   Home does not require a guardian victory before the frontier opens.

Thus the standalone loop costs **two raw flakes per essence**. A whole first
patch supplies several batches. The journal and assay instrument can precede
those batches. Two machines are a small first workshop, not an essence deadlock.

## Interoperability and ownership

Both machines have 16000 FE storage. The dynamo generates 40 FE/t while burning
and exports up to 80 FE/t to neighbors. The condenser accepts up to 80 FE/t and
spends 20 FE/t during a batch. Its power can come from any compatible FE source.
Capabilities use NeoForge's standard EnergyStorage and ItemHandler interfaces.
No new library or Create dependency is required. Create inspired the physical
construction; kinetic-network compatibility is not implied.

Automation inserts flakes into slot 0 and extracts results from slot 1. The GUI
allows manual inventory handling and displays power/progress. Paid feedstock,
stored power and processing progress persist with the block entity. A broken
unfinished condenser returns its reserved raw flake, not a second finished
product; stored energy is lost on breaking, as a normal placed machine resource.

## Deliberate local reversal

Bring a charged condenser and an attunement sigil into the Overworld. Sneak-use
the sigil on the condenser to open a local breach. Opening requires a 4000 FE
reserve; sustaining it consumes 20 FE/t. Refining pauses while it is open.

The affected sphere has a 24-block radius. Live hearts and contact infection can
operate inside it; outside it, home stays sheltered. Bring a recovered heart from
the frontier. If it has become dormant, use an essence on it inside the breach
to restart it. Closing the field, losing power or removing the machine restores
protection and hibernates living colonies. Unloaded anchors are not force-loaded.
A liberated dimension remains silent; experimental breaches do not erase its win.

This is optional, reversible home experimentation. Keeping the ordinary
Overworld protected requires no machine, fuel, timer or maintenance.

## Art and verification

The open-frame machine family, chart lectern and updated handheld sprites are
in `art-direction/stillness/`; the image-generation board is only a design guide.
The actual game uses native sprites and custom geometry with matching voxel
shapes. Approved layer material and editable art exports were preserved.

Build/alphaBundle, 162 unit tests and 142 core GameTests pass (145 reported with
three optional biome probes). The client verified actual adjacent-machine essence
production, the processing screen, item sprites and the open models. A final
focused run rechecked the refined flywheel geometry. Existing-world users need
new chunks for new worldgen traces, or can recover existing dormant colonies.
