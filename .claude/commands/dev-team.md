You are the entry point for the autonomous engineering workflow. When the user invokes `/dev-team`, you bootstrap and hand off to the project orchestrator.

## What to do

1. **Check for an objective.** Read `project_memory/active_objective.md`. If it's empty or contains only the template, ask the user to describe what they want built or changed. Write their response to `active_objective.md`.

2. **Check for an in-progress run.** Look in `checkpoints/run/` for any `*/state.json` with `"status": "RUNNING"`. If found, ask the user:
   - **Resume** the existing run (pick up where it left off)?
   - **Abort** it and start fresh?

3. **Bootstrap the run.** Create:
   - `checkpoints/run/<run_id>/` directory (run_id = `run_YYYYMMDD_HHMMSS`)
   - `checkpoints/run/<run_id>/state.json` with initial state:
     ```json
     {
       "run_id": "<run_id>",
       "objective": "<from active_objective.md>",
       "current_phase": "ORIENTING",
       "phase_history": [],
       "fix_rounds": 0,
       "replan_rounds": 0,
       "blocked_reason": null,
       "status": "RUNNING",
       "created_at": "<ISO 8601>"
     }
     ```

4. **Launch the orchestrator.** Spawn the `project-orchestrator` agent with:
   ```
   OBJECTIVE: <objective text>
   RUN ID: <run_id>
   CHECKPOINT DIR: checkpoints/run/<run_id>/
   PROJECT MEMORY: project_memory/
   ```

5. **Report.** When the orchestrator completes, read `checkpoints/run/<run_id>/final-report.md` and present a summary to the user. Include:
   - What was built
   - How many phases ran
   - Whether any fix/replan rounds were needed
   - Suggested next steps (commit, review, test manually)

## Arguments

The user can optionally pass the objective directly:
- `/dev-team Add user authentication with JWT tokens`
- `/dev-team` (no args — will prompt for objective or read active_objective.md)

If an argument is provided, write it to `project_memory/active_objective.md` before bootstrapping.

## New vs existing project detection

Before launching the orchestrator, check if the project has existing code:
- If the working directory has source files (beyond `.claude/` and config files), it's an **existing project**.
- If the working directory is empty or only has config/docs, it's a **new project**.

Include this context in the orchestrator brief:
```
PROJECT TYPE: new | existing
```
