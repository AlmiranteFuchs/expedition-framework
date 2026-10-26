# Mod Concept & Design Pillars

**Status:** Living document — concept and direction, not final specification.

This document captures the mod's current identity, gameplay vision, and design constraints. It is intentionally incomplete. Specific names, lore, mechanics, balance values, recipes, and implementation details remain open unless explicitly marked as decided.

The implementation roadmap is maintained separately. This document describes **what the mod should feel like and why its systems exist**, not the order in which they must be implemented.

---

## 1. Core Identity

### The central idea

An explorer-engineer discovers strange phenomena in other dimensions and learns to turn them into tools, machinery, and new possibilities.

The world contains anomalous ecosystems, forgotten infrastructure, unusual materials, and phenomena that do not fully obey familiar rules. The player gradually learns how to investigate, extract, process, and harness these discoveries.

**Current identity direction:** Engineering the frontier — transforming the mysteries of other dimensions into tools.

### Player fantasy

The player is an explorer and artificer who:

- Discovers places and phenomena that are not fully understood.
- Recovers and operates ancient or abandoned infrastructure.
- Extracts unusual resources and discovers their properties.
- Builds practical technology that interacts with these discoveries.
- Establishes a base of operations and develops increasingly capable expedition equipment.
- Investigates and eventually neutralizes major threats to recover access to dangerous dimensions.

Research is a gameplay system, not a roleplay requirement. Players should be able to engage through exploration, construction, combat, collecting, or resource gathering without having to pretend to be a scientist.

### Intended feeling

**Dangerous, mysterious, inventive, and rewarding.**

The player should feel that exploration has a purpose, discoveries have practical consequences, and entering unknown territory carries meaningful risks.

Mystery should come from the world and its systems, not from withholding basic gameplay instructions.

> The system should be understandable; its origin, not necessarily.

---

## 2. Creative Direction

### Visual and mechanical language

The current direction combines:

- **Vanilla Minecraft materials:** iron, copper, redstone, amethyst, and other familiar materials.
- **Mechanical engineering:** devices, mechanisms, processing, power, and infrastructure.
- **Applied magic:** unusual materials and phenomena used through understandable systems.
- **Ancient technology:** structures and devices that can be recovered, repaired, studied, or repurposed.
- **Frontier exploration:** equipment and infrastructure that enable progressively more ambitious expeditions.

The intended aesthetic is mechanical, industrial, mysterious, and slightly steampunk — not modern military science fiction and not exclusively medieval fantasy.

Magic and technology are not necessarily opposing forces. Both can be ways to manipulate matter and phenomena. Crystals might store unusual properties, mechanisms might control their effects, and anomalous materials might enable functions that ordinary engineering cannot achieve.

These are conceptual examples, not finalized mechanics.

### Reference points

The mod draws inspiration from different aspects of other games and Minecraft mods:

- **Applied Energistics 2:** discovering and mastering unfamiliar technology.
- **Create:** expressive mechanical engineering and automation.
- **Botania:** magical systems with understandable rules.
- **Immersive Engineering:** retro-industrial machinery and infrastructure.
- **Thaumcraft:** research, discovery, and the investigation of unusual properties.
- **Ad Astra:** purposeful exploration and progression into unfamiliar environments.
- **ARC Raiders:** dangerous exploration and the feeling of operating in a hostile frontier.

These are references for individual qualities, not templates to reproduce.

### Open decisions

- Final visual identity and color palette.
- How much steampunk versus magical or ancient-tech styling to use.
- Whether the setting has a recognizable historical era.
- The names and visual language of devices, resources, and phenomena.
- How explicitly magical the technology should appear.

---

## 3. World Premise and Mystery

The world contains unusual phenomena and infrastructure whose origins are not fully understood.

Some infrastructure may be ancient, abandoned, or ownerless. Some anomalies may be consequences of a larger threat. Other discoveries may be unrelated, preserving the possibility of multiple histories, civilizations, and mysteries.

The player begins with limited knowledge and gradually learns how to interact with these systems through practical experimentation and progression.

### Narrative principles

- The player should always understand what they can do next, even when they do not understand why it works.
- Discoveries should communicate through gameplay, environments, objects, records, and system feedback.
- Mandatory NPC exposition, cutscenes, and roleplay should not be required.
- The larger story should emerge gradually rather than explain everything immediately.
- The main threat must not be the explanation for every ruin, anomaly, or mystery.
- The lore should leave room for future discoveries and expansion.

### Unresolved lore

The following remain deliberately undecided:

- Who built the ancient infrastructure.
- Whether its creators are extinct, absent, or still present.
- What the larger threat actually is.
- Whether the threat was created, released, awakened, attracted, or merely revealed by something else.
- How the infrastructure relates to the threat.
- Whether multiple civilizations or unrelated phenomena exist.
- What the player ultimately discovers about the world.

