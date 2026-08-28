# Subagent-driven development progress

## Active frontiers

| Item | Status | Artifact |
| --- | --- | --- |
| **RT-MI-TS1** | Open — runtime falsification; launch not authorized | `task-33-brief.md` |
| **V1-D village perception driver** | **DONE** — VR-T1A **PASS**; diagnostics removed | `task-45-report.md` |
| **V1.5 Settlement attachment & return** | **CLOSED** — VR-T1.5a–c PASS; task-46 **DONE** | `task-46-report.md` |
| **V2 Trading** | **CLOSED** — VR-T2 PASS | `task-47-report.md` + V2-TE |
| **V3 Village Work** | Implementation complete; certification partially complete/open. Gate 0 + T3j **RUNTIME PASS**; `D-VR-088` closure minimization **LOCKED**. Remaining runtime rows a/b/d/e/g/i/k/l will resume from the future validation artifact and do not block V4 | `task-58-report.md`, `task-59-report.md`, `VR-T3-RUNTIME-MATRIX.md` |
| **V4-P0 tooling extraction** | **DONE_WITH_CONCERNS / STATIC+PACKAGE ACCEPTED (`D-VR-096`)** — production/validation split built and audited; live sidecar loading remains unverified without runtime approval | `task-60-report.md` |
| **V4-R0 settlement representation** | **DONE / STATIC+PACKAGE ACCEPTED (`D-VR-089`)** — independent `homeAnchor`, deterministic tier migration, rekey/eviction/save-load coverage; no V4 behavior producer | `task-61-report.md` |
| **V4-A known trader evidence** | **DONE / STATIC+PACKAGE ACCEPTED (`D-VR-090`)** — bounded component-exact positive capabilities in existing village-memory lifecycle; live vanilla-board observation only; no market authority | `task-62-report.md` |
| **V4-D VillageIntent** | **DONE / STATIC+PACKAGE ACCEPTED (`D-VR-091`)** — transient reason/destination only; stable material+consumer identity, live route/destination revalidation, interruption suspension, lifecycle cleanup; no authority or movement | `task-65-report.md` |
| **V4 Architecture** | `D-VR-089…096` + `D-VR-042-A1` **LOCKED**; V4-P0 through V4-F are static/package accepted on SPM v0.96; V4-G validation sidecar/runtime fixture is **PREPARED**, physical closure remains launch-gated and `UNVERIFIED` | `task-68-report.md`, `docs/porting/V4-G-RUNTIME-RUNBOOK.md` |

**Task-59 authorization scope (2026-08-22):**

| Scope | Status |
| --- | --- |
| Static/build work | **AUTHORIZED** |
| `spm_vr` preset + runtime matrix preparation | **AUTHORIZED** |
| Pre-launch fixture sub-slice (`spm_vr` bodies, structural test, env pin) | **DONE** (2026-08-22) |
| Minecraft / `runClient` / batched VR-T3 execution | **NOT AUTHORIZED** |

**Checkpoint before further runtime:** install only an explicitly approved matching pair of the clean
production artifact and separately packaged validation mod. Then redesign/statically review the minimal T3l host-hook
witness and obtain exact validation-artifact approval for the reduced eight-row campaign. T3j and
D-VR-084 must not be rerun; standalone T3c/T3h/T3m sessions are not closure obligations.
| **GAO-6** | **CLOSED** (GAO-6R repair) — SPM entity bridge + social sub-episodes; 574 tests | `task-37-report.md` (GAO-6R section) |
| **GAO-7** | **CLOSED / STATIC ACCEPT** — PersonalityModel; 581 tests | `task-39-report.md` |
| **GAO-8A** | **CLOSED / STATIC ACCEPT** — passive physical expression; 593 tests | `task-40-report.md` |
| **GAO-9** | **CLOSED / STATIC ACCEPT** — bounded overland environment affinity; 618 tests | `task-41-report.md` |
| **GAO-10 / Task 44D** | **COMPLETE / STATIC ACCEPT** — 44D-R1 DONE-time ownership repaired; 809 tests | `task-44d-report.md` |
| **RT-GAO-1** | **Narrowed** — static/MAIBS sufficient for parity, GAO-5B math, RET lifecycle; runtime only for `RUNTIME_QUESTION` (nav, SPM goal contention, perf) | RFC GAO validation topic |
| **RET-GAO-1** | **DONE** (static) — bounded live + frozen snapshot store; runtime `UNVERIFIED` | `task-35-report.md` |

