---
name: ignore-plugin-conventions
description: >-
  Conventions for the Ignore plugin in this repository (net.simpvp.ignore):
  commands, chat/PM filtering, SQLite storage, and gradual modernization to
  Purpur 26.3 / Paper APIs. Use when editing this plugin's Java sources,
  plugin.yml, or storage. Prefer English sources online; teach in Russian
  after edits.
---

# Ignore plugin conventions

## Purpose

Players can ignore each other. This plugin replaces vanilla-style social commands
(`/me`, `/tell`, `/w`, `/msg`, and related PM helpers). Avoid silent conflicts
with other chat plugins when changing command takeover behavior.

## Layout (current)

- Main: `net.simpvp.ignore.Ignore`
- Storage: `SQLite` + `Storage`; DB path historically `plugins/Ignore/ignore.sqlite`
- Commands/listeners: mostly one class per concern
- Legacy pattern: `public static Ignore.instance` — modernize carefully when touching

## Modernize policy (this repo)

- Target: Purpur **26.3**, Java **25**, Paper/Purpur + Adventure for touched code
- Prefer incremental modernization over big-bang rewrites
- Before large behavior changes (ignore rules, PM routing, DB schema), explain options and get confirmation
- Before NMS or new soft-depends, present alternatives and ask

## Do not casually break

- Command names/aliases in `plugin.yml` without an explicit product decision
- SQLite schema / `user_version` migrations — always add a forward migration
- Player-facing ignore toggle and PM/`me` filtering semantics

## When changing build/API

- Moving from Spigot 1.16 API → `purpur-api` 26.3 is expected under Modernize, but call out breaking API replacements
- Keep shade limited to true runtime deps
- Update `api-version` appropriately when bumping

## Teaching after edits

Follow the full **Teaching after edits** section in `purpur-plugin-runtime`.
After non-trivial edits: teach in Russian at basic-Java level; offer a self-check question only if the user asks or accepts when offered.