Do not treat any one explanation as canon until it is explicitly chosen.

---

## 4. The First Anomaly: The Overworld Microbiome

### Current concept

A small anomalous ecosystem appears somewhere in the Overworld. It is both an invasive or unusual biological environment and a place where signs of ancient infrastructure can be found.

The area contains unique resources that cannot be replaced entirely by ordinary materials or by external mods.

Its appearance may be related to the influence of the larger threat, but the ancient infrastructure found there does not necessarily originate from that threat.

This location is the player's initial entry point into the mod's central systems.

### Intended role

The microbiome should:

- Introduce the player to anomalous materials and their unusual properties.
- Provide the unique resource required to begin the mod's main progression.
- Encourage the player to explore, extract, transport, and process materials.
- Reveal the existence of ancient infrastructure without explaining its entire history.
- Remain useful after the initial discovery, rather than becoming an obsolete tutorial location.

### Discovery and accessibility

The microbiome should be interesting to find without making progression depend on hours of random searching.

Its precise world-generation rules, discovery hints, size, frequency, and possible alternate discovery methods remain open.

### Open decisions

- Appearance, flora, terrain, and environmental effects.
- Exact resource types and extraction methods.
- Whether the player encounters active defenses or environmental hazards.
- How the player learns to locate it.
- How extraction changes or affects the area, if at all.
- Whether multiple microbiomes can generate and whether they differ.

---

## 5. The Base and Expedition Terminal

### Current concept

The player discovers the means to build a dimensional expedition terminal, but the terminal itself is constructed and operated at the player's own base.

The microbiome is a source of essential anomalous materials, not a permanent location where the player must perform all core operations.

This creates a meaningful logistical loop: discover a resource in the field, bring it home, process it, construct equipment, and return to the field when supplies run low.

### Three independent progression requirements

The terminal's initial activation and continued operation should combine three distinct elements:

**1. Energy**

The mod provides a native energy-generation method that works in a standalone installation, with deliberately accessible but relatively slow default throughput.

Compatible external power systems may accelerate or supplement this process where supported.

**2. Anomalous resources**

A resource obtained through the mod's own content is required for activation or dimensional operations. External mods must not be able to replace this requirement entirely.

The resource should remain useful beyond the first activation, potentially supporting continued operation, advanced components, or extraction-related systems.

**3. Player interaction**

The player must personally complete a meaningful activation, assembly, calibration, or stabilization milestone.

This should represent discovery and progression rather than repetitive manual work. Once a process has been understood and unlocked, its routine components may be automated.

The exact division between initial activation costs and ongoing operating costs remains open.

### Base-building and automation

The terminal and its supporting infrastructure should support a progression from manual operation to automation.

The player should be able to:

- Extract resources from the microbiome.
- Transport them back to the base.
- Process them into usable components.
- Construct and activate the terminal.
- Improve logistics and resource production over time.

External mods such as Create or compatible transport and automation systems may make these activities more efficient. The mod should not require any particular external mod to complete its progression.

Specific integrations should be designed around the APIs and capabilities actually supported by the implementation, rather than promising universal compatibility.

---

## 6. Compatibility and Progression Philosophy

The mod should work both as a standalone experience and inside mixed modpacks containing technology, magic, automation, and exploration mods.

### Core rule

**External mods may accelerate progression, but must not bypass the mod's defining discoveries and milestones.**

This means:

- Standalone players can complete the progression using the mod's own systems.
- Automation and advanced machinery can reduce repetitive work and improve throughput.
- External energy systems may supplement or accelerate power where compatible.
- Unique anomalous resources remain tied to the mod's own world and mechanics.
- Required discoveries, player milestones, and progression gates cannot be replaced simply by supplying more power.
- The mod should not artificially weaken external systems just to force its intended pacing.

Compatibility should reward players for expanding their setups, not punish them for using other mods.

The exact supported energy, inventory, fluid, item-transfer, and automation interfaces remain implementation decisions.

---

## 7. Dimensional Exploration

### Purpose

Other dimensions are the frontier the player gradually learns to access, investigate, and recover.

Each dimension should offer meaningful discoveries, exclusive resources, collectibles, and a sense of completion. Entering a dimension should be more than visiting it once to obtain a generic material.

The mod's systems should encourage players to return with a purpose.

### Current campaign direction

Each dimension has a recovery campaign built around a sequence of expeditions:

1. **Exploration:** complete the mandatory checkpoint route and establish an operational foothold.
2. **Artifact recovery:** locate and physically extract a mission artifact.
3. **Resource extraction:** complete an expedition to obtain dimension-exclusive resources.
4. **Progression artifact:** craft a separate artifact using resources from that dimension.
5. **Threat confrontation:** use the crafted artifact to unlock and confront the dimension's major threat.
6. **Recovery:** neutralize the threat and unlock unrestricted ongoing use of that dimension and subsequent dimensional operations.

