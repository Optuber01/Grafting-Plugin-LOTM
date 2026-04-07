---
date: 2026-04-08
topic: "Grafting Plugin - Attendant of Mysteries Sequence 1 Ability"
status: validated
---

# Grafting Plugin — Revised Design Document

## Problem Statement

Implement the **Grafting** authority from **Sequence 1: Attendant of Mysteries** as a standalone Minecraft Java plugin.

The goal is not a single narrow spell. The goal is a **broad, abstract reality-reassembly mechanic** that still fits inside a **small Bukkit/Paper plugin**.

The plugin must let players graft together:
- **Concrete subjects** such as entities, blocks, items, projectiles, potion payloads, enchant-like properties, containers, locations, and areas
- **Abstract concepts** such as **Sun**, **Gravity**, **Beginning**, **End**, **Distance**, and **Concealment**
- **Relationships and structure**, not just stats or damage

The ability should feel like:
- **rewriting how things are connected**
- **rewriting what something is linked to**
- **rewriting what state or effect something carries**
- **rewriting what happens at a trigger point**

It must stay deterministic, playable, and understandable in Minecraft terms.

## Constraints

- **Java with Bukkit/Paper API only**
- **No NMS, packet libraries, protocol libraries, or reflection into server internals**
- **No external database**
- **No spirituality system, Beyonder progression, marionette system, miracle system, or other pathway kit**
- **No AI interpretation** and no freeform text-to-effect casting
- **Small standalone codebase** with a lean component set
- **Deterministic authored behavior** only
- **Temporary runtime state should live in memory** and unwind safely on restart

The plugin is implementing **Grafting only**, not the whole Sequence 1 package.

## Design Shift From The Previous Draft

The previous draft tried to make Grafting universal through a large **property-vector engine**.

That version was broad on paper, but too much of the design effort went into:
- semantic extraction
- complexity scoring
- concept-distance math
- graft slots
- spirituality and instability systems
- large command and GUI surfaces

The revised direction keeps the breadth, but changes the abstraction.

**Old idea:** everything is reduced to a giant semantic profile and the engine infers a result.

**New idea:** everything resolves to a small set of **graftable aspects**, and the engine performs a known type of rewrite on those aspects.

This is the core change.

## Approach

The plugin uses an **Aspect-Based Universal Grafting** model.

### Core principle

Any valid participant in a cast becomes a **Graft Subject**.

Every subject exposes one or more **aspects** from a fixed, human-authored vocabulary. Grafting works by taking one aspect and applying a **rewrite family** to another subject or anchor.

This keeps the ability broad because:
- many different subjects can participate
- concrete and abstract sources use the same system
- the engine works on **structure and state**, not just damage values

This avoids the previous design problem because:
- there is no large semantic-distance engine
- there is no promise of infinite emergent interpretation
- every supported result is explicit, testable, and reversible

## Supported Subjects

The plugin should treat the following as first-class subjects.

### Concrete subjects

- **Blocks**
  - fire, lava, magma, doors, pressure plates, slime, honey, ice, TNT, chests, furnaces, portals, beds, daylight sensors, redstone components
- **Living entities**
  - players, hostile mobs, passive mobs, bosses if Bukkit exposes needed hooks safely
- **Items**
  - held items, dropped items, enchanted items, consumables, tipped arrows, books, tools
- **Projectiles**
  - arrows, tridents, snowballs, eggs, fireballs, splash and lingering payload carriers where practical
- **Potion and status payloads**
  - active potion effects on entities, potion-bearing items, tipped projectile payloads
- **Containers**
  - chests, barrels, hoppers, droppers, dispensers, furnaces, brewing stands where the relevant event hooks exist cleanly
- **Spatial anchors**
  - locations, doorways, area centers, region markers, path endpoints

### Abstract subjects

Abstract subjects do not come from world introspection. They come from a **Concept Registry**.

Initial concepts should include:
- **Sun**
- **Moon**
- **Gravity**
- **Beginning**
- **End**
- **Distance**
- **Binding**
- **Concealment**

More concepts can be added later, but only if they are expressed using the existing aspect vocabulary.

## Aspect Vocabulary

The plugin should ship with a **finite aspect catalog**.

This is the key to being broad without becoming vague.

### State aspects

These describe a condition or payload a subject can carry.

Examples:
- **light**
- **heat**
- **ignite**
- **freeze**
- **heavy**
- **pull**
- **sticky**
- **slippery**
- **bounce**
- **heal**
- **poison**
- **speed**
- **slow**
- **glow**
- **conceal**
- **open**
- **powered**
- **explosive**

