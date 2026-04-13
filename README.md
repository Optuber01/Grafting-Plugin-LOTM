# GraftingPlugin

Plugin based on Lord of the Mysteries Grafting / Reassembly theme for Paper servers. Made as a test submission for the Minecraft Mysteria dev application.

## Install

1. Download/build the jar.
2. Copy the file into your Paper server `plugins/` folder.
3. Start the server once to generate `config.yml` and `messages.yml`.
4. Use `/graft givefocus` if you have admin permission, or have an admin give you the focus item. Balze rod by default, editable in config.

## How to use

1. Pick a mode with `/graft mode <state | link | location | event>`.
2. Pick a source with the focus or a command. Right-click to select a block/entity, or use a command.
3. Pick an aspect.
4. Cast on a target. Left-click to cast.

Also:

- Shift-right-click with no source selected to pick a fluid source. Water/lava etc.
- Uuse `/graft self` to pick yourself as the source. Kinda buggy but eh.
- `/graft concept <name>` selects a practical concept source directly.
- `/graft concept list` opens the practical concept catalog.
- `/graft inventory` opens the inventory source picker.
- To apply a **State Graft** to an item, hold the target item in your offhand and cast. (DOESNT WORK PROPERLY)
- `/graft target` opens the inventory target slot picker for item-to-item workflows. (DOESNT WORK PROPERLY)
- `/graft clear` clears the current source and aspect but keeps the current mode.

### Available modes

Practical Grafting

- **State Graft** — move a state like heat, heal, speed, poison, freeze, glow
- **Link Graft** — connect a source to a target with redirects, tethers, routing, or transfer
- **Location Graft** — bend routes and anchor points in space
- **Event Graft** — load a trigger such as On Hit or On Open

Conceptual grafting (WIP/NOT FULLY IMPLEMENTED)

- temporary zone laws
- place identity rewrites
- threshold/container rewrites
- localized rare effects with particles, sounds, and runtime tracking

After selecting a conceptual graft, left-click with the focus to place it. Zone laws take a center point. 

`/graft concept` is the rare conceptual graft menu.
`/graft concept list` and `/graft concept <name>` are for practical concept sources used in normal grafting.
Left-click a target with the focus to cast.

## Current limits/Future Improvements

- A lot of limitations. I would not consider this a faithful adaptation of the ability. I'd rate it a 7 at best.
- I'd like to make the mod more free. Currently, i would say that the basic grafts are, imo, pretty in-tune with how i understand the ability, but the conceptual grafting needs a lot of work. I am unsatisfied with how it works. I am currently stopping here because this project, which was supposed to be simple and small, is kinda getting out of hand... Technical details and context below.

## Why i made this