The first three mission types are intended to be required before the player can craft the progression artifact and begin the major threat event.

This is the intended campaign structure; exact mission content, costs, and unlock conditions remain subject to implementation and balancing.

**Important distinction:** the physical artifact carried out during an artifact-recovery mission is not the same item as the crafted progression artifact used to unlock the major threat.

### Mission design principles

- The mandatory checkpoint route remains the backbone of expeditions.
- Optional objectives may increase rewards, resources, or risk.
- Optional objectives must not replace the mandatory route.
- Artifact recovery should involve physically carrying and extracting the item, where applicable.
- Resource extraction should give the player a concrete reason to visit a dimension.
- Completing a dimension should feel earned and be recorded meaningfully.

The existing mission, objective, persistence, and dimension systems should be reused wherever possible. The campaign is intended to extend the current foundation, not create a parallel mission framework.

---

## 8. The Major Threat

### Current concept

Each dimension has a major threat — provisionally called the **Big Bad** — that prevents the dimension from being considered fully recovered.

Before recovery, this threat can pursue the player throughout the dimension. Exposure and time spent operating in the dimension may increase the pressure.

The threat should make exploration dangerous without making it arbitrary or unfair.

### Design principles

- Threat escalation must be communicated clearly enough for players to understand the danger.
- Difficulty should increase through legible mechanics rather than unexplained instant kills or arbitrary teleportation.
- The player should have opportunities to respond, prepare, retreat, or improve their equipment.
- The threat should be a meaningful progression obstacle, not merely a larger enemy.
- Neutralizing the threat marks the dimension as recovered.

After recovery, the special escalating threat should be disabled, controlled, or otherwise no longer operate in its pre-recovery form. The dimension should remain a usable place with its native mobs, environmental hazards, and ordinary dangers intact.

Recovery means gaining control over the expedition conditions, not sterilizing the dimension or permanently removing all risk.

### Open decisions

- Whether the threat is one entity, a force, an ecosystem, or a phenomenon.
- How it tracks or responds to the player.
- How exposure is measured and how escalation works.
- How players discover its existence and prepare for confrontation.
- Whether all dimensions share the same underlying threat or have distinct manifestations.
- What changes mechanically and visually after recovery.

---

## 9. Rewards, Collections, and Completion

Exploration should support multiple playstyles instead of rewarding combat alone.

Intended player motivations include:

- **Completionists:** fill collections, discover entries, record discoveries, and complete dimensions.
- **Gatherers:** collect exclusive materials and improve extraction efficiency.
- **Combat-focused players:** confront dangerous enemies and master escalating challenges.
- **General explorers:** investigate unusual places, uncover secrets, and obtain useful rewards.
- **Builders and engineers:** bring discoveries home and turn them into practical infrastructure.

### Reward principles

- Dimensions should have recognizable rewards and collectibles.
- Completion should be recorded and visible to the player.
- Rewards should offer utility, collection value, or new possibilities without relying on endless power creep.
- Progression should respect Minecraft's existing balance and mixed-modpack environments.
- Optional challenges should provide meaningful additional rewards without making every playstyle mandatory.

The exact collection system, bestiary, records, trophies, reward categories, and dimension-completion interface remain open.

---

## 10. The Core Gameplay Loop

The current high-level loop connects the Overworld, the player's base, and other dimensions.

**Discover → Extract → Process → Build → Activate → Explore → Recover → Expand**

1. Discover the anomalous microbiome in the Overworld.
2. Extract unique resources and learn their properties.
3. Bring resources to the base and process them into components.
4. Build the expedition terminal and supporting machinery.
5. Complete the required activation milestone and supply its operating needs.
6. Launch expeditions into other dimensions.
7. Recover artifacts, gather exclusive resources, and complete mandatory missions.
8. Craft the dimensional progression artifact and confront the major threat.
9. Recover the dimension, record the achievement, and unlock further operations.
10. Improve the base, logistics, equipment, and expedition capabilities.

Automation can gradually improve the efficiency of the loop, but it should not remove the discoveries and milestones that give it meaning.

The exact number of steps, resource quantities, mission duration, and degree of automation are not yet defined.

---

## 11. Design Pillars

These principles should guide future design decisions.

### Pillar 1 — Purposeful exploration

Every major expedition should offer a clear objective, meaningful discoveries, and a reason to return.

### Pillar 2 — Mystery without confusion

Players should understand their immediate goals and the rules they can interact with, while the world's deeper origins remain open to discovery.

### Pillar 3 — Engineering the unknown

Strange materials and phenomena should become practical tools through understandable systems of processing, construction, and operation.

