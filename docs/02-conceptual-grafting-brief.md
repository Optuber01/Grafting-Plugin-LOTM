# Remaining Grafting Work Roadmap

## Baseline
Use the current `pi/conceptual-grafting` branch as the baseline.

Already done:
- Phase 1 cleanup and simplification
- Phase 2 practical graft and UX improvements
- initial conceptual graft menu and 6 conceptual grafts

Do not redo earlier phases.
Build on the current baseline only.

## Priority order
Work on the remaining gaps in this order:

### Tier 1 — Highest impact
1. `docs/03-conceptual-law-identity-transfer.md`
2. `docs/04-conceptual-relationship-rewrite.md`
3. `docs/05-practical-graft-breadth.md`

### Tier 2 — Strong improvements
4. `docs/06-conceptual-practical-separation.md`
5. `docs/07-conceptual-telegraphing-and-feedback.md`
6. `docs/08-conceptual-runtime-verification.md`

### Tier 3 — Nice to have
7. `docs/09-optional-conceptual-grafts.md`
8. `docs/10-repo-presentation-polish.md`
9. `docs/11-nice-to-haves-backlog.md`

## Main strategic goal
The biggest remaining problem is that conceptual grafting still reads more like curated zone effects than true LOTM-style law / identity / rule transfer.

The next work should move the plugin toward:
- stronger conceptual semantics
- stronger relationship rewriting
- broader but still controlled practical identity grafting
- clearer telegraphing and player understanding

## Guardrails
- Use a proper git worktree.
- Do not work directly in the main checkout.
- Do not add code comments.
- Keep the repo clean.
- Keep changes narrow and sequential.
- Do not re-bloat the plugin.
- Do not mix routine practical grafts into the same interaction tier as rare conceptual grafts.

## Acceptance standard
Each follow-up phase should:
- stay on the current baseline
- solve one specific gap clearly
- keep build/test green
- keep the worktree clean at handoff
