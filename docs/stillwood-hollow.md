# Stillwood route landmarks — alpha.20

The expedition's bearings gather around pale wood related to the Stillness and
the Bloom. The field guide records what the player can observe: a persistent cut,
an amber seam, and an opening where solid wood should be. It leaves the origin
of those similarities unanswered. These landmarks grant neither infection immunity
nor workshop power.

**Witness Stump** is the checkpoint. A broken, rooted sapling encloses an amber
seam in its split. It replaces the manufactured stake and dark plinth. Use it
in compass order to bank the existing checkpoint reward. The same challenges
still answer activation, with a low wooden knock and a restrained amber pulse.

**Stillwood Hollow** is extraction. A broken, three-wide, three-high trunk arches
over a person-wide opening. Uneven roots, inward bark shoulders, dead branch stubs
and sparse twig silhouettes replace the little recall machine. Dim animated resin
threads hang inside the hollow; there is no opaque portal picture. Each animation
frame is a native 16px tile. The carried item shows a miniature of the whole tree.

Walk into the hollow after meeting the route's requirements, or use any piece of
its wood. The logical server still requires every checkpoint, required objective,
physical cargo and bound guardian proof. Walking past an unfinished hollow stays
quiet. Interacting with it provides the normal refusal explanation. Another exit
address cannot settle the player's charter. Successful recall returns the party
to its terminal and keeps the existing exactly-once reward settlement.

The structure is nine actual block cells, not one oversized block model:
the stable `expedition_exit` root and eight `recall_tree_part` segments. Its complete
3×3×1 footprint, three safe floor blocks, build limits and world border are checked
before any route block is written or entry cost charged. It never cuts terrain or
builds around an obstruction. Generated routes reuse the bounded adapter jitter
and fresh-route retries; authored routes refuse when the hollow cannot fit.

Every segment is tracked in the existing SavedData marker set. Startup pruning,
per-position active-run references and run-end cleanup cover the whole tree.
Recovery validates every segment and its expected index; a missing segment fails
the expedition safely and removes the survivors. Unrelated world blocks are
preserved. The root's `grown=false` default preserves compact exits from existing
saves; only new deployments set `grown=true`. No domain ledger migration or extra
storage file is introduced.

`RecallTrees` owns the bounded placement layout. `RecallTreePartBlock` resolves
trunk interactions and upper-aperture crossings back to that root. Selection and
wood collision come from the native model elements via `FieldMachineShapes`;
the two entrance cells have empty collision. `tools/route_marker_art.py` generates
the textures, segment models, blockstates and miniature through the normal
`tools/gen_textures.py` command. No additional runtime dependency is required.

Four new GameTests exercise occupied-footprint refusal without charge/partial
placement, persisted part tracking and damaged recovery cleanup, actual aperture
entity callbacks with checkpoint gating and duplicate-reward protection, and
real trunk use plus legacy compact validation. Existing destination checks now
recognise only the exact intact, non-colliding upper aperture as valid post-placement
headroom. The synthetic End probe supplies a three-wide floor for the full tree.

Build/alphaBundle, 176 unit tests and all 165 core GameTests pass (168 reported
including three optional installed-biome probes). Item-model and guide-page checks
also pass. Alpha.19's earlier instrument study remains recorded in its commit;
this iteration supersedes its manufactured silhouette.

![Witness Stump and Stillwood Hollow in Minecraft](art-direction/route-instruments/alpha20-in-game.png)

Live debug-world verification also walked the player through the actual opening
on foot without jumping or flying. The automatic-recall test includes a required
timed objective that must be evaluated by `completeRun`; the crossing must not
reject that objective merely because it has not yet been evaluated. Unfinished
walk-through attempts suppress refusal chat, while deliberate wood interactions
still explain the unmet requirements.