### Pillar 4 — Progression through discovery

Power should come from learning, obtaining unique resources, completing milestones, and developing infrastructure — not merely from accumulating generic materials or energy.

### Pillar 5 — Automation as an amplifier

Automation should reduce repetitive work and improve efficiency. It should not be required for progression, nor should it replace unique discoveries and meaningful player actions.

### Pillar 6 — Recovery, not sterilization

A recovered dimension becomes accessible for ongoing use, but remains a living, dangerous place with its own identity.

### Pillar 7 — Multiple valid playstyles

Exploration, collection, combat, resource gathering, and engineering should each have reasons to engage with the mod.

### Pillar 8 — Integration without dependency

The mod should be complete on its own and gain additional possibilities when combined with other mods.

### Pillar 9 — Expandable mystery

The central threat and initial infrastructure should establish a foundation for the world, not explain every unusual thing in it.

---

## 12. Boundaries and Non-Goals

Unless explicitly reconsidered, the following are outside the current direction:

- Requiring specific technology or magic mods to complete the core progression.
- Requiring the player to roleplay as a researcher.
- Forcing players to operate the expedition terminal at a fixed world-generated location.
- Making the microbiome a one-time tutorial resource with no continued purpose.
- Allowing external energy or automation to bypass all unique resource and progression requirements.
- Blocking access to dimensions imposed by other mods' own portals or progression systems.
- Modifying or destroying terrain in third-party dimensions as part of recovery.
- Removing every native danger from a dimension after its recovery.
- Making combat the only meaningful way to progress or earn rewards.
- Explaining every ruin, anomaly, and discovery through one central antagonist.
- Introducing a separate mission framework when the existing systems can support the intended behavior.

These boundaries describe the current vision, not a claim that every technical edge case has already been solved.

---

## 13. Decisions Still Open

The following areas intentionally remain unspecified. They should be resolved when enough design or implementation context exists.

| Area | What remains to decide |
|---|---|
| Identity | Mod name, terminology, final aesthetic and signature visual motifs |
| Lore | Ancient builders, history, origin of anomalies, nature of the major threat |
| Microbiome | Generation, appearance, discovery hints, hazards and extraction details |
| Resources | Exact materials, rarity, processing chains, costs and ongoing consumption |
| Energy | Native generation method, throughput, storage and supported compatibility |
| Terminal | Components, construction, activation, operating costs and interface |
| Research | How properties are discovered, recorded and applied |
| Automation | Supported interfaces, upgrade paths, optional integrations and limits |
| Expeditions | Exact objectives, mission variants, optional tasks, duration and rewards |
| Threats | Escalation rules, encounters, counterplay, confrontation and recovery effects |
| Dimensions | Supported dimensions, compatibility boundaries and progression order |
| Completion | Records, collections, trophies, bestiary, rewards and presentation |
| Balance | Resource scarcity, progression pacing, difficulty, power growth and replayability |
| Narrative | How the larger story is revealed without mandatory exposition |
| Multiplayer | Shared progression, individual contributions, ownership and synchronization |
| Expansion | How future dimensions, anomalies, machines and threats fit the same framework |

An unresolved detail is not permission to silently invent a permanent rule. New proposals should be labeled as proposals until agreed upon.

---

## 14. Relationship to Implementation

This concept document is the design reference for the mod's identity and intended experience.

The implementation roadmap remains the source of truth for milestone order, current status, technical dependencies, tests, and completion evidence.

When translating this concept into implementation:

- Audit existing systems before introducing new ones.
- Extend existing mission, objective, persistence, dimension, and threat mechanisms where appropriate.
- Add new systems only when existing architecture cannot support the requirement cleanly.
- Keep the standalone progression playable without external mods.
- Validate integration behavior against real supported APIs rather than assumptions.
- Update the roadmap separately when scope or milestone boundaries genuinely change.
- Preserve the distinction between agreed requirements and mechanics that are still being explored.

A design idea does not automatically become an implementation requirement simply because it appears in this document. Its status should remain clear until it is deliberately committed to the roadmap.

---

## 15. One-Paragraph Summary

This mod is about engineering the frontier: discovering anomalous ecosystems and forgotten infrastructure, extracting unusual resources, and learning to turn strange phenomena into practical technology. The player establishes a base, constructs a dimensional expedition terminal, and explores other dimensions through purposeful missions that yield discoveries, resources, and collectibles. Each dimension contains a major threat that must be neutralized before the dimension is considered recovered, allowing continued use without erasing its natural dangers. Automation and other mods can expand efficiency and possibilities, but the mod remains complete on its own. Its systems should be understandable, its exploration purposeful, and its deeper origins mysterious enough to leave room for future discoveries.

**Guiding principle:** Make the unknown usable without making it completely understood.
