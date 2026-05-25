# Rule Validation Report

## Scope
- Curated rules (`*_rules.json`) are loaded first.
- Full migration rules (`*_rules_full.json`) are loaded second as fallback.

## Deterministic Loading Guarantees
1. **Priority**: curated IDs always win when the same ID exists in both sources.
2. **Fallback**: full rules only contribute IDs missing from curated.
3. **Stable order**: output order is deterministic:
   - all curated IDs in source order,
   - then fallback full-only IDs in source order.
4. **No duplicate matching IDs**: merged output contains unique IDs only.

## Conflict Handling
- Any ID collision generates a `RuleConflict` entry.
- `winner=CURATED`, `skipped=FULL` for cross-source collisions.
- Duplicate IDs within curated are also tracked as conflicts (`winner=CURATED`, `skipped=CURATED`).

## Validation Checks
- **Malformed JSON**: recorded as `MalformedJson(source, reason)` and skipped safely.
- **Malformed rule entry** (missing/blank `id`): recorded as `MalformedRule(source, reason)` and skipped.

## Fixed Inconsistencies in Loader
- Added malformed JSON detection and issue reporting.
- Added malformed rule ID detection and issue reporting.
- Kept schema unchanged and matching logic untouched.
