# Alpha.25: First Bearing

First Bearing is optional practice for a new expedition player. It uses two
generated witness stumps, a private portal and the ordinary Stillwood Hollow
extraction. Holding the practice compass shows its next bearing and height cue.
Field Survey remains available as the regular start.

The practice charter awards field proof, Expedition Novice and eight tokens
on extraction. It does not bank checkpoint credits or roll checkpoint hordes.
A failed practice run can be retried; a recorded completion cannot be replayed.
Other charters can opt into private admission without changing their combat,
objectives, difficulty or rewards.

Verification uses the full isolated GameTest suite plus the installed-mod
development client. The new GameTests cover fixed practice settings, rejected
guests, compass target and step synchronization, trophy timing and persistence,
replay refusal, menu-selected solo access, corrupt save rejection and the old
open-party default. Bearing unit tests cover yaw direction and wrapping.

The live verification terminal is at `1759, 98, 1881` in the dedicated
`MCP-Debug-Test-20261029` test world. Test deployments are abandoned after
checking the cue so the practice charter remains available. The authored tree
and workshop fixtures were left alone.


Final results: build and alphaBundle passed, 195 unit tests passed and all 178
core GameTests passed (181 reported including the optional biome probes).
The client loaded with installed dimension mods and Create. The
[briefing](art-direction/first-bearing/briefing.png) fits a single page
and the [compass cue](art-direction/first-bearing/compass-cue.png) remains
clear of chat. Portal entry issued the compass; using a real witness stump
advanced the run. The test run was abandoned and the terminal remains ready.
