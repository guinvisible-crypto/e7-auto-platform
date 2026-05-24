# Lua -> JSON Mapping (point.lua)

## Rule category mapping
- `cmp_*` -> `type: single_color`
  - Lua value like `"x|y|RGB,x2|y2|RGB2,..."`
  - JSON:
    - first tuple -> `anchor`
    - remaining tuples -> `checks` with `dx/dy` relative to anchor

- `mul_*` -> `type: multi_point`
  - Lua value like `{ "ANCHOR_RGB", "dx|dy|RGB|..." }`
  - JSON:
    - `anchor_rgb`
    - split chain into `offsets`

- `ocr_*` -> `type: ocr_placeholder`
  - Lua value like `{left,top,right,bottom}`
  - JSON:
    - `region`
    - optional `keywords`

## Converted examples
- `cmp_国服主页Rank` -> `home/home_rules.json::cmp_home_rank`
- `cmp_国服竞技场战斗开始` -> `arena/arena_rules.json::cmp_arena_start_cn`
- `mul_竞技场挑战` -> `arena/arena_rules.json::mul_arena_challenge_cn`
- `cmp_国服神秘商店立即更新` -> `shop/bookmark_rules.json::cmp_shop_refresh_btn`
- `ocr_国服结束` -> `stage/stage_rules.json::ocr_stage_end`
