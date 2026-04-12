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

Useful verification task:

```powershell
.\gradlew.bat testHarness
```

The plugin jar is written to `build/libs/`.

## Install

1. Build the jar.
2. Copy `build/libs/GraftingPlugin-0.1.0-SNAPSHOT.jar` into your Paper server `plugins/` folder.
3. Start the server once to generate `config.yml` and `messages.yml`.
4. Use `/graft givefocus` to get the focus item.

## Focus item

- Material: `BLAZE_ROD`
- Name: `Mystic Focus`
- The focus must be in the player's main hand.
- Range is controlled by `focus.interaction-range`.

## Core loop

1. Pick a mode with `/graft mode <state|link|location|event>`.
2. Pick a source with the focus or a command.
3. Pick an aspect.
4. Cast on a target.

Legacy mode aliases are still accepted, but the player-facing names are:

- **State Graft**
- **Link Graft**
- **Location Graft**
- **Event Graft**

Concept sources are a way to pick a source, not a separate mode.

## Source selection

- Right-click a block or entity with the focus.
- Shift-right-click with no source selected to pick a fluid source.
- Shift-left-click in air with no source selected to pick **Void**.
- Double right-click in air to pick yourself as the source.
- `/graft concept [name]` opens the concept list or selects a concept directly.
- `/graft inventory` opens the inventory source picker.

## Aspect selection

- `/graft aspect <aspect>` sets an aspect directly.
- `/graft next` or Shift-right-click cycles compatible aspects.
- `/graft inspect` shows the current mode, source, aspect, and available aspects.

## Casting

- Left-click a target with the focus to cast.
- To apply a **State Graft** to an item, hold the target item in your offhand and cast.
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
| `/graft mode <state|link|location|event>` | Select the active graft mode. | `grafting.use` |
| `/graft concept [name]` | Open the concept list or select a concept source directly. | `grafting.use` |
| `/graft inventory` | Open the inventory source picker. | `grafting.use` |
| `/graft aspect <aspect>` | Select an aspect directly. | `grafting.use` |
| `/graft next` | Cycle to the next compatible aspect. | `grafting.use` |
| `/graft inspect` | Show the current graft setup. | `grafting.use` |
| `/graft clear` | Clear the current source and aspect. | `grafting.use` |
| `/graft active` | Show active runtime grafts for yourself. | `grafting.use` |
| `/graft debug` | Toggle focus debug logging. | `grafting.use` |
| `/graft status [player]` | Show graft status for a player. | `grafting.admin` |
| `/graft clearactive [player]` | Clear active grafts for a player. | `grafting.admin` |
| `/graft givefocus [player]` | Give the focus item. | `grafting.admin` |
| `/graft reload` | Reload config and clear runtime state. | `grafting.admin` |

## Default concept sources

Configured in `config.yml`:
- `sun`
- `moon`
- `gravity`
- `vitality`
- `swiftness`
- `frost`
- `venom`
- `radiance`
- `beginning`
- `end`
- `distance`
- `binding`
- `concealment`

## Runtime behavior

- Runtime graft state is cleared on plugin disable and reload.
- Active graft tracking is in-memory only.
- Family limits are enforced per player by the active registry.

## Current limits

- This is an authored aspect system, not a freeform text-to-effect engine.
- Event Graft is intentionally limited to **On Hit** and **On Open**.
- Active graft reporting covers retained runtime state, not every one-shot side effect.
