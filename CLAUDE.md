# Project Configuration

This project uses an autonomous multi-agent engineering workflow.

## Quick start

Run `/dev-team` in Claude Code to start an autonomous engineering run.
Optionally: `/dev-team <describe what you want built>`

## How it works

The `/dev-team` command launches a pipeline of specialized AI agents:
1. **Research** — analyzes codebase, domain, dependencies, risks
2. **Architecture** — designs the solution with ADRs and contracts
3. **Planning** — converts architecture into executable task graph
4. **Implementation** — engineers write the code
5. **Verification** — independent testers verify everything works
6. **Release** — creates checkpoint, updates project memory

Each phase has a hard gate — it must pass before the next starts.
Failed verifications trigger fix rounds (max 3), then replanning (max 2), then escalation.

## Project structure

```
project_memory/          — persistent knowledge across runs
  active_objective.md    — current goal (edit this before /dev-team)
  module_status.md       — what the repo remembers about itself
  architecture/          — living architecture docs
  adrs/                  — architecture decision records
  contracts/             — API contracts between modules
  schemas/               — data model definitions
  known_issues/          — tracked issues

checkpoints/             — run history (auto-generated)
  run/<run_id>/          — one directory per run

.claude/
  commands/dev-team.md   — the entry point command
  agents/                — all agent definitions
  skills/                — reusable capability definitions
```

## Git guidance

- `checkpoints/` can be gitignored if you don't want run history in version control
- `project_memory/` SHOULD be committed — it's the project's persistent knowledge
- `.claude/agents/` and `.claude/commands/` SHOULD be committed — they're the workflow definition
