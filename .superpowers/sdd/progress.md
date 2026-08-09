# RFC implementation progress

FS-1 through FS-5: complete (existing furnace policy, station, persistence, and goal work)
FS-6: complete (Phase 2 initializer repaired; pack/reference static gate passes)
FS-7: complete (deterministic horizontal fuel-face negotiation; U-F10 + rollback)
FS-9: complete (user-selected configurable `ironStockTarget=0` interim default)

TT-2b + FS-8: complete (consumer-owned iron-tool recipes, typed material demand, minimal IRON config activation; runtime unverified)

MI-1: complete with concerns (immutable GatherIntentPolicy integrated; 128 tests/build pass; runtime unverified)

MI-3 + MI-23: complete with concerns (generic staged NEED allocation; 131 tests/build pass; runtime unverified)

MI-13a: complete with concerns (ore exposure in pass-one candidate; 138 tests; runtime unverified)

MI-24 + MI-25 policy: complete with concerns (marginal curves + opportunity bonus; gather wire deferred MI-4; runtime unverified)

MI-4R: complete with concerns (candidate-aware wealth admission, plausible diamond depth,
tag-aware log stock; 148 tests/build pass; runtime/performance unverified)

MI-13 + MI-2: complete with concerns (DiscoveryMode classification, GatherTargetPolicy blocking>wealth
priority sort, harvest-reveal for NEWLY_EXPOSED; 155 tests/build pass; runtime unverified)

MI-4S: complete with concerns (D-MIW-028 Option A desire×proximity; saturated scan gate explicit;
158 tests pass; runtime unverified)

MI-5: complete with concerns (D-MIW-031 progression vs local gather; descent pressure unlocks
explore + lower landing bias; 165 tests pass; runtime cave seek unverified)

MI-6: complete with concerns (CaveContextPolicy; cave ore gather priority; explore under-surface
landing bias; 169 tests; runtime unverified; no MiningMemory)

MI-6A + MI-6D + MI-6B + MI-6C: complete with concerns (3D cave landings, DESCENT_IN_CAVE,
local rim, per-candidate opportunity; 178 tests; runtime unverified; 6E/6F/6G deferred)

Task 19: complete with concerns (looted diamond pick ownership across backpack/main/off hand;
181 tests/build pass; runtime loot/equipment behavior unverified)

MI-7A: complete with concerns (MiningProject session types + SavedData; 200 tests; no goal wire;
runtime unverified; MI-7B/C next)

MI-6F: complete with concerns (CaveOpportunity wired to explore landing arbitration; 213 tests;
runtime branch anti-thrash unverified)

MI-7B+C: complete with concerns (MiningBudgetUsage + NaturalDescentExhaustionPolicy + search state
on ExploringGoal; 213 tests; MI-7E gate unwired; runtime unverified)

MI-5H: complete with concerns (DescentHeadingPolicy + descent expedition routes; 218 tests;
runtime heading unverified)

MI-7D: complete with concerns (StairStepPlan/Planner/Safety; 218 tests; runtime unverified)

MI-7E: BLOCKED — MAIBS-1 FAIL (R1–R4 architecture defects); MI-7R semantic repair required before MI-14

MI-7R: complete with concerns (R1–R4 repaired; StairStepSafety + ControlledDescentCaveHandoff + ControlledDescentGoal wire; 225 tests; MAIBS static PASS_WITH_CONCERNS; runtime unverified)

MI-14C1-R1: complete with concerns (temporary-blocker episode clock via blockedSince +
currentBlocker; executorStartedAt NEVER_STARTED sentinel; 4 regression tests; full suite pass;
runtime unverified; verified commit a6e9793)

MI-14C2: repair complete (R1 commitment + R2 scheduler-wide contention + C1-R2 safe stop;
MAIBS C2 re-pass PASS_WITH_RUNTIME_UNVERIFIED; 302 tests; task-29-report)

MI-14C3: code implemented, but MAIBS-1 FAIL (task-28; C3-A…E pure tests green; 310 tests).
Integration defects: active executor's 2400-tick project budget (`>=`) shadows C3's >2400 progress
timeout; protected MOVE owners are excluded from contention without becoming another blocker.

Next execution-control work: repair MI-14C3 budget reachability and protected-MOVE classification.
Do not advance to `TunnelSearchGoal` until MAIBS-1 passes. Runtime falsification remains open and
requires separate Minecraft launch approval.

GA-OPINION: RFC created (`PROPOSED` / deferred) — `plans/RFC-ADAPTIVE-OPINION-MOOD-AND-ENGAGEMENT.md`; author Agent_ChatGPT; no implementation

No commits. No Minecraft launches (separate approval required).
