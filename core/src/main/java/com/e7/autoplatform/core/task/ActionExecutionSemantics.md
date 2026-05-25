# Action Execution Semantics

## Core definition
All task actions are executed under the model:

- **At-least-once delivery**
- **Idempotent-safe behavior**

This means an action may be executed again after crash/restart windows, but repeated execution must not break correctness or corrupt system state.

---

## Guarantees

### 1) At-least-once
- The runtime persists task state at state-machine boundaries, not at every instruction.
- If a crash happens after action execution but before the next persisted checkpoint, the action may be replayed.

### 2) Idempotent-safe
- Before an `ACTION_*` executes, the task must perform a **postcondition-already-achieved** check.
- If the target result is already achieved, action must be skipped.
- This ensures replayed execution is safe and does not cause invalid transitions.

### 3) Bounded retries
- Retries are bounded by task-level counters and engine-level retry guards.
- Watchdog timeout bounds each task run duration.
- Therefore repeated attempts are finite and converge to success, retry handoff, or interrupted fallback.

---

## Crash window behavior

A crash window exists between:
1. action side effect on UI, and
2. next persisted state/counter write.

Expected outcome:
- After restart, the system restores **state-level checkpoint**.
- The same action may be considered again.
- Idempotent pre-action postcondition check will skip if result already exists.

This is intentional and compatible with at-least-once semantics.

---

## Retry behavior

- `TaskRunResult.Retry` means the task should be retried from the current persisted state.
- Retry does **not** imply action must execute again blindly.
- Action path must always re-evaluate detection and postcondition before executing side effects.

---

## Detection-first action pattern

Every action must follow one of:

- `Detect -> Confirm -> Act`
- `Detect -> Confirm -> Skip`

Where:
- **Detect**: precondition / signal for potential action.
- **Confirm**: validate whether desired result is already achieved.
- **Act**: execute only when confirmation says result is not yet achieved.
- **Skip**: if already achieved, move to next transition without side effects.

---

## Examples

### BUY (`BookmarkTask.ACTION_BUY`)
- Detect buy signal (`RULE_BUY`).
- Confirm slot/item still requires purchase (`stillNeedsAction`).
- If already changed/sold/consumed -> **Skip**.
- Else perform tap buy -> continue.

### REFRESH (`BookmarkTask.ACTION_REFRESH`)
- Detect refresh signal (`RULE_REFRESH`).
- Confirm refresh still needed (`stillNeedsAction`).
- If refresh effect already reflected -> **Skip**.
- Else perform refresh tap -> continue.

### START_BATTLE (`ArenaTask.START_BATTLE`)
- Detect start signal (`RULE_START`).
- Confirm battle already progressed (e.g. challenge/entry state visible).
- If already progressed -> **Skip** start tap.
- Else execute start tap -> transition to wait-result.

---

## Operational note

For observability, action decisions should be logged as structured records:
- `decision=EXECUTE` or `decision=SKIP`
- action name, state, task name
- scheduler/task exception counters and task index via unified logging.
