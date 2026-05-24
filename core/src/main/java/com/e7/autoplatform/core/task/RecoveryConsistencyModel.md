# Recovery Consistency Model

## Goal
Define deterministic system behavior after process crash / kill during automation execution.

## 1) Recovery granularity
- Recovery is **state-level**, not action-level.
- The engine restores:
  - scheduler state (`scriptStatus`, `exceptionCount`, `currentTaskIndex`)
  - task-local state checkpoint (e.g. `arena_task_state`, `bookmark_task_state`)
- The system does **not** guarantee exact instruction pointer recovery inside a single action.

## 2) Execution guarantee
- Task actions are **at-least-once**.
- In crash windows, the last action may be re-executed once (or more in bounded retry paths) after restart.
- Therefore all `ACTION_*` transitions must follow detection-first and idempotent-safe design.

## 3) Safety guarantee
- No infinite loops:
  - task state machines have bounded `maxSteps` and return `Retry` when exceeded.
  - task-specific retry limits exist for sensitive states (e.g. arena enter/start/wait, bookmark buy/refresh counters).
  - engine-level retry guard (`maxRetryPerTask`) bounds retry loops.
  - execution watchdog (`maxExecutionTimeMs`) bounds a single task run duration.
- No non-recoverable states:
  - unrecoverable execution paths return `Interrupted` and are surfaced to scheduler.
  - fallback paths use HomeResolver to converge toward a valid state.

## 4) Expected behavior after crash
- The system may re-run the last action due to state-level checkpointing.
- The system must converge to a valid state via:
  - state-machine transition constraints,
  - bounded retries,
  - fallback-to-home behavior,
  - scheduler recovery decision.

## 5) Developer guidelines

### 5.1 Detection-first action pattern (mandatory)
For every `ACTION_*`:
1. Detect precondition (action can be performed)
2. Detect postcondition (result already achieved?)
3. If already achieved -> skip action
4. Else execute action
5. Re-detect postcondition to confirm

### 5.2 Idempotency requirements
- Treat all actions as potentially repeated after restart.
- Ensure repeated execution does not violate correctness.
- Prefer “confirm-then-act” and “act-then-confirm” over blind tapping.

### 5.3 Counter and checkpoint discipline
- Persist task state and bounded counters frequently enough to cap replay windows.
- Do not couple task-local counters to scheduler global counters.

### 5.4 Logging requirements
Always log both dimensions explicitly:
- `schedulerExceptionCount=<n>`
- `taskExceptionCount=<n>`
And include:
- `taskName`, `state`, `action`, `decision=EXECUTE|SKIP|RETRY|INTERRUPTED`.

## 6) Non-goals
- Exactly-once action execution is **not** guaranteed.
- Action-level transactional rollback is **not** provided.
