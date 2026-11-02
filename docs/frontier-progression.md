# Frontier progression: alpha.12 balance pass

**Historical home rules:** alpha.13 replaces home liberation with the Stillness
and first-survey readiness. See [the current starter chain](stillness-progression.md).
The other-dimension attention table below remains the frontier reference.

This is an initial playable tuning pass, not a claim of completed long-term balance.
The player can enter dangerous places early through their native portals. The
Bloom makes staying costly; liberation makes settling there practical.

## Progression and reward

- Two completed expeditions establish a foothold in a dimension. Presence then
  accumulates at half speed. Checkpoints bank their existing rewards as before.
- The Overworld retains Field Survey -> Deep Salvage -> Heart of the Thicket.
  Survey twice, recover the salvage, defeat the mission-bound guardian and extract.
- Liberating the Overworld opens remote terminal deployments for the whole world.
  Before that, the terminal shows the guardian-proof requirement. Native portals
  remain usable; their own mods still control their recipes and portal rules.
- Loaded dimensions without an authored boss mission receive a generated
  **Sever the Bloom** mission. It requires an established foothold and costs ten
  tokens. Routes still pass their existing adapter and terrain-safety checks.
- Routine completions do not clear dimensions by default. Only a bound mission
  guardian's accepted death plus successful extraction awards liberation.
- Liberation is shared: new ambient hearts stop seeding, existing hearts stop
  growing, new infection exposure stops, and personal attention/pursuit ends.
  Dormant scars and ordinary native monsters/hazards remain. Existing saves'
  cleared flags are preserved.

## Attention clock

The server displays a private boss-style bar outside the Overworld preparation
refuge. It names the current stage, next stage and time until escalation.
The following times describe an unestablished dimension other than the Overworld:

| Effective time present | State | Consequence |
| --- | --- | --- |
| 0-2 minutes | Listening | Preparation and short reconnaissance window |
| 2-4 minutes | Noticed | Message and audible warning before hunters |
| 4-7 minutes | Hunted | Two infected biome-native hunters per wave, about once a minute |
| 7-10 minutes | Overrun | Three hunters per wave, about every 35 seconds |
| 10+ minutes | Reckoning | A pursuing guardian, with continuing hunters |

The Overworld clock runs at half speed. Establishing a foothold halves the clock
again. Thus an unestablished Overworld warns at four real minutes, hunts at eight
and calls its guardian at twenty; establishing it doubles those times again.
The 128-block radius around Overworld world spawn is a preparation refuge:
attention cools and ambient hearts cannot seed there. It is not protection from
ordinary monsters or deliberately placed infectious tissue.

Each successful checkpoint removes 45 effective seconds. Leaving a dimension
or entering the refuge cools its clock by two effective seconds per real second.
Logging out and dying do not wipe exposure; death copies the residence record.
Creative/spectator players do not attract this system. Peaceful difficulty and
`doMobSpawning=false` suppress pursuit spawning.

Infection remains physical: hunters transmit it through contact/direct melee.
The clock does not silently apply infection, nausea or Darkness. A disruption
charm protects against new infection; it does not make its wearer invisible.

## Pursuit guardians and cleanup

The **Hunting Echo** is not the mission's bound guardian. Killing it cannot grant
liberation, campaign proof or rewards. It buys relief from that pursuer's cooldown.
Pursuit entities award no drops or experience. Wave bodies expire after 90 seconds;
echoes after 120 seconds, with a three-minute echo cooldown. A local and per-player
cap of seven prevents nearby players multiplying one ambush into an army.

Hunter ownership and expiry survive entity saves. Unloaded tracked bodies reserve
capacity until expiry; returning bodies discard when their owner is absent, dead,
in another dimension, in refuge, no longer sufficiently noticed, or too distant.
Liberation also discards them. Cleanup never grants boss proof. Bodies use bounded
safe-column placement and real bounding-box collision checks, including mod mobs.

The HUD service belongs to world SavedData at runtime. ExpeditionManager is a
stateless facade, so keeping HUD state on each facade would duplicate bars.

## Tuning

Global `expeditions-server.toml` keys:

- `expeditions.frontier.enabled` (true)
- `expeditions.frontier.guardianAfterSeconds` (600 effective seconds)
- `expeditions.frontier.homeRefugeRadius` (128)

Sparse per-dimension datapack overrides in `data/<namespace>/expedition_dimensions/<path>.json`:

```json
{
  "frontier": {
    "enabled": true,
    "guardian_after_seconds": 600,
    "home_refuge_radius": 128,
    "max_hunters": 7
  },
  "campaign": {
    "auto_liberation": true,
    "require_home_liberation": true,
    "require_guardian": true
  }
}
```

The resource id is the dimension id, e.g. `data/minecraft/expedition_dimensions/the_nether.json`.
Setting `campaign.require_guardian=false` opts that dimension into the legacy
completion-count clearing rule; the old `clearedAtCompletions` setting applies
only to this opt-in. Authored boss missions take precedence over generated ones.

## Verification

159 unit tests and 137 core GameTests pass (140 reported including three optional
biome probes). New coverage includes stage boundaries, overflow bounds, relief,
retreat/death persistence, deployment gates, automatic boss missions, heart refusal,
guardian spawn reservations, orphan cleanup and shared HUD service ownership.
The actual client check is recorded in `docs/alpha-playtest.md`.

Design reference: Minecraft's [ominous trial announcement](https://www.minecraft.net/en-us/article/minecraft-snapshot-24w13a)
informed the emphasis on readable escalation and finite encounters. This frontier
clock is the mod's own mechanic and is not an implementation of trial spawners.
