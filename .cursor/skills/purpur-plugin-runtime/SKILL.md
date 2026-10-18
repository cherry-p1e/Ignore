---
name: purpur-plugin-runtime
description: >-
  Write and modernize Purpur/Paper plugin runtime code: commands, events,
  chat, permissions, scheduling, persistence, Adventure. Use when implementing
  or fixing plugin behavior. Prefer English sources online. After code edits,
  teach the student in Russian (basic Java level).
---

# Purpur plugin runtime

## Stack context

- Purpur **26.3**, Java **25**, Paper/Purpur API first
- Modernize legacy patterns when touching code
- Prefer English docs/search; teach the user in Russian

## Before implementing a feature

1. Clarify requirements if unclear.
2. Propose **several implementation options** with trade-offs.
3. Ask before NMS/reflection/internals or external plugin soft-depends (Vault, PAPI, ProtocolLib, …).
4. Only then implement the chosen option.

## Threading

- Bukkit/Paper API that touches players, worlds, entities, inventories: **main thread only**
- DB / files / HTTP: run async, then return to main with the scheduler when calling the API again
- Do not block `onEnable` with long I/O beyond quick connect/migrate

## Commands

- Prefer modern Paper command APIs / Brigadier-style registration when fitting the project
- Still keep `plugin.yml` command declarations in sync when required
- Validate sender type, args, and permissions early
- Document conflicts when replacing vanilla commands (`/tell`, `/me`, etc.)

## Events

- Prefer specific events over overly broad ones
- Use `ignoreCancelled = true` when ignoring cancelled events is correct
- Keep listeners thin; put logic in services/helpers (avoid growing static god-classes when modernizing)

## Messaging (Adventure)

- Prefer Adventure `Component` APIs over legacy `§` / `ChatColor` for new or touched code
- When migrating legacy strings, do it deliberately and keep player-facing UX stable unless asked to change it

## Persistence

- Prefer UUID keys over player names
- Use prepared statements; version the schema (migrations)
- Close resources; think about reload behavior

## Modernize guidance

When editing legacy code (static singletons, Spigot 1.16 habits):

- Move toward clearer instance/services and Paper APIs incrementally
- Prefer readable structure over cleverness
- If a change risks breaking player-facing behavior (commands, ignore rules, DB), explain the plan briefly and confirm when risk is high

## Teaching after edits (canonical)

The user is a student with roughly **basic Java**. Skill files stay in English; **explanations to the user must be in Russian**.

After any **non-trivial** code change, explain in the reply:

1. **What changed** — 2–4 plain sentences (no buzzwords).
2. **Why this way** — one concrete reason (threads, Purpur/Paper API, clarity, safety).
3. **Walk the code** — tutor-style through important lines:
   - what each key statement does
   - what the main types/objects are (`Player`, `Component`, `Event`, …)
   - control flow (who calls what, in what order)
4. **Java basics when relevant** — briefly teach concepts that appear (`extends`, interfaces, lambdas, `static`, generics) assuming basic Java only.
5. **Minecraft/Paper terms** — define once when first used (main thread, event, listener, scheduler, Adventure component).
6. **Do not** paste the whole file again; cite only the parts that matter.
7. **Self-check question** — **not by default**. You may ask once: whether the user wants a short self-check question. Only ask the question if they say yes or explicitly request it.

For tiny edits (rename, typo, one-line fix): keep the explanation short.

When researching APIs or examples, prefer **English** queries and sources; then teach the user in Russian.
