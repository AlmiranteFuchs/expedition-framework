# Resonance supply and expedition costs — proposal

Status: not implemented. This incorporates the user's later request for a liquid
Resonance production line. The live Create checks and example recipe are in
[Create automation](create-automation.md).

## One named resource, two useful forms

Existing FE-compatible Resonance powers the workshop. Proposed **Liquid Resonance**
is its prepared physical carrier, made from essence, water and charcoal. The
terminal spends a quoted liquid amount to deploy. FE is spent during manufacture,
not charged again as a second portal toll. Ordinary generic token entry fees
should move to the liquid quote; tokens remain earned money for supplies and
upgrades. Explicit special permits may still be authored mission requirements.

The proposed recipe is one essence + one charcoal + 1000 mB water -> 1000 mB Liquid
Resonance. A native Still uses FE and manual handling; a heated Create mixer uses
rotation, a burner and automatic batch handling. Both require the same anomalous
input and give the same output. There is no reverse liquid-to-FE recipe in the
first implementation, avoiding an unintended power-generation feedback loop.

## What works now

Native generation is 20 / up to 80 / 120 R/t. Machines expose standard FE/item
capabilities, and real Create chutes have delivered input and extracted completed
essence in the dev world. A real Create mixer crafted the journal and a sigil,
retaining its attunement component. However, the current condenser still takes
ten seconds per essence, and the current terminal has no Resonance input or fluid
tank. The fluid, Still, new recipe and deployment costs below do not exist yet.

Create rotation needs an electrical bridge to power FE machines; the author's
[Create Crafts & Additions page](https://www.curseforge.com/minecraft/mc-mods/createaddition)
documents its motor/alternator bridge. The tested base-Create setup instead uses
our own Resonator for the condenser. The addon is optional, not a new dependency.

## Reward work and risk; charge for deployment and support

Initial solo tuning examples, not current shipped payouts:

| Contract | Work / risk | Liquid cost | Target base tokens | Value |
| --- | --- | --- | --- | --- |
| Practice / recovery | Basic local instruction | Free | Modest common supplies | Always possible to rebuild |
| Reconnaissance | 3 checkpoints, relaxed encounters, generous bonus window | 500 mB | 40 | Reliable low-cost preparation |
| Standard recovery | 4 checkpoints, ordinary encounters | 1000 mB | 100 | Campaign baseline |
| Strike | Same 4-checkpoint route, hard encounters and a tighter optional bonus | 1000 mB | 150 | Skill and risk earn more from the same travel work |
| Deep recovery | 6 checkpoints and a required defense | 2000 mB | 180 + objective bonuses | Higher total return and additional recovery opportunities |

Difficulty increases danger and rewards; it is not an arbitrary duplicate fuel
tax. Route length, party load and supplied special infrastructure determine the
launch cost. Longer routes must pay for their added work. A shorter route cannot
keep a long route's full reward. Extra supplied time/support can increase the fuel
quote and reduce the optional speed-bonus value, while retaining base completion
rewards. The current free extension to a 400% bonus window needs that tradeoff.

These numbers need survival testing. One starter-produced bucket takes about
thirty seconds of total 20 R/t production under the proposed recipe, including
essence refinement, before manual handling. A full tank is already prepared;
there is no extra launch countdown. A busy party or equipment workshop benefits
more from automation than an occasional solo trip. If native reserves remain
permanently full in a playtest, rebalance higher contracts and other consumers,
not the first departure into a compulsory AFK wait.

Extra FE should eventually permit bounded 1x/2x/4x condenser processing at
20/40/80 R/t, preserving one flake and 4000 R per essence. Native later generators
can supply those rates too. Larger factories add parallel processing and automatic
stocking. More power does not replace discoveries, cargo delivery or guardian proof.

## Safe lifecycle and party rules

- Tutorial access stays free. An always-available recovery option gives ordinary
  supplies without becoming the best rare-loot farm.
- Resolve and validate the route before charging. A refused route costs nothing.
  Snapshot its quote with the accepted run; later settings cannot change it.
- Pay once at launch. Pump failure, power loss, unloading, logout or taking longer
  must never disable an expedition's return. Existing checkpoint banking remains.
- Party costs follow the same total reward-pool scaling, including non-split
  configurations. A join pays only its incremental quote before membership and
  route extension succeed; a refused join pays nothing.
- Keep reservations and run ownership crash-consistent, with regression tests
  for rejected routes/joins, recovery and repeated start requests.

The terminal should show liquid stock, launch cost, prerequisites, base reward
and at-risk bonuses together. It accepts buckets manually and a standard fluid
handler for pipes. Essence and captured specimens continue to feed equipment,
upgrades and higher power; portals become a recurring reason to keep producing.
