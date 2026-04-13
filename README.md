# GraftingPlugin

A small Paper plugin that turns the Lord of the Mysteries Grafting / Reassembly theme into a focused set of in-memory runtime mechanics.

## Stack

- Java 21
- Paper `1.21.11`
- no NMS
- no ProtocolLib
- no database
- runtime state stored in memory only

## Build

```powershell
.\gradlew.bat build
```

```bash
./gradlew build
```

The plugin jar is written to `build/libs/`.

## Install

1. Build the jar.
2. Copy `build/libs/GraftingPlugin-1.0.0-final.jar` into your Paper server `plugins/` folder.
3. Start the server once to generate `config.yml` and `messages.yml`.
4. Use `/graft givefocus` if you have admin permission, or have an admin give you the focus item.

## Focus item

- Material: `BLAZE_ROD`
- Name: `Mystic Focus`
- The focus must be in the player's main hand.
- Range is controlled by `focus.interaction-range`.

## Core loop (practical grafting)

1. Pick a mode with `/graft mode <state|link|location|event>`.
2. Pick a source with the focus or a command.
3. Pick an aspect.
4. Cast on a target.

The player-facing mode names are:

- **State Graft** — move a state onto a target
- **Link Graft** — link a source to a target
- **Location Graft** — bend space between anchor points
- **Event Graft** — store a trigger that fires when something happens

Practical concept sources (Sun, Frost, Gravity, etc.) are a way to pick a source, not a separate mode.

## Conceptual grafting

Conceptual grafts are a separate, high-impact layer above practical grafting. They impose localized laws, transfer place-identity, and rewrite relationships between things.

Open the conceptual graft menu with `/graft concept`. From there, pick a conceptual graft type:

- **Sun → Ground** — solar law: light dominates, cold fails, undead burn, growth accelerates
- **Sky → Ground** — sky law: falling is denied and unsupported weight cannot descend
- **Nether → Zone** — nether law: water cannot exist and heat protects instead of harms
- **End → Zone** — end law: positions become unstable for everyone inside
- **Overworld → Zone** — foreign law is stripped and natural order is restored
- **Concealment → Recognition** — hostile recognition in the zone loses players as valid targets
- **Beginning ↔ End** — two places are treated as one route; what reaches one may finish at the other
- **Threshold → Elsewhere** — one container threshold opens another container's contents elsewhere

After selecting a conceptual graft, left-click with the focus to place it. Zone laws take a center point. Beginning ↔ End fixes the first anchor at your current position and takes the second anchor on left-click. Threshold → Elsewhere takes two container anchors: source first, destination second.

Conceptual grafts are:
- rare (one active per player by default, with a cooldown)
- temporary (zones expire after a configurable duration)
- localized (zone effects apply within a configurable radius)
- visually distinct (particles and sounds mark the zone)
- in-memory only (no persistence across restarts)

Settings are in `config.yml` under `conceptual-graft`.

## Source selection

- Right-click a block or entity with the focus.
- Shift-right-click with no source selected to pick a fluid source.
- Shift-left-click in air with no source selected to pick **Void**.
- Double right-click in air or use `/graft self` to pick yourself as the source.
- `/graft concept <name>` selects a practical concept source directly.
- `/graft concept list` opens the practical concept catalog.
- `/graft inventory` opens the inventory source picker.

## Aspect selection

- `/graft aspect <aspect>` sets an aspect directly.
- `/graft next` or `/graft cycle`, or Shift-right-click, cycles compatible aspects.
- `/graft inspect` shows the current mode, source, aspect, and available aspects.

## Casting

- Left-click a target with the focus to cast.
- To apply a **State Graft** to an item, hold the target item in your offhand and cast.
- `/graft target` opens the inventory target slot picker for item-to-item workflows.
- `/graft clear` clears the current source and aspect but keeps the current mode.

## Supported modes

### State Graft

Moves a state from the source onto a target.

Current behavior includes:
- light, glow, speed, slow, poison, heal, conceal, freeze, heavy
- ignite and heat damage
- bounce effects
- projectile payloads
- temporary block and area manifestations
- limited item repair and item damage

Targets supported by the current handlers:
- entities
- projectiles
- blocks
- fluids
- areas
- offhand items

### Link Graft

Links a source to a target.

Current behavior includes:
- redirecting mob aggro
- retargeting projectiles to entities or anchor locations
- tethering entities or projectiles to entities or locations
- routing inserted items from one container into another
- handing a selected inventory item directly into another player's inventory
- withdrawing the first available stack from a container into a player's inventory

### Location Graft

Bends space between anchor points.

Current behavior includes:
- temporary anchor links
- temporary path loops

### Event Graft

Triggers a stored effect when something happens.

Current behavior is intentionally narrow:
- **On Hit** loads a projectile with a state payload
- **On Open** relays an interaction through a stored anchor

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/graft help [page]` | Show controls and command help. | `grafting.use` |
| `/graft mode <state\|link\|location\|event>` | Select the active graft mode. | `grafting.use` |
| `/graft concept` | Open the conceptual graft menu. | `grafting.use` |
| `/graft concept list` | Open the practical concept catalog. | `grafting.use` |
| `/graft concept <name>` | Select a practical concept source directly. | `grafting.use` |
| `/graft inventory` / `/graft inv` | Open the inventory source picker. | `grafting.use` |
| `/graft target` | Open the inventory target slot picker. | `grafting.use` |
| `/graft self` | Select yourself as the current source. | `grafting.use` |
| `/graft aspect <aspect>` | Select an aspect directly. | `grafting.use` |
| `/graft next` / `/graft cycle` | Cycle to the next compatible aspect. | `grafting.use` |
| `/graft inspect` | Show the current graft setup. | `grafting.use` |
| `/graft clear` | Clear the current source and aspect. | `grafting.use` |
| `/graft active` | Show active runtime grafts for yourself. | `grafting.use` |
| `/graft debug` | Toggle focus debug logging. | `grafting.use` |
| `/graft status [player]` | Show graft status for a player. | `grafting.admin` |
| `/graft clearactive [player]` | Clear active grafts for a player. | `grafting.admin` |
| `/graft givefocus [player]` | Give the focus item. | `grafting.admin` |
| `/graft reload` | Reload config and clear runtime state. | `grafting.admin` |

## Default concept sources

Configured in `config.yml` under `concepts`:
- `sun`, `moon`
- `gravity`, `vitality`, `swiftness`, `frost`, `venom`, `radiance`
- `beginning`, `end`, `distance`, `binding`, `concealment`
- `sky`, `nether`, `end-dimension`, `overworld`

The last four (`sky`, `nether`, `end-dimension`, `overworld`) are used as concept keys by the conceptual graft system.

## Runtime behavior

- Runtime graft state is cleared on plugin disable and reload.
- Active graft tracking is in-memory only.
- Family limits are enforced per player by the active registry.
- Conceptual grafts report separately from practical grafts in `/graft active` and `/graft status`.
- Conceptual laws, loops, and rewrites use their own cooldowns and max-active limit.

## Current limits

- This is an authored aspect system, not a freeform text-to-effect engine.
- Event Graft is intentionally limited to **On Hit** and **On Open**.
- Conceptual grafts remain an authored set of rare laws, identities, and rewrites rather than a freeform engine.
- Active graft reporting covers retained runtime state, not every one-shot side effect.
