# Tier 1.3 — Practical Graft Breadth Completion

## Goal
Finish the highest-value remaining practical graft use cases, especially around **inventory, player, and container identity grafting**.

## Current gap
Practical grafting is much better than before, but some of the broader “make one thing function as another thing” use cases are still thin or only partially implemented.

## Scope
Focus on practical identity grafting around:
- player inventory
- player-held items
- containers
- container slots
- inventory-to-inventory and player-to-player practical flows where still missing

Keep the scope finite and clearly documented.

## Non-goals
- No duplication exploits.
- No persistence layer.
- No economy/trade system.
- No broad UI redesign.
- No conceptual graft menu work in this phase.

## Workflow requirements
- Use a proper git worktree.
- Keep edits focused on practical inventory/container handling.
- Do not add code comments.
- Update tests when behavior changes intentionally.
- Keep the repo clean.

## Acceptance criteria
- Inventory, item, and container identity-grafting flows are complete enough for normal use.
- Invalid combinations fail clearly.
- Help/config/messages match actual behavior.
- Build passes.
- Relevant tests pass.
- Worktree is clean at handoff.