### Relation aspects

These describe what something is connected to or aimed at.

Examples:
- **target**
- **aggro**
- **owner**
- **tether**
- **destination**
- **receiver**
- **paired-exit**
- **container-link**

### Topology aspects

These describe spatial structure.

Examples:
- **anchor**
- **entry**
- **exit**
- **surface**
- **volume**
- **path-start**
- **path-end**
- **near**
- **far**

### Sequence aspects

These describe when or how something triggers.

Examples:
- **on-enter**
- **on-hit**
- **on-open**
- **on-consume**
- **begin**
- **end**
- **return**
- **repeat**

Not every subject exposes every aspect.

Broadness comes from the fact that many different subjects normalize into the same aspect vocabulary.

Examples:
- **lava**, a **fire aspect enchant-style source**, and the abstract **Sun** concept can all expose **heat** and **ignite**
- a **door**, a **corridor marker**, and the abstract **Beginning** concept can all expose **entry** or **path-start**
- a **mob target**, a **projectile receiver**, and a **container destination** are all relation-bearing subjects

## Concrete Extraction Versus Abstract Definition

This distinction is essential.

### Concrete extraction

Concrete subjects are inspected through Bukkit/Paper APIs and mapped to aspect tokens.

Examples:
- lava exposes **heat** and **ignite**
- slime exposes **bounce**
- honey and cobweb expose **sticky** and **slow**
- TNT exposes **explosive**
- a glowing mob exposes **glow**
- a poisoned player exposes **poison**
- a chest exposes **open**, **on-open**, and possibly **container-link**
- an arrow exposes **target** or **receiver** and **on-hit**

### Abstract definition

Abstract concepts are authored in configuration or a simple registry using the same aspect tokens.

Examples:
- **Sun** exposes **light**, **heat**, **ignite**, and **radiant-style daylight behavior**
- **Gravity** exposes **heavy**, **pull**, and downward-force behavior
- **Beginning** exposes **entry**, **path-start**, and **begin**
- **End** exposes **exit**, **path-end**, and **end**
- **Distance** exposes **near**, **far**, and compression-style spatial behavior
- **Binding** exposes **tether** and anchoring behavior
- **Concealment** exposes **conceal** and suppression-style behavior

This means **Sun to block**, **Sun to area**, and **Sun to projectile** are all valid as long as the handler knows how to apply **light**, **heat**, or **ignite** to that target class.

## Rewrite Families

The plugin should organize casts around **four rewrite families**.

These are not four tiny spells. They are the four ways Grafting rewrites reality.

### 1. State Transfer

Moves or imposes a state aspect onto a target.

Typical use:
- take **ignite** from lava and apply it to a mob
- take **bounce** from slime and apply it to a player or projectile
- take **speed** from a potion payload and apply it elsewhere
- take **light** and **heat** from **Sun** and apply them to a block or area

This is the most direct family and covers many item, enchant, potion, and concept uses.

### 2. Relation Graft

Rebinds what something points to, follows, belongs to, or routes into.

Typical use:
- move hostile aggro from one entity to another
- redirect a projectile's receiver or target
- temporarily pair a container to a different destination
- tether an entity or object to a new anchor

This is the **tampering** side of Grafting.

### 3. Topology Graft

Rewrites spatial structure by linking anchors, folding paths, or changing how entry and exit connect.

Typical use:
- make a doorway lead somewhere else
- link two distant locations for a duration
- graft the start and end of a path together to create a loop
- compress distance between two anchors

This is the closest expression of the lore's location-grafting examples.

### 4. Sequence Tamper

Moves or rewrites a trigger sequence.

Typical use:
- move an **on-hit** payload from one carrier to another
- attach an **on-open** outcome to a different container or anchor
- graft **begin** and **end** behaviors together for loop-style traps or forced returns
- relay an interaction trigger from one subject to another

This is the most abstract family and should remain the most curated.

## Compatibility Model

The engine should not attempt freeform interpretation.

Instead, a cast succeeds only if:
- the **source subject** exposes an aspect in the chosen rewrite family
- the **target subject** can accept that aspect in that family
- a registered handler exists for that pairing

If any of those are false, the cast fails with a clear reason.

This is how the system remains stable while still feeling broad.

## Examples Of Broad But Deterministic Grafts

### Spatial and structural examples

- **Beginning + End + hallway anchors**
  - the path feeds back into itself for the duration
- **Doorway entry + remote rooftop anchor**
  - entering the doorway teleports the traveler to the rooftop
