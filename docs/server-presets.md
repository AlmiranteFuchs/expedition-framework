# Server presets

Expeditions ships with the **Charter** settings shown below. For a gentler
start, use **Quiet Roads**. For a server that already knows the route and
warding systems, use **The Bloom Listens**.

Stop the server, back up the world's `serverconfig/expeditions-server.toml`,
and edit the existing values using one column. The first launch with
Expeditions generates the file. Individual dimension datapacks can override
world-level settings for their dimensions.

| Key under `expeditions` | Quiet Roads | Charter | The Bloom Listens |
| --- | ---: | ---: | ---: |
| `resonance.simple_generators` | `true` | `false` | `false` |
| `challenges.hordeChance` | `0.5` | `1.0` | `1.0` |
| `challenges.hordeSize` | `2` | `4` | `6` |
| `challenges.defenseDeviceHp` | `28` | `20` | `20` |
| `challenges.defenseWaveSize` | `2` | `3` | `4` |
| `challenges.defenseMobCap` | `4` | `6` | `8` |
| `pressure.perRunStart` | `2` | `5` | `8` |
| `pressure.perRunCompletion` | `5` | `10` | `15` |
| `frontier.guardianAfterSeconds` | `900` | `600` | `420` |
| `rewards.dropValuePerToken` | `1.0` | `1.0` | `1.25` |

Quiet Roads reduces routine horde and defense pressure and lets every native
generator provide baseline power. The Bloom Listens brings pursuit guardians
sooner in uncleared dimensions and adds pressure faster, with a larger budget for extraction bonus
drops. These settings work alongside the per-charter difficulty chosen at
the Wayroot. Campaign proof and extraction requirements apply in all three
presets.