## Task ledger

Task 6: complete (FS-6 Phase 2 furnace datapack repair)
Task 7: complete (horizontal furnace fuel-face negotiation)
Task 8: complete (TT-2b + FS-8 consumer iron tools)
Task 9: complete (MI-1 gather intent consolidation)
Task 10: complete (MI-3 / MI-23 resource need layers)
Task 11: complete (MI-13a perception legitimacy)
Task 12: complete (MI-24 / MI-25 wealth curves — policy)
Task 13: complete (MI-4R candidate-aware wealth repair)
Task 14: complete (MI-13 + MI-2 discovery / target priority)
Task 15: complete (MI-4S D-MIW-028 scale repair)
Task 16: complete (MI-5 explore downward bias)
Task 17: complete (MI-6 cave opportunistic ore)
Task 18: complete (MI-6A/6B/6C/6D cave landings + rim)
Task 19: complete (looted diamond pick equipment slots)
Task 20: complete (MI-7A MiningProject session state)
Task 21: complete (MI-6F CaveOpportunity wiring)
Task 22: complete (MI-7B+C budget + descent exhaustion)
Task 23: complete (MI-5H DescentHeadingPolicy)
Task 24: complete (MI-7D StairStep plan + safety)
Task 25: blocked → superseded by task 26 (MI-7E MAIBS FAIL)
Task 26: complete (MI-7R controlled descent semantic repair)
Task 27: complete (MI-14C2 execution intent & arbitration)
Task 28: complete (MI-14C3 progress lease — superseded by task 30 for R1)
Task 29: complete (MI-14C2 repair package)
Task 30: complete (MI-14C3-R1 protected interruption lease)
Task 31: complete (PERF slices 0A–2)
Task 32: complete (PERF slice 4A; RFC closed)
Task 34: complete with concerns — GAO-5 current-place inversion documented (548 tests)
Task 35: complete — RET-GAO-1 bounded live/frozen registry; 553 tests
Task 36: complete — GAO-5B destination route ranking; semantic inversion fixed (556 tests)
Task 37: complete — GAO-6 ENTITY bridge + social expedition emitter (569 tests)
Task 38: complete — GAO-6R social sub-episode repair; GAO-6 CLOSED (574 tests)
Task 39: complete with concerns — GAO-7 PersonalityModel static accept (581 tests; runtime visual salience unverified)
Task 40: complete with concerns — GAO-8A passive physical expression (593 tests; runtime visual cadence unverified)
Task 41: complete with concerns — GAO-9 overland environment affinity (618 tests; runtime distribution/performance unverified)
Task 44D: complete with concerns — exact FriendlyGreet binding + 44D-R1 causal completion repair (809 tests; physical runtime unverified)
Task 46: complete — V1.5 A–D (1.11.0); VR-T1.5a–c CLOSED (Bob/God, 2026-08-15); debug commands removed; frontier → V2 Trading

Archived briefs/reports: `archive/` (tasks 6–32). Index: `archive/INDEX.md`.

## Program rollup

**Furnace / tools (FS, TT):** FS-1…FS-9 and TT-2b complete; runtime largely unverified.

**Mining intelligence (MI):** MI-1 through MI-14C3-R1 static complete; tunnel executor shipped;
**RT-MI-TS1** is the MI runtime frontier (`task-33-brief`).

