---
name: purpur-plugin-test
description: >-
  Build and smoke-test Purpur plugins locally: package the jar, run a Purpur
  26.3 server, install the plugin, verify commands/behavior. Use when the user
  asks to test a plugin, validate a jar, or set up a local Purpur smoke
  environment. Prefer English docs/search; teach the user in Russian after
  meaningful setup or fix edits.
---

# Purpur plugin test

## Goal

Prove the plugin **builds** and **loads** on Purpur **26.3**, then smoke-check key behavior.

## Defaults

- Server: Purpur **26.3** (latest build for that version unless the user pins one)
- Java: **25**
- Download API (English docs): `https://api.purpurmc.org/v2/purpur`
- Example latest jar URL pattern:  
  `https://api.purpurmc.org/v2/purpur/26.3/latest/download`
- Accept the EULA only in a dedicated local test directory (never commit `eula.txt` secrets; `eula=true` for local smoke is OK in a gitignored test server folder)

## Before heavy setup

1. Confirm whether the user wants a full local server smoke test or only `mvn package`.
2. If adding scripts, server dirs, or deps — present options (throwaway `test-server/` vs CI-only build).
3. Ask before pulling third-party plugins into the test server.

## Smoke workflow

1. `mvn -q clean package` and confirm the jar under `target/`
2. Ensure a local Purpur 26.3 jar exists (download if missing)
3. Fresh or reusable gitignored server dir (e.g. `test-server/`): put Purpur jar there
4. Copy plugin jar into `test-server/plugins/`
5. Start server with Java 25; watch logs for plugin enable errors
6. Run agreed smoke checks (join, commands, no exceptions in console)
7. Report: build OK / load OK / which checks passed / log excerpts of failures

## Rules

- Do not commit large server jars or world data; keep test server paths gitignored unless the user wants tracked scripts only
- Prefer English error-search when debugging stack traces; explain findings to the user in Russian
- Leave the test server running if useful for follow-up, unless the user asks to tear it down

## Teaching after edits

Follow the full **Teaching after edits** section in `purpur-plugin-runtime`.
After non-trivial test harness or plugin fixes: teach in Russian at basic-Java level; offer a self-check question only if the user asks or accepts when offered.