- **Distance + two anchors**
  - the gap between them is temporarily compressed into a short traversal

### Relation examples

- **Zombie aggro + villager**
  - the zombie switches pursuit target
- **Projectile target + another entity**
  - a fired projectile is redirected or retargeted
- **Container destination + linked chest**
  - inserted items route to the temporary linked destination while the graft lasts

### State examples

- **Lava ignite + hostile mob**
  - the mob burns
- **Sun light and heat + area**
  - the area becomes a localized daylight field with brightness and heat behavior
- **Slime bounce + projectile**
  - the projectile gains bouncing or rebound behavior
- **Potion speed payload + ally**
  - the ally receives a speed state from a transferred payload

### Sequence examples

- **Potion on-hit payload + arrow**
  - a selected projectile gains the transferred hit payload for the duration
- **On-open trigger + trapped anchor**
  - opening a marked container relays a configured outcome elsewhere
- **Begin and end pairing on a path**
  - movement through the path repeats or returns instead of completing normally

## What This Design Deliberately Removes

The plugin should no longer revolve around:
- a giant **PropertyVector** model
- complexity-distance scoring
- domain-bit math
- spirituality or stability numbers
- graft slot systems
- instability tables
- permanent graft logic
- a huge syntax-heavy command grammar

Those systems made the old design bigger without making the actual Grafting mechanic clearer.

## Casting Flow

The player interaction model should stay small and consistent.

### Standard casting flow

1. The player holds the configured **focus item**.
2. The player chooses a **rewrite family**.
3. The player selects a **source subject**.
4. If multiple compatible aspects exist, the plugin opens a small picker to choose the exact aspect.
5. The player selects a **target subject** or **target anchor**.
6. The engine validates compatibility and applies the graft.
7. The plugin shows feedback through particles, sound, action bar text, and short messages.

### Selection model

- **Concrete subjects** are selected by looking and clicking.
- **Areas and anchors** are selected by marking a point in the world.
- **Abstract concepts** are selected through a lightweight concept picker or a small command surface.
- **Multi-aspect subjects** such as enchanted items, active potion carriers, and containers open a compact chooser so the player picks the exact aspect to graft.

This keeps the UI small while still supporting broad casting.

## Runtime Limits And Balance

The plugin still needs guardrails, but they should be simple.

### Duration and cooldown

- Each rewrite family has its own **base duration** and **cooldown**.
- **Abstract concept sources** apply a configurable cooldown multiplier.
- **Area-scale** or **multi-anchor** grafts apply a configurable duration or cooldown modifier.

### Active graft limit

Each player should maintain at most:
- **one active Topology Graft**
- **one active Relation Graft**
- **one active Sequence Tamper**
- **two active State Transfers**

If a new cast would exceed the limit, the oldest graft in that family is replaced.

This replaces the old slot system with something much smaller and easier to reason about.

### Persistence rule

All active grafts are **temporary runtime effects**.

They should:
- clean up on expiry
- clean up on plugin disable
- clean up on invalid subject removal where possible
- not survive server restart

That keeps the implementation small and safe.

## Architecture

The plugin should stay lean.

### Proposed component set

- **Main plugin bootstrap**
- **Focus item service**
- **Cast session manager**
- **Subject resolver**
- **Aspect catalog**
- **Concept registry**
- **Graft engine**
- **State handler**
- **Relation handler**
- **Topology handler**
- **Sequence handler**
- **Active graft manager**
- **Small UI layer** for concept and aspect selection
- **Config and message wrapper**
- **Minimal command layer**

This remains small enough for a lightweight standalone project.

## Components

### Focus item service

Determines whether the player is holding the configured catalyst item and routes input into the casting system.

### Cast session manager

Tracks per-player temporary casting state.

This includes:
- chosen rewrite family
- selected source subject
- selected abstract concept if any
- pending aspect selection
- target anchor markers for spatial casts

This state is temporary and in-memory only.

### Subject resolver

Turns clicks, look targets, held items, projectile hits, selected areas, and concept choices into normalized **Graft Subjects**.

It should understand the supported subject classes but stay dumb about final behavior.

### Aspect catalog

Maps concrete subjects and sub-subject payloads into the shared aspect vocabulary.

This includes normalization of:
- common blocks
- entity statuses
- potion payloads
- enchant-like item properties
- projectiles
- container hooks
- spatial anchors

This is the replacement for the old property-vector system.

### Concept registry

Defines authored abstract concepts using the same aspect vocabulary.