**GA-OPINION:** GAO-0 through GAO-9 (**CLOSED / STATIC ACCEPT**) + GAO-4.1 + RET-GAO-1 (618 tests, MAIBS); runtime reserved for engine/SPM/perf `RUNTIME_QUESTION`s only — not a default feature gate.

**PERF:** RFC closed (task 32); craft-table phased scan shipped.

Task 47: complete (V2-A CLOSED — VillagerTradeAdapter + OfferSnapshot + TradeTransaction, two-cost E2E; 974 tests, 5 negative controls; #take wording reconciled; runtime VR-T2 deferred to V2-E/H)

Task 48: complete (V2-B TradeEvaluationPolicy — pure offer scoring; 986 tests, 3 negative controls; V2-C owns route admission)

Task 49: complete (V2-C TradeDemandRegistrar - acquisition feasibility, 10 gates; 998 tests, 3 negative controls; stateless so gate 9 is unrepresentable)

Task 50: complete (V2-D transient TradeChainPlan SELL->BUY; 1015 tests; 3 architecture-defect probes unreachable)

Task 51: complete (V2-E + R1 review repairs - P0 route-feasibility wiring, attempt tick bound, real offer index, offhand, continuation revalidation; 1040 tests, 6 negative controls; runtime UNVERIFIED, VR-T2 held)

Task 52: complete with concerns (D-VR-084 shared MandatoryOwnership + V2-DEF-002 repair — claim registry + pure decision + director wiring + VILLAGE_TRADE blocker + Gather publisher; 12 scenarios + 2 temporal simulations + producer controls, 53 tests; 1357 total, 0 failures; R1 single-authority scanCovers repair + mutation matrix; V2-DEF-002 four-part status; multi-publisher identity-bound release prerequisite recorded; runtime witness deferred to V3 campaign)

Task 53: complete with concerns (V3-A — VillageScenarioProfile + PlayerMobVillagePolicySavedData + VillageWorkAdmission + VILLAGE_WORK taxonomy + operator commands; 29 new tests; 1386 total, 0 failures; S1/S10 mutation-confirmed; no P4 goal/selector/classifier pin; admission not wired to executor by design; runtime UNVERIFIED)

Task 54: complete / STATIC-BEHAVIORAL ACCEPT (V3-B + R1/R1.2 repair — 1435 tests, 0 failures; owner/share invariant + replacement + stale-placement unit-confirmed; runtime VR-T3g–i UNVERIFIED — deferred to batched V3 campaign)

Task 55: STATIC-BEHAVIORAL ACCEPT (V3-C + R1 + closure CLOSE-1/2 — fail-open crop continuation, probe-budget backoff only on concrete failures; 1478 tests, 0 failures; runtime VR-T3 deferred to batched V3 campaign)

Task 56: STATIC-BEHAVIORAL ACCEPT (V3-D + CLOSE-56-1/2 — anchor supersede cancelPending + real enumeration caps; 1499 tests, 0 failures; runtime VR-T3e/f UNVERIFIED) — `task-56-report.md`

Task 57: CLOSED / STATIC-BEHAVIORAL ACCEPT (V3-E population food — CLOSE-57-1/1R/1F + CLOSE-57-2 + CLOSE-57-3; 1543 tests, 0 failures; runtime VR-T3e/j UNVERIFIED deferred to batched V3 campaign) — `task-57-report.md`
Task 57: Gate 0 PASS — `task-57-gate0-report.md`
Task 57: peer review ACCEPT — `task-57-peer-review.md`
Task 57: brief v1.2 LOCKED — `task-57-brief.md`

Task 58: brief v1.1 — `task-58-brief.md` (V3-F unified ComposterWorkFacts + CompostGoal)
Task 58: Gate 0 PASS — `task-58-gate0-report.md`
Task 58: complete — V3-F STATIC-BEHAVIORAL ACCEPT (`task-58-report.md`, CLOSE-58-1…3)

Task 59: Gate 0 + VR-T3j **RUNTIME PASS** on `BDAA788C...A0B2BDC`; `D-VR-088` closure policy LOCKED — D-VR-084/T3c/T3h satisfied without standalone runtime, T3m static substitution paired with T3a, remaining runtime a/b/d/e/g/i/k/l. Harness preservation moves to the `D-VR-096` validation artifact; certification remains open (`task-59-report.md`)

Task 60: **DONE_WITH_CONCERNS / STATIC+PACKAGE ACCEPTED** — V4-P0 extracted Task-59 into `spmscavenger_validation`, retained passive General Debug, and passed the normal dual-artifact build/audits (1,624 production + 57 validation tests; zero failures). Live sidecar loading remains UNVERIFIED without launch approval (`task-60-report.md`)

Task 61: **DONE / STATIC+PACKAGE ACCEPTED** — V4-R0 removed `SettlementTier`, moved exactly one optional home to `MobVillageMemory.homeAnchor`, added deterministic legacy migration/dirty rewrite, rekey/eviction/corruption/save-load tests, and passed 1,635 production + 57 validation tests (`task-61-report.md`)

Task 62: **DONE / STATIC+PACKAGE ACCEPTED** — V4-A added bounded `KnownVillager` positive capability evidence inside `MobVillageMemory`, component-exact registry-aware persistence, 168,000-tick physical pruning, selective live-board invalidation, simultaneous 16/64/16 bounds, rekey/eviction/permanent-cleanup coverage, and passive observation of the already-read vanilla V2 board. No ranking/director/COMMUTE/home promotion (`task-62-report.md`).

Task 63: **DONE / STATIC+PACKAGE ACCEPTED** — V4-B added immutable `SettlementOpinionContext` + bounded `SettlementOpinionBias` (±15), current-anchor geographic Place lookup, neutral undefined contributions, and corrected the existing block-coordinate→chunk conversion. 1,658 production + 57 validation tests; no V4-C+ consumer or runtime (`task-63-report.md`).

Task 64: **DONE / STATIC+PACKAGE ACCEPTED** — V4-C added component/TTL-aware immutable ranking facts, structural POSITIVE_HINT→UNKNOWN lexicographic destination ranking, horizontal-only factual utility, bounded Opinion ordering, deterministic dimension+anchor tie-break, and bounded transient non-persistent route demotion input. 1,676 production + 57 validation tests; no V4-D+ consumer/runtime (`task-64-report.md`).

Task 65: **DONE / STATIC+PACKAGE ACCEPTED** — V4-D added canonical material+consumer demand identity, minimal transient `VillageIntent`, pure live legitimacy/admissibility revalidation, one-per-loaded-mob registry with unload/death/server-stop cleanup, and no path/market/SavedData authority. 1,692 production + 57 validation tests; no V4-E consumer/runtime (`task-65-report.md`).

Task 66: **DONE / STATIC+PACKAGE ACCEPTED** — V4-E added the production `VillageInteractionDirector`, exact-intent `CommuteDirective` binding, REQUIRED_TRADE as a second source for the existing COMMUTE executor, anchor-targeted chained legs, live resume/continuation revalidation, arrival handoff to V2, and terminal-only bounded transient route demotion. The closure addendum pins SPM v0.96 (`508EDA...1097`), explicitly audits all load-bearing V3/V4 host seams, classifies Douse as safety and Flint ignition as combat, and repairs the shelter provenance hook. No second Goal/navigation/market authority; v0.96 runtime remains unverified (`task-66-report.md`).

Task 67: **DONE / STATIC+PACKAGE ACCEPTED** — V4-F evaluates first-home promotion only after the real `SeekShelterGoal` sleep call reports `isSleeping`, resolves exactly one already-remembered settlement from the exact bed position, requires familiarity >=600 and no existing home, and delegates once to the canonical SavedData writer. Zero/multiple associations, failed sleep, 599 familiarity and every later sleep when home exists fail closed. 1,719 production + 57 validation tests; physical sleep→home behavior remains runtime-unverified (`task-67-report.md`).

No commits unless user requests. No Minecraft launches unless separately approved.
