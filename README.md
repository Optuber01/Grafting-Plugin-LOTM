# GraftingPlugin

Aspect-based grafting for Paper 1.21.11.

This plugin lets players select a source subject, extract one of its aspects, and apply that aspect through one of four runtime graft families:

- State Transfer
- Relation Graft
- Topology Graft
- Sequence Tamper

The implementation is standalone and lightweight:

- Java 21
- Paper API `1.21.11-R0.1-SNAPSHOT`
- no NMS
- no ProtocolLib or packet libraries
- no database
- runtime state stays in memory only

## Requirements

- Java 21 JDK
- Paper 1.21.11

Validated in this workspace with:

- Java: `OpenJDK 21.0.9`
- Paper API jar: `.tools/paper-api/paper-api-1.21.11-R0.1-20260404.205808-87.jar`

## Build

Standard Gradle build:

Windows:

```powershell
.\gradlew.bat build
```

macOS / Linux:

```bash
./gradlew build
```

Useful Gradle verification task:

```powershell
.\gradlew.bat testHarness
```

The Gradle-built jar is written to `build/libs/`.

## Offline verification (Windows)

For this restricted Windows environment, use the repo-local offline verifier instead of the Gradle wrapper:

```powershell
.\verify-offline.bat
```

Direct PowerShell entry point:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-offline.ps1
```

The offline verifier:

- requires `JAVA_HOME` to point to a Java 21 JDK, or `java`, `javac`, and `jar` on `PATH`
- compiles `src/main/java` and `src/test/java`
- uses the local Paper API jar and `.tools/deps/*.jar`
- builds `build/offline-verify/libs/GraftingPlugin-0.1.0-SNAPSHOT.jar`
- runs `com.graftingplugin.tests.TestHarness`
- exits non-zero on any compile or test failure

## Gradle wrapper limitation in restricted environments

The checked-in wrapper targets Gradle `8.14.3` and bootstraps itself from:

```text
https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
```

When outbound network access is blocked, `gradlew` / `gradlew.bat` fails before project evaluation. That is an environment bootstrap limitation, not a plugin code failure. Use `verify-offline.bat` in that case.

## Install

1. Build or verify the plugin.
   - Gradle: `./gradlew build` or `.\gradlew.bat build`
   - Offline Windows path: `.\verify-offline.bat`
2. Copy the jar from `build/libs/` or `build/offline-verify/libs/` into your server's `plugins/` directory.
3. Start the server once to generate `config.yml` and `messages.yml`.
4. Use `/graft givefocus` to get the default focus item.

## Default focus item

- Material: `BLAZE_ROD`
- Name: `Mystic Focus`
- Interaction range: `8`

The focus must be in the player's main hand.

## Casting workflow

1. Choose a family with `/graft mode <state|relation|topology|sequence>`.
2. Select a source with the focus.
   - Left-click a block or entity with the focus.
   - If nothing is clicked, the plugin falls back to the current look target.
   - If no world target is found, a non-focus offhand item can act as the source.
3. Choose an aspect with `/graft aspect <aspect>`.
4. Cast with the focus.
   - Right-click while a source and aspect are selected.
   - For State Transfer, sneak while casting to target an area instead of a single block/entity/projectile.

Use `/graft inspect` to see the current family, source, selected aspect, the supported aspect surface for the current mode, and the source-compatible aspects that are actually usable.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/graft mode <state|relation|topology|sequence>` | Select the active graft family. | `grafting.use` |
| `/graft concept <concept>` | Select an authored concept source from `config.yml`. | `grafting.use` |
| `/graft aspect <aspect>` | Select the aspect to graft. | `grafting.use` |
| `/graft inspect` | Show current cast state and compatible aspects. | `grafting.use` |
| `/graft clear` | Clear the current cast state. | `grafting.use` |
| `/graft active` | List currently tracked active grafts for the player. | `grafting.use` |
| `/graft status [player]` | Show mode, source, supported aspects, source-compatible aspects, and active graft count for a player. | `grafting.admin` |
| `/graft clearactive [player]` | Clear active graft runtime effects for a player. | `grafting.admin` |
| `/graft givefocus [player]` | Give the focus item. | `grafting.admin` |
| `/graft reload` | Reload config/messages and clear live runtime effects. | `grafting.admin` |

## Implemented graft families

The aspect vocabulary is broader than the currently shipped runtime handlers, but the plugin now filters selection and inspection to only the aspects that the current planners/runtime actually support. The list below describes those implemented behaviors.

### State Transfer

Implemented runtime behaviors:

- temporary entity effects such as glow, speed, slow, poison, heal, conceal, and freeze-style slowing
- direct heat / ignite application to entities
- temporary bounce behavior on entities
- projectile hit payload transfer for `poison`, `ignite`, and `heat`
- limited projectile bounce behavior
- temporary area or block-centered fields

One-shot projectile trait casts such as `speed`, `slow`, `light`, and `glow` are applied immediately and do not create tracked active runtime entries.

### Relation Graft

Implemented runtime behaviors:

- redirect mob aggro to a new living target
- retarget a projectile toward another entity
- retarget a projectile toward a fixed anchor location
- tether an entity or projectile to another entity
- tether an entity or projectile to a fixed location
- reroute inserted items from one container into another

### Topology Graft

Implemented runtime behaviors:

- temporary anchor links
- temporary path loops

These are driven by topology aspects such as `anchor`, `entry`, `exit`, `near`, `far`, `path-start`, `path-end`, `begin`, and `end`.

### Sequence Tamper

Implemented runtime behaviors:

- transfer an `on-hit` payload onto a projectile
- relay a container `on-open` trigger toward a stored anchor

Sequence aspects such as `on-enter`, `on-consume`, `return`, and `repeat` remain part of the shared vocabulary but are not currently selectable because they are not implemented.

## Active graft tracking

The plugin tracks active runtime grafts in memory and exposes them through `/graft active`.

Family limits:

- State Transfer: `2`
- Relation Graft: `1`
- Topology Graft: `1`
- Sequence Tamper: `1`

If a new cast would exceed a family limit, the oldest graft in that family is replaced.

`/graft active` prints entries like:

```text
Active grafts:
- [state] Bounce: Slime -> Arrow (18s)
- [topology] Anchor: Beginning -> Stone (29s)
```

Only retained runtime effects are listed. Immediate one-shot effects that do not keep service-managed state are not reported as active grafts.

## Default config overview

`config.yml` currently exposes these main sections:

- `focus`
- `state-transfer`
- `relation-graft`
- `topology-graft`
- `sequence-tamper`
- `concepts`

Current default concepts:

- `sun`
- `moon`
- `gravity`
- `beginning`
- `end`
- `distance`
- `binding`
- `concealment`

Important default timings:

- state effect duration: `20s`
- state field duration: `12s`
- relation aggro duration: `15s`
- relation projectile retarget duration: `10s`
- relation tether duration: `12s`
- topology route duration: `30s`
- sequence hit payload duration: `20s`
- sequence open relay duration: `30s`

## Development notes

- Runtime state is cleared on plugin disable and on `/graft reload`.
- Active graft tracking is in-memory only and does not survive restart.
- The custom verification entry point is `testHarness`.
- `check` depends on `testHarness`.

## Paper smoke test status

- This repository includes a Paper API jar for compilation, not a Paper server jar for startup testing.
- In a restricted environment with no preinstalled Paper server binary and no outbound download access, a local Paper smoke load cannot be executed from this repo alone.
- To smoke test manually, copy the built jar into a Paper `1.21.11` server and confirm the plugin loads, `/graft` registers, and startup logs stay clean.

## Current limitations

- The shared aspect enum is still larger than the currently implemented runtime handlers.
- Selection and inspect flows intentionally hide unsupported vocabulary instead of exposing speculative future behavior.
- Sequence Tamper is intentionally narrow right now and only supports `on-hit` and `on-open` flows.
- Active graft reporting covers retained runtime state, not every immediate side effect of a cast.
