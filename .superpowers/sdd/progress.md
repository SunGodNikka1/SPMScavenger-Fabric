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

No commits. No Minecraft launches (separate approval required).
