# Agent Brief: Conceptual Grafting

## Reference
- LOTM ability reference: https://lordofthemysteries.fandom.com/wiki/Fool_Pathway/Abilities#Sequence_1:_Attendant_of_Mysteries

## Context
This phase is about the part the current plugin missed: **conceptual grafting**.

In this project, conceptual grafting should not mean:
- a concept card that only maps to a small preset list of aspects
- a themed buff source
- a cosmetic extra panel

It should mean:
- **temporarily making one thing inherit the law, identity, rule, or behavior of something else**

## Core definition
Use this definition:

> Conceptual grafting transfers a **law, rule, identity, or environmental behavior** from a concept onto a target.

Concrete grafting transfers properties.
Conceptual grafting transfers laws.

## Goal
Build the smallest robust foundation for **concept-based rule grafting**.

This phase should make the concept panel matter.
The player should be able to pick a concept and apply it in a way that feels like reality is being rewritten, not just like a themed status effect.

## Design direction
Prioritize:
- temporary
- localized
- reversible
- understandable
- extensible

Do **not** try to rewrite the entire server or dimension globally.
Favor **anchored rule patches** over permanent world edits.

## What conceptual grafting should look like
Examples of intended behavior:
- **Sun -> Ground**
  - the ground temporarily behaves under sun-like rules
- **Overworld -> Nether zone**
  - a local area in the Nether temporarily behaves under Overworld-like rules
- **Nether -> Overworld zone**
  - a local area in the Overworld temporarily behaves under Nether-like rules
- **Beginning -> End**
  - the end of a path or loop behaves like its beginning
- **Sky -> Ground**
  - the ground temporarily inherits sky-like behavior

The result should feel like **rule substitution**, not just effect transfer.

## Scope guidance
Support a small number of strong concepts first.
Recommended early concepts:
- Sun
- Overworld
- Nether
- End
- Beginning
- End
- Distance
- Binding
- Sky
- Ground

Support only a small number of target classes at first, for example:
- block
- zone/area
- path/link
- entity
- container/inventory

## Strong design principles
- Concepts should have **clear target-dependent behavior**.
- A concept should not just be a static bag of aspects.
- Prefer a few strong, legible conceptual grafts over many weak ones.
- Keep player-facing language simple.
- Keep LOTM flavor, but do not rely on obscure terminology.

## Constraints
- Keep it a **Paper plugin**.
- Keep **Java 21**.
- No NMS.
- No ProtocolLib.
- No database.
- No persistence layer.
- Runtime state stays **in memory only**.
- Keep it lean.
- Build a foundation, not an endless combination engine.

## Non-goals
- Do not implement infinite freeform reality editing.
- Do not add full dimension rewriting.
- Do not add unrelated systems or lore mechanics.
- Do not solve this with huge hardcoded pair tables for every possible combination.
- Do not turn the concept panel into a cosmetic picker with no real gameplay weight.

## Workflow requirements
- Use a **proper git worktree**.
- Do not work directly in the main checkout.
- Keep the directory clean.
- Do not leave temp files, logs, or build junk.
- **Do not add code comments.**
- Keep the implementation focused and reviewable.

## Acceptance criteria
- Conceptual grafting is represented as **law/rule/identity transfer**, not just themed effects.
- At least a few core examples are implemented cleanly.
- The concept panel has real gameplay value.
- The implementation stays localized and reversible.
- The system is understandable for players.
- The codebase remains lean.
- Relevant tests pass.
- No code comments are introduced.
- The worktree is clean at handoff.

## Phase order
This phase comes **after** the mod-improvements phase.
Do not expand conceptual grafting on top of a messy or bloated baseline.
