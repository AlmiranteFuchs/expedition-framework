# Field preparation — alpha.15

The loop is preparation at home, a dangerous trip, a recovered capability, then
a better prepared return. Ordinary essence stays accessible at home; captured
hearts provide choices that essence alone cannot craft.

## From the first trace to the frontier

A one-time starter search checks at most 32 candidate patches (25 surface positions each) on four rings,
32–128 blocks from world spawn. It places a cold patch only when at least twelve
air positions have sturdy natural dirt or stone beneath them. It does not replace
blocks, plant a live heart, create infection, or repeat after harvesting/restart.
The attempt and optional address persist in world SavedData. Each player receives
the recorded coordinates once on login. Ocean or obstructed spawns can refuse the
find; ordinary dormant-scar worldgen remains available in new terrain.

The terminal now uses **glass**, not amethyst. Its two essence still require the
starter condenser, iron/copper and redstone. Amethyst remains useful for specialist
instruments and the infection charm, without blocking the first deployment.

| Operation | Teaches | Opens |
| --- | --- | --- |
| Field Survey | Three checkpoints and extraction | Remote deployment, Trace Recovery |
| Trace Recovery | Three checkpoints, physical run-bound cargo at checkpoint two | Recovery proof; second completion establishes home |
| Deep Salvage | Cargo under pressure and a required defense | Guardian approach |
| Heart of the Thicket | Bound guardian and successful extraction | Liberation and severed-core attunement |

Trace Recovery has no entry fee and pays 30 guaranteed tokens. Survey pays 20.
Deep Salvage costs 15, so training earnings fund its first attempt without lucky
loot or timed bonuses. Repeating Survey can build tokens but cannot replace the
recovery proof. A player who already earned salvage/guardian proof receives the
new tutorial proof on login; fresh players do not. This does not reset existing
campaign state or add a tutorial gate to native dimension portals.

## Equipment for the next trip

| Equipment | Investment | Benefit | Limit |
| --- | --- | --- | --- |
| Resonance Cell | Dormant knot, two essence, copper and iron | Carry 16000 R from home | Starts empty; manual transfers capped at 1000, also bounded by the other device |
| Field Ward | Captured heart, two essence, iron and copper | Hold attention and pause personal dwell pressure in an eight-block sphere | 4000 R buys two minutes; existing enemies and contact infection remain |
| Heartglass Lens | Captured heart, essence, copper and amethyst | Complete a compass reading for horizontal distance and signed elevation | 200 R per reading from one carried cell |

The recipes consume the recovered heart. It can instead become the existing
Living-heart Engine. This is a choice of portable preparation or workshop power;
the crafted equipment is reusable. The issued compass retains its free bearing
and elevation ember without upgrades.

Sneak-use a generator with a cell to withdraw power. Use a receiver normally to
supply it. Hold use for repeated interactions; a machine's own transfer limits
still apply. Standard NeoForge FE item capabilities let compatible chargers fill
the cell. Its charge persists on the physical stack, including drops and saves.
It is not recalled with the expedition compass.

Place a ward and use a cell with at least 4000 R. Active wards reject additional
charges without spending them. Empty-hand use reports remaining seconds. The
deadline uses that dimension's game time and persists through chunk unloading;
unloading never freezes paid time. Breaking the instrument returns an uncharged
ward. Overlapping fields do not stack. The lookup examines only nearby loaded
chunks and never keeps camps or dimensions loaded.

A ward does not lower an accumulated attention clock. Hunts already due continue
on their existing cadence, infection still requires contact and is still possible,
and ordinary hazards remain. This buys time to sort inventory or prepare another
leg, rather than granting liberation. Leaving its radius resumes exposure.

Carry the lens and cell in the main inventory or offhand, then finish the normal
24-tick compass reading. Invalid or other-dimension targets consume nothing.
Without enough charge the free compass still works and the lens explains why it
could not measure. The new journal chapter unlocks on obtaining a heart, lens or
ward and includes all three crafting pages.

## Verification and balance limits

The preparation GameTests exercise actual machine/cell and ward interaction,
FE simulation and conserved transfer, item and ward persistence, paid deadlines,
attention resumption, lack of infection immunity, local-only paid readings,
recipe consumers, legacy tutorial migration and real completion of both training
missions. Pure unit tests cover malformed charge and bounded transfer budgets.

This is an initial alpha economy, not a completed survival pacing study. The
first-find search can refuse unsuitable terrain, a generator can be carried into
the frontier, and recovered hearts are currently shared across dimensions. More
dimension-specific specimen families should land with distinct useful consumers.

Verified alpha.15: build/alphaBundle, 172 unit tests and 154 core GameTests pass
(157 reported including three optional biome probes). Live client checks verified
a 4000 R ward activation, a paid range/height reading, starter address message and
guide opening. See `art-direction/field-kit/` for the native sprite and ward views.
