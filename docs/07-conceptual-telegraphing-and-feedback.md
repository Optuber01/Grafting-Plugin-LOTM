# Tier 2.2 — Conceptual Graft Previews, Telegraphing, and Feedback

## Goal
Make conceptual graft placement and active zones understandable before, during, and after activation.

## Current gap
Conceptual grafts are functional, but they still need stronger previews, clearer boundaries, stronger anchor telegraphing, and better player feedback.

## Scope
- Add lightweight pre-placement previews for:
  - zone center and approximate radius
  - two-anchor loop placement
- Improve active telegraphing for conceptual zones and loops:
  - boundary cues
  - anchor cues
  - type-specific visual identity that stays restrained
- Add direct feedback for:
  - graft armed
  - anchor set / second anchor needed
  - cancellation
  - trigger events
  - important rule interactions
  - useful expiry warnings where appropriate

## Non-goals
- No new conceptual mechanics.
- No full HUD/minimap system.
- No excessive particle spam.
- No broad overhaul of practical graft feedback.

## Workflow requirements
- Do this after the conceptual/practical separation pass is stable.
- Use the same player-facing vocabulary everywhere.
- Keep feedback lightweight enough for normal Paper runtime use.
- Do not add code comments.
- Keep the repo clean.

## Acceptance criteria
- Players get a clear preview before conceptual placement.
- Active zones and loops are legible without reading docs.
- Triggering, cancellation, and expiry-adjacent events produce consistent feedback.
- Beginning ↔ End loops are understandable from anchor telegraphing alone.
- Build passes.
- Relevant tests pass.
- Manual in-game smoke checks succeed.
- Worktree is clean at handoff.
