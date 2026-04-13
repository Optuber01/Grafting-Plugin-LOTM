# Tier 2.3 — Conceptual Runtime / Integration Verification

## Goal
Verify that conceptual grafting is reliable as a runtime system: activation, limits, cooldowns, cleanup, reporting, and coexistence with practical grafting.

## Current gap
Current automated coverage checks conceptual catalog/settings, but not much of the conceptual runtime lifecycle.
Manual verification is also not yet well-defined.

## Scope
Add narrow verification for conceptual runtime rules such as:
- cooldown enforcement
- max-active enforcement
- active registration/unregistration
- loop vs zone cleanup
- reload/disable cleanup
- conceptual reporting labels after separation work

Also define a small manual Paper smoke test for:
- zone placement
- loop placement and teleport behavior
- `/graft active`
- `/graft clearactive`
- `/graft reload`
- coexistence with practical grafting

## Non-goals
- No full live-server automation suite.
- No exhaustive verification of every particle/sound detail.
- No broad practical test expansion except where conceptual work touches shared paths.

## Workflow requirements
- Run this after the separation and telegraphing passes are stable.
- Prefer narrow, testable seams over heavy simulation.
- Keep the existing `testHarness` workflow as the main automated entry point.
- Do not add temp scripts or clutter.
- Do not add code comments.

## Manual smoke checklist
1. Give yourself a focus with `/graft givefocus`.
2. Open `/graft concept` and place one zone law.
3. Confirm the placement preview shows a center and rough radius before the cast lands.
4. Run `/graft active` and confirm the entry appears under the conceptual section with a conceptual label.
5. Open `/graft inspect` and confirm practical setup remains separate from any armed conceptual cast.
6. Arm **Beginning ↔ End**, confirm the second-anchor preview is visible, then walk through both anchors to verify teleporting and trigger feedback.
7. Arm **Threshold → Elsewhere** on two containers, open the source container, and confirm the destination inventory opens instead.
8. Arm **Concealment → Recognition** around hostile mobs and confirm their target recognition toward players is cancelled while inside the zone.
9. Run `/graft clearactive` and confirm all conceptual entries disappear from `/graft active`.
10. Place another conceptual graft, run `/graft reload`, and confirm conceptual state is cleared cleanly.
11. For isolated scenario checks, use `/graft reload` between conceptual smoke cases so cooldowns and active-state limits do not contaminate later checks.
12. Repeat one practical graft flow and confirm it still appears under the practical section without mixing labels.

## Acceptance criteria
- Automated checks cover conceptual lifecycle rules, not just catalog/settings loading.
- There is a repeatable manual smoke test for conceptual runtime behavior.
- Reload/disable/clear flows leave no lingering conceptual state.
- Build passes.
- Relevant tests pass.
- Worktree is clean at handoff.
