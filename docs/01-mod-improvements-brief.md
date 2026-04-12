# Agent Brief: Mod Improvements

## Reference
- LOTM ability reference: https://lordofthemysteries.fandom.com/wiki/Fool_Pathway/Abilities#Sequence_1:_Attendant_of_Mysteries

## Context
This project is a small Paper plugin inspired by **Sequence 1: Attendant of Mysteries** and the **Grafting / Reassembly** authority.

For this phase, do **not** expand the design. Fix and slim down the current plugin first.

## Goal
Produce a **leaner, clearer, more stable** version of the current plugin.

Keep the core loop:
- pick a mode/family
- pick a source
- pick an aspect
- cast on a target

The result should feel simpler, cleaner, and easier to understand for a new player.

## Current problems
- The codebase is larger than the real supported feature set.
- Some abstractions and flows overlap or are harder to reason about than needed.
- Player-facing language is not always clear. Codebase language and player facing is different causing conflcits and issues.
- Some supported behaviors are narrow, but the surrounding architecture makes them feel broader than they are.
- Help/config/messages do not always match the actual experience cleanly.

## What to change
- Reduce unnecessary complexity.
- Remove dead, redundant, or weakly justified code paths.
- Tighten the supported feature set so it is obvious what the plugin does.
- Prefer one clear implementation path per supported mechanic.
- Make unsupported behavior fail early and clearly.
- Improve gameplay clarity for a new player.
- Keep LOTM flavor, but use **simple player-facing language**.
- Do not introduce new big systems in this phase.

## Language guidance
Keep the theme, but avoid obscure wording.

Good player-facing wording:
- Location Graft
- State Graft
- Link Graft
- Event Graft
- Concept Graft

Avoid overly technical or unclear wording for players if a simpler equivalent works.
Internal naming should match player facing ones.

## Constraints
- Keep it a **Paper plugin**.
- Keep **Java 21+**.
- No NMS.
- No ProtocolLib.
- No database.
- No persistence layer.
- Runtime state stays **in memory only**.
- Keep the solution small and practical.
- Do not redesign the whole project just to chase architecture purity.

## Non-goals
- Do not implement the full conceptual grafting vision yet.
- Do not add a freeform text-to-effect engine.
- Do not add unrelated progression, spirituality, Beyonder systems, or other pathway mechanics.
- Do not bloat the repo with planning artifacts, local tools, or build junk.

## Workflow requirements
- Use a **proper git worktree**.
- Do not work directly in the main checkout.
- Keep the directory clean.
- Do not leave stray files, temp files, logs, or build artifacts.
- **Do not add code comments.**
- Keep edits focused.
- Update tests only where behavior is intentionally changed.
- Make the final handoff easy to review.

## Acceptance criteria
- The plugin builds successfully.
- Relevant tests pass.
- The final codebase is smaller or clearly simpler than before.
- The actual supported feature set is easy to understand.
- New-player usability is improved.
- Help/config/messages match the real behavior.
- No code comments are introduced.
- The worktree is clean at handoff.

## Phase order
1. Fix and slim down the current mod/plugin.
2. Only after that, move on to conceptual grafting work.
