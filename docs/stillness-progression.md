# Stillness progression — alpha.15

Home is preparation space. The Overworld is sheltered by default, independently
of campaign liberation. It has no natural live hearts, Bloom attention or passive
pressure accrual. The Stillness suppresses hive coordination; existing infection
can linger while it decays. Ordinary vanilla monsters and hazards remain.

## The starter chain

1. Explore for small pale dormant scars. They generate in newly explored natural
   Overworld terrain, roughly one patch per sixteen chunks. A one-time bounded search also places a
   starter trace near spawn on suitable natural land and records its coordinates. They are inert and
   noninfective. Existing sheltered hearts and their loaded colonies also become
   dormant. Destroying a live heart converts its remaining supported tissue into
   the same dormant layer; unsupported faces disappear silently.
2. Harvest dormant flakes. Detached tissue is raw material; breaking layers does
   not award finished essence anymore. A dormant heart yields four flakes.
3. Craft a field journal from a book and a flake. The assay kit also uses raw
   flakes. Collecting raw material unlocks the relevant recipe and guide entries.
4. Compact four flakes into a Dormant Knot. Build a Stillness Resonator around it
   using vanilla metals and redstone, then build an Essence Condenser. No refined
   essence is required for this workshop. Place the machines adjacent.
5. The sheltered knot supplies 20 Resonance/t without burning flakes. Put one raw
   flake into the condenser: 4000 Resonance makes one essence in ten seconds.
6. Refine the essence needed for the terminal. Complete Field Survey to open remote
   deployments; an attuned sigil remains an alternate proof. Home does not require
   a guardian victory before the frontier opens. Trace Recovery follows Survey as
   a distinct cargo tutorial; completing both establishes home and opens Deep Salvage.
   The terminal now uses glass in place of amethyst.

After the initial four-flake knot, the loop uses **one raw flake per essence**.
The source and its FE connections are described in [Resonance power](resonance-power.md).
That guide also covers captured-heart upkeep and liberation-earned severed cores.

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

Build/alphaBundle, 172 unit tests and 154 core GameTests pass (151 reported with
three optional biome probes). The client verified actual adjacent-machine essence
production, the processing screen, item sprites and the open models. The alpha.15
preparation chain adds carried power, a finite quiet camp and a paid distance lens. Existing-world users need
new chunks for new worldgen traces, or can recover existing dormant colonies.