This is supposed to be a test submission for the Minecraft [Mysteria](https://www.mysterria.net/) dev application. Their requirements were:

"Create a small, standalone Minecraft plugin that implements a simplified version of the Reassembly (Tampering / Grafting) ability from the Attendant of Mysteries (Sequence 1, Fool Pathway). You don’t have to overdo yourself though, keep it within reasonable limits. This must be a very small, standalone Java plugin. Do not use massive external frameworks, databases, or complex packet-handling libraries unless absolutely necessary; also don’t make it even more difficult with making spirituality systems, beyonders etc. Just the spell and some way to use it is enough."

And i kinda got carried away... I'm stopping here cause i don't want to make this bigger than it actually is. Here's a list of stuff i wanted to add properly but didn't/haven't yet:

- Inventory Grafting
- Aspect-specific grafting (e.g., "I want to graft my hunger to my health")
- Crafting Recipe Grafting
- Dimensional Grafting
- Better way to select aspects
- Making the Conceptual Grafting more faithful to the ability and making it more modular. It's too rigid for my liking right now.


## Technical Details & Context

This is to explain my approach for the mod. Probably only relevant to whoever checks my application. 

When I first read the task, my immediate thought was to use an LLM to generate grafts, something closer to [Infinite Craft by Neal.fun](https://neal.fun/infinite-craft/), where concepts combine into emergent results. I dropped that idea pretty quickly because it felt far outside the scope of a small Paper plugin. (Tho this is still kinda too big i guess...)

After some research,I realized Minecraft already has a lot of useful "properties" built into it for a lot of things. That gave me the idea to simplify the problem by treating grafting as the transfer, linking, or rewriting of existing Minecraft-facing properties instead.

From there, I split the common grafting into a few bounded families:

   - **State Graft** for transferring states like heat, healing, poison, freezing, speed, glow, item damage/repair, and temporary field/block effects
   - **Link Graft** for relationships like aggro redirects, tethers, projectile retargeting, container routing, inventory handoff, and withdraw flows
   - **Location Graft** for anchor links and loops between places
   - **Event Graft** for narrow trigger-driven behavior like **On Hit** and **On Open**

Architecturally, the plugin is built using subject resolution to interpret sources/targets, compatibility validation to reject invalid combinations, planners to map valid combinations, services to execute and track active effects, and active registries/runtime trackers to manage the rest. I was trying to keep the system flexible and modular.

I treated conceptual grafting differently on purpose. I did not want concepts like Sun**, Nether**, End etc to feel like just more routine aspects in the normal practical loop. So conceptual grafts are a separate, menu-driven layer with stronger presentation and more deliberate effects. Currently, they are hardcoded because i wanted a proof of concept but if this is selected, i would like to rework it to be more modular and flexible. 

I also tried to keep the project lightweight:

- Java 21
- Paper API
- no NMS
- no ProtocolLib
- no database
- no persistence layer
- runtime state kept in memory only

The tradeoff is that active graft state does not persist across restarts, and the final result is not an unlimited freeform graft generator. It is a bounded grafting system built from reusable Minecraft-facing primitives, with a separate authored conceptual layer on top.

## Disclaimer

This project was built with the help of LLMs, specifically Claude and Codex. The code was mostly written with AI assistance, but I took the time to understand and refactor it to make it production-ready. I wouldn't say i have read all the code lines but i can say that i understand the project architecture pretty well. I relied on them because this was my first time making a Minecraft plugin from scratch and my previous experience was limited to just forking existing projects and minimally editing them back in like 2021. I mostly work on web development and front-end design as a hobby, so this was a new territory for me. I came up with the ideas of how to approach the problem myself. I did try to use Ai for any ideas but they don't really understand LOTM so they were pretty much useless. I was also using the free/trail version of Codex so i was quite limited in my use. That is related to why the project is not as polished as i would like it to be. I learned of the application and started work on April 7th 2026 after learning of it from a friend, amd as of writing it's midnight on April 14th 2026. I'm rambling. GOod night.


## Demonstrations

- Here are some demonstrations of practical grafting:

Event Graft

Grafting a lever to a chest:
![Grafting a lever to a chest](README%20Media/20260413-2117-11.6975253.gif)

State Graft

Grafting an effect from an entity to a block:
![Grafting an effect from an entity to a block](README%20Media/20260413-2121-28.9203815.gif)

Link Graft

Grafting aggro from one entity to another:
![Grafting aggro from one entity to another](README%20Media/20260413-2125-03.8083137.gif)

Location Graft

Grafting two locations together:
![Grafting two locations together](README%20Media/20260413-2127-07.4859372.gif)

- Some Conceptual Graft Examples

Grafting a sky to the ground:
![Grafting a sky to the ground](README%20Media/20260413-2059-13.2824787.gif)

Grafting concealment:
![Grafting concealment](README%20Media/20260413-2130-41.4255068.gif)

Grafting the sun to the ground at night:
![Grafting the sun to the ground at night](README%20Media/20260413-2132-11.3570609.gif)

You can check the rest yourself in game.

## All Commands

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