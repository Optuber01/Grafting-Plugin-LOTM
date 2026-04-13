# Tier 1.2 — Stronger Relationship-Rewrite Conceptual Casts

## Goal
Add conceptual grafts that rewrite **relationships between things**, not just the identity of a local area.

## Current gap
The conceptual layer currently has strong zone and loop examples, but it does not yet express enough of the canon-style relationship rewriting side of Grafting / Reassembly.

## Scope
Add a small number of conceptual casts that temporarily rewrite relationships such as:
- target recognition
- anchor relationship
- transition destination
- source/endpoint association

Keep the set small, explicit, and strongly legible.

## Non-goals
- No broad entity-behavior rewrite.
- No persistent social/faction system.
- No broad pathfinding overhaul.
- No “everything can relate to everything” sandbox.
- No large menu redesign.

## Workflow requirements
- Build this after the law/identity transfer pass is stable.
- Use a proper git worktree.
- Do not add code comments.
- Keep changes isolated to the relationship-rewrite mechanics.
- Keep the repo clean.

## Acceptance criteria
- At least a few conceptual casts rewrite relationships end-to-end.
- The relationship change is visible in gameplay, not only in text.
- The effects are temporary and reversible.
- Existing conceptual grafts still work.
- Build passes.
- Relevant tests pass.
- Worktree is clean at handoff.
