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

## Acceptance criteria
- Automated checks cover conceptual lifecycle rules, not just catalog/settings loading.
- There is a repeatable manual smoke test for conceptual runtime behavior.
- Reload/disable/clear flows leave no lingering conceptual state.
- Build passes.
- Relevant tests pass.
- Worktree is clean at handoff.
