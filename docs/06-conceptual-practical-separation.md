# Tier 2.1 — Tighten Conceptual vs Practical Separation

## Goal
Make conceptual grafting clearly distinct from practical grafting in commands, menus, runtime reporting, and player understanding.

## Current gap
The split is better than before, but still a little leaky. Players can still confuse practical concept sources with conceptual graft actions.

## Scope
- Tighten naming and flow boundaries between:
  - practical concept sources
  - conceptual graft actions
  - location/topology grafts
- Align command/help/UI wording so players can tell when they are:
  - picking a source
  - arming a conceptual graft
  - placing a conceptual zone or loop
- Make runtime reporting identify conceptual grafts distinctly.

## Non-goals
- No new conceptual graft types.
- No major command tree redesign.
- No balance changes.
- No architecture rewrite.

## Workflow requirements
- Use a proper git worktree.
- Keep terminology consistent across help, messages, README, and runtime output.
- Do not add code comments.
- Keep the repo clean.

## Acceptance criteria
- New players can distinguish practical concepts from conceptual grafts from UI/help alone.
- Runtime reporting no longer makes conceptual grafts look like ordinary location grafts.
- Help, messages, and labels agree with real behavior.
- Build passes.
- Relevant tests pass.
- Worktree is clean at handoff.
