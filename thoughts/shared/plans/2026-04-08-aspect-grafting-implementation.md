# Aspect Grafting Plugin Implementation Plan

## Target

- Plugin: GraftingPlugin
- Branch: design/aspect-grafting
- API: Paper 1.21.11-R0.1-SNAPSHOT
- Java: 21

## Milestones

1. Bootstrap Gradle Paper project, plugin descriptor, config, focus item service, command layer, session shell, and baseline runtime services.
2. Implement subject resolution, aspect catalog, concept registry, compatibility validation, and aspect picker flow.
3. Implement State Transfer with ignite, heat, light, bounce, slow, speed, poison, concept-to-block, concept-to-area, and tests.
4. Implement Relation Graft with aggro redirect, projectile retargeting, container destination routing, and tests.
5. Implement Topology Graft with doorway reroute, distance compression, anchor lifecycle cleanup, and tests.
6. Implement Sequence Tamper with on-hit payload transfer, on-open relay, and tests.
7. Implement active graft tracking, expiry, replacement, chunk/entity cleanup, message polish, README updates, and validation.
8. Run build, automated tests, Paper smoke load, fix remaining issues, and finish with a clean working tree.

## Paper-Safe Deviations

- Topology grafts will use teleport-based anchor reroutes and compression instead of packet or dimension tricks.
- Projectile retargeting will use scheduled homing adjustments instead of packet redirection.
- Sequence Tamper will arm future projectile carriers and relay container open events instead of rewriting arbitrary server internals.
- Container behavior will stay event-hook-driven and temporary rather than shared persistent inventories.
