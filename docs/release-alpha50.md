# Alpha.50 development build

This build includes the approved item/workshop artwork, carved salvage and
machine interfaces, and dedicated Resonance Cell charging/discharging docks.
The version distinguishes the current development build from alpha.49 jars.

Wayroot desks now drop exactly one item when harvested in survival with an
axe or pickaxe. Both the loot table and mining tags were missing.

Open parties still admit guests only until the first witness stump is used.
The menu and first-witness cue now explain that boundary. A regression moves
the owner, then a second player through the physical built doorway and checks
that both arrive and share the run before sealing. No separate failure of
pre-witness guest admission was reproduced.

Pale Field Caches remain optional Aftergrowth Survey detours on liberated
ground. They do not spawn for regular Field Surveys. Their bearings now have
valid translated directions and distance. The settled first stump repeats
the hint and sends a short amber trail toward the detour. Repeating the hint
does not change route progress, rewards or the compass's assigned target.

Validation evidence is recorded in the roadmap. A separate installed-mod
client review did not finish loading alongside an existing Minecraft session;
the disposable client was closed. Do not treat that attempt as visual proof
of the new trail or as reproduction of a cache-rendering failure.

The final alpha.50 build/alphaBundle and all 197 required GameTests passed
in build/alpha50-final-rerun.log; the 203 unit tests are green. The earlier
versioned run has no final result and is retained separately as
build/alpha50-final-validation.log. Representative approved item art, the
salvage frame, machine dock sprite and Wayroot model match source bytes
inside the installable JAR.

Install build/alpha/expeditions-0.6.0-alpha.50.jar on server and clients,
replacing the previous Expeditions jar; retain the required Patchouli jar.
The matching SHA-256 file is staged beside the mod.