This is how the plugin supports broad abstractions like **Sun** without pretending to infer their meaning automatically.

### Graft engine

Validates the cast and dispatches it to the correct rewrite-family handler.

The engine should answer only four questions:
- what family is being cast
- what aspect is being moved or rewritten
- what the source subject is
- what the target subject or anchor is

### Family handlers

Each rewrite family gets its own handler.

This is where the actual Minecraft behavior lives.

- **State handler** applies status, block, projectile, or area states
- **Relation handler** rewires targets, ownership-style links, aggro, routing, or tethering
- **Topology handler** manages anchor pairs, linked entries, path loops, and spatial compression effects
- **Sequence handler** manages transferred triggers and event relays

### Active graft manager

Tracks every active graft instance and is responsible for:
- expiry
- replacement
- cleanup
- reversion where applicable
- safety cleanup on plugin disable

### Small UI layer

Provides a compact selector for:
- rewrite family
- abstract concept
- exact aspect when a subject exposes several compatible ones

This should stay minimal and should not turn into a large menu system.

### Minimal command layer

Commands should support setup and power-user control, not replace the main casting flow.

Recommended commands:
- set rewrite family
- select abstract concept
- clear current cast state
- inspect current stored source or active grafts
- admin reload and focus-item utilities

## Data Flow

The runtime flow should be simple.

1. Player uses the focus item.
2. The plugin reads the current rewrite family.
3. The player selects a source subject.
4. The subject resolver produces a normalized subject.
5. The aspect catalog or concept registry exposes compatible aspects.
6. The player chooses the exact aspect if needed.
7. The player selects the target subject or anchor.
8. The graft engine validates compatibility.
9. The correct family handler creates the active graft.
10. The active graft manager tracks and later expires or reverts it.

## Error Handling Strategy

The plugin should fail clearly and safely.

- If the player has no focus item, the cast is rejected immediately.
- If a subject exposes no compatible aspect for the chosen family, the cast fails with a precise message.
- If the target cannot accept the chosen aspect, the cast fails with a precise message.
- If a required anchor, area, or subject becomes invalid, the active graft ends early and cleans up.
- If a chunk unload makes a spatial graft unsafe to maintain, the graft is suspended or removed rather than left in a broken state.
- If a handler cannot apply a pairing safely, the cast is rejected instead of attempting fallback magic.

The system should prefer **explicit rejection** over vague half-success.

## Testing Strategy

Testing should focus on breadth through representative cases, not every imagined concept.

### Subject coverage

- block sources
- entity sources
- item and enchant-style sources
- projectile sources
- potion payload sources
- container sources
- abstract concept sources
- location and area anchors

### Family coverage

- one simple and one advanced case for each rewrite family
- one area-based case
- one concept-to-concrete case
- one concept-to-area case
- one concrete-to-concept rejection case if unsupported

### Representative scenarios

- Sun to block
- Sun to area
- lava to entity
- slime to projectile
- zombie aggro to different target
- arrow hit payload to new carrier
- doorway to remote anchor
- path start and path end loop
- container destination reassignment

### Safety coverage

- expiry cleanup
- replacement of oldest graft in a family
- restart cleanup
- target death or removal cleanup
- disconnect cleanup
- chunk unload behavior for topology grafts

## Out Of Scope

The following are intentionally not part of this plugin.

- full **Spirit World** traversal simulation
- full **Realm of Mysteries** implementation
- full **Miracles** authority
- full **Regenerate** and object-marionette corruption systems
- **Resurrection** and other Sequence 1 non-grafting powers
- global permanent rule rewriting
- arbitrary freeform concept typing by players

The plugin is broad, but it is still a controlled and authored gameplay system.

## Open Questions

- **Initial concept set size**: ship with a very small core list or a broader default concept registry?
- **Sequence Tamper scope**: should the first implementation ship with only event relays and trigger transfers, or also include more path-loop behavior beyond topology grafts?
- **Container support depth**: should containers only support destination linking and trigger relays at first, or also limited shared-inventory behavior?
- **Area scale**: what default area size keeps spatial and concept-area grafts strong without becoming server-noisy?

## Final Direction

The plugin should treat Grafting as a **universal aspect-reassembly authority**.

It should feel broad because:
- almost any game object can become a subject
- abstract concepts can participate through authored manifests
- the same aspect vocabulary works across concrete and abstract casting
- the player is rewriting **state**, **relation**, **topology**, and **sequence**, not just applying a buff

That is the smallest design that still feels like **Attendant of Mysteries Grafting** instead of a normal spell system.
