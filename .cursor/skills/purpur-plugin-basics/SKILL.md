---
name: purpur-plugin-basics
description: >-
  Scaffold and maintain Minecraft Purpur plugins (Maven, purpur-api 26.3,
  Java 25, plugin.yml, package layout, build). Use when creating a new
  Purpur/Paper plugin, changing build config, plugin.yml, project structure,
  or explaining Purpur plugin project setup. Prefer English sources when
  looking up docs or examples online.
---

# Purpur plugin basics

## Stack defaults

- Minecraft / Purpur target: **26.3**
- Java: **25** (`maven.compiler.release` / toolchain 25)
- Build: **Maven**
- API dependency: `org.purpurmc.purpur:purpur-api` (latest **26.3** build), `scope=provided`
- Repository: `https://repo.purpurmc.org/snapshots`
- Prefer English documentation and search queries (Purpur/Paper javadoc, docs, GitHub)

## Before implementing a new plugin or feature

1. Ask the user clarifying questions when the goal is ambiguous.
2. Present **multiple implementation options** (trade-offs: simplicity, API surface, deps, risk).
3. Wait for the user to choose before writing substantial code.
4. Especially ask before: NMS / reflection / internals, or third-party plugin APIs (Vault, PlaceholderAPI, ProtocolLib, etc.).

## Modernize policy

- Target modern **Paper/Purpur APIs** (Adventure components, current events/commands where appropriate).
- Prefer upgrading legacy Spigot/Bukkit patterns over preserving them when touching code.
- Do not use NMS unless the user explicitly picks that option after you presented alternatives.

## New plugin checklist

1. `pom.xml`: Purpur repo + `purpur-api` 26.3 + Java 25 compiler release
2. `src/main/resources/plugin.yml`: `name`, `main`, `version`, `api-version`, commands, permissions
3. Main class extends `JavaPlugin`; register commands/listeners in `onEnable`
4. Package layout: clear split of command / listener / storage / service classes
5. Build: `mvn -q clean package` → jar under `target/`
6. Shade only true runtime libraries, never the server API

## Rules

- Skill instructions are English; teaching explanations to this user are **Russian** (see Teaching below).
- Keep `api-version` in `plugin.yml` aligned with the supported Purpur range.
- Prefer Paper/Purpur APIs over CraftBukkit / NMS.

## Teaching after edits

Follow the full **Teaching after edits** section in `purpur-plugin-runtime`.
Summarize: after non-trivial edits, teach in Russian at basic-Java level; offer a self-check question only if the user asks or accepts when you offer.
