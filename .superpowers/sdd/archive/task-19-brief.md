# Task 19 brief: looted diamond pickaxe across equipment slots

## Evidence

Diamond/netherite are recognized, but ownership and equipment inspect only backpack + main hand.
No off-hand access exists in `ToolBox`, `ToolTierPolicy`, or production progression goals.

## Behavioral Prediction — MAIBS-1

Backpack/main-hand loot works; an off-hand diamond pick is ignored, causing redundant progression
and failure to draw the tool. Use one backpack/main/off-hand ownership view and swap an off-hand
winner to main hand at mining time. No new Goal, scanner, or loot handler.

Weirdness: combat re-arm/redraw is runtime-unverified; hand swapping may be visually abrupt but is
lossless; broken tools must not suppress replacement. Alternatives: manual relocation (rejected),
three-location view (selected), host-loot interception (rejected as duplication).

Gate: `PASS — BEHAVIORALLY_PLAUSIBLE`.

## Acceptance and verification

- Must: usable diamond pick in any of the three locations satisfies ownership and can be drawn.
- Must not: broken tool counts, redundant progression starts, or either held stack is lost.
- Run focused tests then `gradlew.bat clean build`; no Minecraft launch, commit, or push.
