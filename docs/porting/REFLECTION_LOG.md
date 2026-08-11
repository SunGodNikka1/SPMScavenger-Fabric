# Reflection log — SPM Scavenger

Appended per reflection. Newest last.

---

## 2026-08-07 — Scavenger v1.0 → v1.4, plus spmhostiles 2.2–2.3 and SPM skill updates

**Gate PREFL-1.** First reflection for this project; no prior report to continue from.

### Step 1 — Scope

| Field | Value |
| --- | --- |
| Project | Social Player Mobs: Scavenger (`spmscavenger`) — new compatibility addon, not a port |
| Host mod | Social Player Mobs `v0.86.0` / `4b80b5e849`, PolyForm Shield 1.0.0 |
| Target | Minecraft 1.21.1, Fabric (Loom 1.14.10, loader 0.16.14, FAPI 0.116.4+1.21.1) |
| Task | Give PlayerMobs behaviour outside a Dungeon Train: torches, shelter, gather + craft |
| Starting state | Nothing; SPM's world-changing goals are all train-gated |
| Final state | `spmscavenger-1.4.0.jar`, `6d9c89d1…`, builds clean, Gate 3.6 pass |
| Also in scope | `spmhostiles` 2.2.0 → 2.3.0 (two user-reported bugs); SPM skill + wiki digest |
| Verification | Build and packaged-jar inspection `CONFIRMED`. Runtime **partly** confirmed by user play: beds/sleeping work; gather/craft never observed up to 1.3 |
| Deferred | Furnace→charcoal, home stash chest, shelter building, wall torches, crop replanting (upstream) |

### Step 2 — Reconstruction

| Stage | Problem | Action | Result | Evidence | Confidence |
| --- | --- | --- | --- | --- | --- |
| Feasibility | Is "un-gate SPM's mining/crafting" real? | Traced `DigThroughGoal` → `TrainConfinement.digObstructingBlock` | **No.** It is a `default` returning `false`; the real code is in Dungeon Train. `CraftingLadder` does not exist; crafting is private inside `TrainRecoveryGoal` | Source grep; javadoc "No-op off a train" | `CONFIRMED` |
| Architecture | Avoid linking PolyForm code | `Class.forName` for the entity; backpack via **vanilla** `InventoryCarrier` | Zero SPM types in the addon | `PlayerMobEntity.java:104` imports the vanilla interface | `CONFIRMED` |
| Attachment | `registerGoals` is not extensible | `ENTITY_LOAD` + one `@Accessor("goalSelector")` on vanilla `Mob` | Works, and sees other mods' goals | SPM wiki; jar shows `field_6201` | `CONFIRMED` |
| v1.0 | Coal never collected | Left drops for `CollectFloorItemsGoal` | **Dead end**: coal is absent from `ItemPickupPolicy` | `VALUABLES` read from source | `CONFIRMED` |
| v1.0 fix | — | Take coal/charcoal/logs from `Block.getDrops`, drop the rest | Mirrors SPM's own `HarvestCropsGoal` | Source comparison | `CONFIRMED` |
| v1.1 | Shelter crude | Scored search + real bed sleeping | Beds work in play | User: "bed just works fine like normal" | `CONFIRMED` (runtime) |
| v1.2 | "Don't take shelter seriously" | Priority 4→2; distance −2→−1/block; floor 10→5; dusk lead-in; radius 16 | Not yet re-confirmed in play | Arithmetic against three real cases | `INFERRED` |
| v1.3 | Chain stalls without a pickaxe | Crafting table + wooden tools; `ToolBox` equips before mining | Compiles; unobserved | — | `INFERRED` |
| v1.4 | "Never see them chop a tree" | Priority 5/6→3; radius 10→20; gathering also runs for tool needs | Awaiting play | User's objective-readout report | `INFERRED` |

### Step 3 — Errors and recovery

| Error | Root cause | How found | Recovery | Reusable? |
| --- | --- | --- | --- | --- |
| **Goal priority above SPM's chores** (shelter 4, craft 5, gather 6) | Assumed "below the urgent things" was polite; `canBeReplacedBy` needs **strictly lower** | Two rounds of user reports, then disassembly | Shelter → 2, craft/gather → 3 | **Yes — highest-value lesson** |
| **Coal hand-off never accepted** | Assumed SPM's pickup goal collects anything mined | Tracing a user question ("how do they get torches?") | Keep wanted drops directly | **Yes** |
| **Owned tool ≠ held tool** | `getDestroySpeed` reads the held stack; gated on `getMainHandItem()` | Diagnosing why gathering produced nothing | `ToolBox.equipFor` before the first swing | **Yes** |
| **Cloth `isEdited()` not overridden** (`spmhostiles` 2.2.0) | Assumed the base class tracked value changes; it reports *errors* only | User: "save and exit button still being dark" | Mirror `BooleanListEntry` | **Yes → new Gate 5.6** |
| **`MobCategory.MONSTER` used as a hostility filter** | Assumed category implies hostility | User: only 2 of 9 Millager mobs listed | `DefaultAttributes.hasSupplier` | **Yes** |
| **SPM's own entity listed as a hostile** | Same wrong assumption, opposite direction | User noticed the PlayerMob toggle reading "On" | Excluded by id | Yes |
| **Claimed `finalizeSpawn` proves "no spawn gear"** | Probed for loot tables, concluded "empty-handed"; missed that egg `entity_data` merges *after* `finalizeSpawn` | User: "why do they spawn with Epic Knights armor?" | Corrected skill and reference | **Yes — AV-1 shape** |
| **Pitched "un-gate mining/crafting" as cheap** | Read goal names, not implementations | Checked before building on it | Corrected to the user before writing code | Yes |

Six of eight are self-inflicted assumption errors. Five were caught by **user runtime reports**, not
by build, code review or static analysis.

### Step 4 — Breakthroughs

| Breakthrough | Obstacle | Discovery | Technique | Why it matters | Confidence |
| --- | --- | --- | --- | --- | --- |
| Zero-linkage inventory access | PolyForm noncompete discourages linking | `PlayerMobEntity implements` **vanilla** `InventoryCarrier` | Cast to the vanilla interface | Full backpack read/write with no dependency on their code | `PROVEN` |
| Host readout as primary diagnostic | Behaviour "not happening", cause unknown | `ObjectiveReadout` names running goals; non-`DescribableGoal` fall back to a humanised class name | Read the label before reading your own code | Turned a vague report into a precise diagnosis in one step | `PROVEN` |
| Priority table as a design input | Injected goals silently never ran | `canBeReplacedBy` requires strictly-lower priority; SPM's chores sit at 3 | Choose preempt (<3) or interleave (=3) deliberately | The single largest source of "addon does nothing" | `PROVEN` |
| Mob liveness without `MobCategory` | Mod-specific categories hide whole rosters | Every `LivingEntity` type must register default attributes | `DefaultAttributes.hasSupplier` | Category-independent, no instance, no level | `PROVEN` |

### Step 5 — Patterns

1. **Injected-goal priority budget** — `AI_PATTERN`, `PROVEN`. Read the host's `registerGoals`,
   tabulate priorities, then decide preempt vs interleave. Failure indicator: the behaviour never
   appears in a state readout and is never observed.
2. **Host-diagnostic-first** — `DEBUGGING_PATTERN`, `PROVEN`. Before instrumenting your own code,
   find the host's readout or overlay. Complements Gate DBG-1 for behaviour that produces no log.
3. **Verify the hand-off, not the API** — `COMPATIBILITY_PATTERN`, `PROVEN`. When passing data to a
   host system, prove the host accepts *that input*. GVC-6 at a data boundary.
4. **Held-vs-owned capability** — `ENTITY_PATTERN`, `STRONGLY_SUPPORTED`. Capability checks that read
   the held stack need an explicit, conservative equip step.
5. **Custom Cloth entry contract** — `VALIDATION_PATTERN`, `STRONGLY_SUPPORTED` → Gate 5.6.
6. **Docs authoritative for intent, not API** — `WORKFLOW_PATTERN`, `PROVEN`. Already promoted in
   `wiki-digest.md` during the work.

### Step 6 — Lesson → instruction mapping

| Lesson | Existing instruction | Relationship | Action |
| --- | --- | --- | --- |
| Priority budget | SPM skill §6 | `NEW_CAPABILITY` | Added, plus a full reference |
| Host readout diagnostic | SPM skill §6; Gate DBG-1 | `STRENGTHENS_EXISTING` | Added to SPM skill. DBG-1 left unchanged — logs remain first for crashes |
| Verify the hand-off | GVC-6; SPM skill §6 | `STRENGTHENS_EXISTING` | Added to SPM skill as a data-boundary shape. GV skill not edited |
| Cloth `isEdited()` | validation matrix 5.x | `ADDS_VALIDATION_GATE` | **Gate 5.6**, narrowly scoped to custom entries |
| `MobCategory` ≠ hostility | SPM hostile recipe | `CLARIFIES_EXISTING` | New reference §6 |
| Held ≠ owned | — | `NEW_CAPABILITY` | New reference §5 |
| `finalizeSpawn` correction | `spawn-equipment-vs-scavenging.md` | `REPLACES_WEAKER_TECHNIQUE` | Corrected during the work |

### Step 8 — Rejected proposals

| Rejected proposal | Reason not promoted |
| --- | --- |
| "Three scanning goals are fine at 50+ mobs" | No profiler run. `UNVERIFIED`; Gate SPM-5 / AV-1 forbids promoting an estimate |
| Recommend implementing `DescribableGoal` by default | Requires compiling against PolyForm code — a dependency-posture change, the user's call (SPM-4) |
| "Always take drops directly rather than leaving them" | Overfits. Correct for coal; wrong where the host does accept the item, and Gate SPM-2 prefers reuse |
| Generalise "priority 3" as a constant for all host mods | Overfits to SPM's numbers. The *method* generalises; the number does not |
| Claim the shelter and gathering fixes work | Not yet observed in play. `INFERRED` until the readout shows them |

### Step 9 — Handoff

- **Objective:** behaviour addon giving PlayerMobs torches, shelter/sleep, and gather + craft.
- **Stable state:** `spmscavenger-1.4.0.jar` (`6d9c89d1…`). Beds `CONFIRMED` in play; everything else `INFERRED`.
- **Key files:** `SpmScavenger` (attachment + priorities), `PlayerMobs` (reflection + vanilla
  backpack), `ToolBox`, `ScavengerCrafting`, `PlankMap`, `ShelterScore`, `goal/*`,
  `mixin/MobGoalSelectorAccessor`.
- **Architecture:** zero SPM linkage; `ENTITY_LOAD` attachment; one vanilla accessor mixin; pure
  policy classes beside thin goals, matching SPM's own engineering model.
- **Failed approaches to avoid:** goal priority ≥ 4; leaving drops for SPM to collect; gating tools
  on the main hand; `MobCategory` as a hostility or liveness proxy; assuming `finalizeSpawn` is the
  only spawn-equipment path.
- **Commands:** `./gradlew build`. Gate 3.6:
  `javap -v -p <extracted>/com/noobk/spmscavenger/mixin/MobGoalSelectorAccessor.class | grep field_`
  must print `field_6201`.
- **Next action:** have the user watch the objective readout for `Gather resources` / `Craft torches`.
  If still absent, trace that goal's `canUse` preconditions — do not adjust priority again blind.
- **Constraints:** no Minecraft launch without approval; commits only on request; SPM is PolyForm
  Shield 1.0.0 — do not vendor or ship their code.

### Shared-instruction changes made by this reflection

1. `.agents/skills/minecraft-mod-porting/references/validation-matrix.md` — **new Gate 5.6**.
2. `.agents/skills/social-player-mobs-integration/references/adding-goals-to-playermobs.md` — new.
3. `.agents/skills/social-player-mobs-integration/SKILL.md` — §6 additions and cross-reference.

No commits or pushes were made.

---

## 2026-08-08 — Scavenger 1.7.3 → 1.7.4 stuck-tree path recovery

**Gate PREFL-1.** Incremental reflection only: the gathering target/navigation repair completed
after the prior v1.0–v1.4 reflection. No whole-project parity or dependency audit was performed.

### Step 1 — Scope

| Field | Value |
| --- | --- |
| Project / mod | Social Player Mobs: Scavenger (`spmscavenger`) |
| Host reference | Social Player Mobs `v0.86.0` / `4b80b5e849` |
| Target | Minecraft 1.21.1 Fabric; addon version 1.7.4 |
| Task | Repair PlayerMobs that remove one log, remain on `Gather resources`, and stare at the tree; use path-aware targets plus bounded leaf recovery |
| Starting state | 1.7.3 atomic felling worked statically, but acquisition treated every log as an independent Euclidean target and navigated to the solid block |
| Final state | 1.7.4 selects base/logical targets, paths to interaction positions, retains one recoverable partial path, clears tightly bounded direct leaves after measured stall, and backs off the logical tree |
| Systems changed | Gather AI target selection/navigation/recovery; config + Cloth screen; pure policy tests; README and decision evidence |
| Claimed stability | Reported reproduction `CONFIRMED` by user runtime observation; negative edge matrix and performance remain `UNVERIFIED` |
| Verification | `gradlew clean build --stacktrace`; 11/11 tests; packaged JAR inspection; user: “Worked” |
| Artifact | `build/libs/spmscavenger-1.7.4.jar`; SHA-256 `F78F8E8698195D182A4604ADB05264F9F285A28F1E250E82139A1AFFF6DE1916` |

### Step 2 — Reconstruction

| Stage | Problem | Action | Result | Evidence | Confidence |
| --- | --- | --- | --- | --- | --- |
| Runtime triage | One log removed, objective remains active, mob stares | Logs/crash reports searched first; no Scavenger exception found | Classified runtime logic/navigation rather than a logged crash | Three `NOT FOUND` probes recorded in `DECISIONS.md`; installed 1.7.3 active in `latest.log` | `CONFIRMED` classification; exact runtime target was `INFERRED` |
| Static cause | Every log was independently ranked by distance; no path/standing test; `moveTo` result ignored; one-coordinate backoff | Traced `findBlock`, `start`, timeout, and vanilla 1.21.1 `PathNavigation`/`Path` APIs | A falsifiable target/path defect replaced the initial foliage hypothesis | `GatherResourcesGoal.java`; Mojang-mapped navigation source | `CONFIRMED` |
| Architecture | Need foliage recovery without a general digging AI | Chose base-only logical target + standing-position paths + partial-path fallback + measured-stall leaf recovery | Destruction is optional, direct, local, delayed, capped, and followed by re-path | `GatherResourcesGoal`, `GatherApproachPolicy`, config screen | `CONFIRMED` implementation |
| Performance boundary | Protection previously consumed 32.10% of server tick time; new pathfinding could regress it | Retained 24-candidate shortlist, limited native path probes to 3/scan, re-path only after recovery or at 20-tick completed-path intervals | Work is statically bounded; multi-mob impact not measured | constants/control flow; prior Spark result in README | Boundedness `CONFIRMED`; performance `UNVERIFIED` |
| Failure domain | Exact-log cooldown can cycle through a tree; 2×2 trunks have several bases | Backoff key changed to logical/canonical base; adjacent bottom logs share one bounded key | Same failed tree cannot immediately re-enter as a different raw hit | `treeFailureKey`, eight-key/200-tick limits | `CONFIRMED` static |
| Validation | Compile cannot prove navigation/destruction | Added six focused approach-policy tests; clean build/package audit; handed runtime matrix to user | 11 tests passed; reported reproduction worked in play | XML reports, JAR hash, user message | Core reproduction `CONFIRMED`; edge matrix `UNVERIFIED` |

### Step 3 — Errors and recovery

| Error | Root cause | How found | Recovery | Reusable? |
| --- | --- | --- | --- | --- |
| Raw resource block used as navigation destination | Conflated “thing to operate” with “place the mob can stand” | Static trace after user symptom | Generate action-reach/body-clearance positions and use native path reachability | Yes — `AI_PATTERN` |
| Timeout scoped to one log | Failure identity matched scan hits, not the task/resource | Reviewed retry behavior | Back off the logical tree/base; canonicalise adjacent 2×2 bottoms | Yes — `AI_PATTERN` |
| First repair draft admitted only already-clear standing cells | A leaf-wrapped base then had no destination, so recovery could never start | Self-review before final build | Permit leaf-filled feet/head cells as recovery destinations only for tree targets with recovery enabled; hard collision remains rejected | Yes — conditional |
| First backoff draft used one bottom coordinate | Missed multi-column trunks | Self-review before final build | Bounded one-block canonical key for adjacent bottoms | Yes — resource-dependent |
| “Worked” could be overextended to every guard | One successful runtime scenario does not cover ore/build/config/combat/cap/performance negatives | AV-1 reflection check | Confirm only the reported reproduction; retain explicit unverified matrix | Yes — validation discipline |

### Step 4 — Breakthroughs

| Breakthrough | Obstacle | Discovery | Final technique | Why it matters | Evidence | Reuse scope |
| --- | --- | --- | --- | --- | --- | --- |
| Logical target / interaction position split | A valid resource can be physically unreachable from the resource coordinate | Vanilla navigation accepts a set of candidate destinations and exposes `canReach()` | Normalize the task, generate reachable action positions, then navigate | Prevents silent stare/stall behavior without custom pathfinding | User runtime success + source/tests | `STRONGLY_SUPPORTED`, generic NPC resource AI |
| Recovery-only partial path | Strict reachability skips dense natural trees; general digging is unsafe | A partial path is useful only when paired with a bounded local recovery action | Prefer complete paths; retain one partial fallback; recover after measured stall; re-path | Preserves safe default while solving natural soft obstruction | User runtime success; guarded implementation | `STRONGLY_SUPPORTED`, soft-obstruction tasks |
| Failure keys match logical resources | Coordinate cooldown does not stop sibling components being selected | Retry scope must match the unit of work | Base/canonical-tree key with bounded expiry map | Prevents target thrashing and repeated expensive failure | Static proof; runtime core works | `STRONGLY_SUPPORTED` |

The **leaf type and numeric thresholds are `PROJECT_SPECIFIC`**. They are not promoted as universal
defaults.

### Step 5 — Reusable patterns

1. **Logical target → interaction position → path result** — `AI_PATTERN`,
   `STRONGLY_SUPPORTED`. Use for block/resource/machine tasks where the target itself is not a valid
   standing cell. Do not add it where the existing goal already exposes a correct reachable
   interaction point. Validate a reachable alternative and an obstructed target at runtime.
2. **Bounded soft-obstacle recovery** — `AI_PATTERN` + `PERFORMANCE_PATTERN`,
   `STRONGLY_SUPPORTED`. Preconditions: explicit destructive permission, allowlisted soft block,
   locality/protection checks, measurable progress, hard cap, retry/backoff. Do not use as general
   digging or against protected/hard blocks. Failure signals: early damage, repeated clearing,
   target-type leakage, or per-tick pathfinding.
3. **Failure-domain backoff** — `AI_PATTERN`, `STRONGLY_SUPPORTED`. Back off the logical work unit,
   not whichever child coordinate happened to fail. The grouping rule must be bounded and
   target-derived; a world flood-fill is not acceptable.
4. **Pure recovery policy + thin Minecraft action** — `VALIDATION_PATTERN`, `PROVEN` in project.
   Unit-test threshold/cap/guards independently, then require runtime navigation/destruction proof.

### Step 6 — Lesson → instruction mapping

| New lesson | Existing instruction | Relationship | Evidence | Action |
| --- | --- | --- | --- | --- |
| Separate resource and standing targets | SPM skill Gate SPM-5 mentions path failure but not target construction | `NEW_CAPABILITY` | Static defect + user runtime success | Added conditional guidance and canonical reference section |
| Bounded measured-stall recovery | SPM-5 already requires timeout/retry/backoff | `STRENGTHENS_EXISTING` | User runtime success; unit guards | Expanded existing recovery guidance, did not create a new global gate |
| Backoff logical resource | SPM-5 says backoff without defining failure identity | `CLARIFIES_EXISTING` | Exact-log cycling defect | Added to SPM skill/reference checklist |
| Exact leaf/20-tick/3-leaf values | No shared instruction | `PROJECT_SPECIFIC_ONLY` | One project/runtime scenario | Kept as example; explicitly not defaults |
| Multi-mob performance claim | SPM-5 requires measurement | `INSUFFICIENT_EVIDENCE` | No comparable profiler run | No performance rule change |

All promotion requirements pass for the conditional procedure: real observed problem, static and
runtime evidence, focused tests, cross-task applicability, named prerequisites/limits, no conflict
with SPM-2/SPM-5, explicit verification, and improved reliability. The result is
`STRONGLY_SUPPORTED`, not a mandatory universal leaf-breaking gate.

### Step 8 — Rejected proposals

| Rejected proposal | Reason not promoted |
| --- | --- |
| “NPC gathering AI should always clear leaves” | Overfits one tree task and creates griefing risk; only the bounded recovery decision process generalises |
| Universal constants: 3 path probes, 20 ticks, 3 leaves, 200-tick cooldown | Project-specific tuning with no cross-mod or performance comparison |
| New global validation-matrix gate | Existing Gate SPM-5 already owns path failure/recovery; strengthening its procedure avoids duplicate governance |
| Claim performance improved or equal to 1.7.3 | No post-change Spark/profile measurements at representative mob counts |
| Claim every negative guard works in game | The user confirmed the reported reproduction, not the deliberate ore/build/config/combat/cap matrix |

### Step 9 — Knowledge-transfer handoff

- **Completed objective:** PlayerMobs no longer select arbitrary hidden logs and stall indefinitely;
  the reported tree-gathering reproduction works in the user's game.
- **Stable artifact:** `build/libs/spmscavenger-1.7.4.jar`, SHA-256
  `F78F8E8698195D182A4604ADB05264F9F285A28F1E250E82139A1AFFF6DE1916`.
- **Key files:** `goal/GatherResourcesGoal.java`, `goal/GatherApproachPolicy.java`,
  `goal/GatherApproachPolicyTest.java`, `ScavengerConfig.java`, `client/ScavengerConfigScreen.java`.
- **Architecture:** base/logical target; action-reach standing positions; max-three native path
  probes; reachable-first/one-partial fallback; measured stall; one local leaf then re-path;
  capped/config/game-rule/protection/combat guards; bounded logical-target backoff.
- **Avoid:** navigating to the solid resource coordinate; Euclidean distance as reachability proof;
  per-tick pathfinding; raw-coordinate cooldown; general foliage digging; claiming all edge cases
  from the one successful reproduction.
- **Proven command:** `.\gradlew.bat clean build --stacktrace` — 11 tests, successful remapped JAR.
- **Remaining validation:** README 1.7.4 negative matrix and Spark runs at representative mob counts.
- **Highest-priority next action:** exercise protected-build, coal, setting-off,
  `mobGriefing=false`, combat, exact-cap and 2×2 timeout cases; profile only after functional edges
  pass.
- **Constraints:** ask before any Minecraft launch; no commit/push was requested; do not vendor SPM
  PolyForm code.

### Shared-instruction changes made by this reflection

1. `.agents/skills/social-player-mobs-integration/SKILL.md` — strengthened Gate SPM-5 with the
   logical-target/interaction-position and bounded-recovery procedure.
2. `.agents/skills/social-player-mobs-integration/references/adding-goals-to-playermobs.md` — added
   the canonical evidence, constraints, procedure, validation matrix and checklist items.

Project evidence also updated in `README.md` and `docs/porting/DECISIONS.md`. No wrapper workflow
was duplicated, no unrelated rule was changed, and no commit, push or Minecraft launch occurred.

---

## 2026-08-08 — Scavenger 1.8.1 → 1.8.2 objective visibility and exploration replanning

**Gate PREFL-1.** Incremental reflection only: the two runtime reports and repairs following the
1.8.0 exploration implementation. This is not a whole-project parity or performance audit.

### Step 1 — Scope

| Field | Value |
| --- | --- |
| Project / mod | Social Player Mobs: Scavenger (`spmscavenger`) |
| Host reference | Social Player Mobs `v0.86.0` / `4b80b5e849` |
| Target | Minecraft 1.21.1 Fabric; addon version 1.8.2 |
| Task | Remove false `Exploration activity` / `Antics` objectives and keep the semantic `Exploring` state through bounded path replanning |
| Starting state | 1.8.0 attached two always-running flagless goals that SPM humanised; retryable path failure stopped `Exploring` for 20 ticks; one `navigation.isDone()` could fail immediately |
| Final state | Both background goals use the pinned host's cosmetic classification; replan wait stays inside `Exploring`; path-done has 20-tick grace; final outcomes are logged |
| Systems changed | Goal/readout compatibility, exploration retry state, pure failure policy, diagnostics, tests and port docs |
| Claimed stability | Build/static behavior `CONFIRMED`; 1.8.2 runtime behavior `UNVERIFIED` |
| Verification | `gradlew.bat clean test build`; 30 tests; packaged 1.8.2 metadata/classes/hash |
| Artifact | `build/libs/spmscavenger-1.8.2.jar`; SHA-256 `092596575D4E0C2F589E108CD433EB7CF1DD48AE554F2DC5DD5367CA46FC7340` |

### Step 2 — Reconstruction

| Stage | Problem | Action | Result | Evidence | Confidence |
| --- | --- | --- | --- | --- | --- |
| Runtime triage | User saw simultaneous real and implementation labels | Read newest log first; pinned active 1.8.0/1.8.1; inspected SPM `ObjectiveReadout` | Identified humanised fallback, not simultaneous MOVE execution | `latest.log`; `ObjectiveReadout.java`; goal flags | Root cause `CONFIRMED` |
| First repair | `ExplorationActivityGoal` always ran and appeared | Classified it under SPM 0.86.0 cosmetic goal filtering; disabled inherited behavior | 1.8.1 hid the observer statically | visibility test, `javap`, clean build | Static `CONFIRMED`; runtime result `INFERRED` |
| Second report | User continuously saw `Antics` | Searched all goal lifecycle/flags; active config had `mimicry=true` | Found same class of failure in another always-running goal | `AnticsGoal.canUse`; config; SPM fallback | `CONFIRMED` |
| Replan repair | `Exploring` briefly became idle/wander after seconds | Kept retry wait within the running goal, preserved route state, added path-done grace | Semantic state no longer intentionally disappears during internal retry | state transitions + policy tests | Static `CONFIRMED`; runtime `UNVERIFIED` |
| Diagnostics | Exact final stop branch could not be determined from the log | Added completion/abandonment reason and counters | Next reproduction distinguishes path/frontier/stale completion | `ExploringGoal.completeExpedition/abandon` | Static `CONFIRMED` |
| Packaging | Needed installable bugfix artifact | Bumped to 1.8.2; clean test/build; inspected JAR | 30 tests passed; metadata 1.8.2 | Gradle/XML/JAR/hash output | `CONFIRMED` |

### Step 3 — Errors and recovery

| Error | Root cause | How found | Recovery | Reusable? |
| --- | --- | --- | --- | --- |
| Background observer appeared as a real objective | Assumed flagless meant invisible; did not inspect host presentation | User runtime report + host source | Explicit readout policy and negative display test | Yes — `COMPATIBILITY_PATTERN` |
| First repair missed `AnticsGoal` | Fixed one class rather than searching every goal with the same lifecycle signature | Second user runtime report | Search all always-running/flagless attached goals after one leak | Yes — `DEBUGGING_PATTERN` |
| Internal retry appeared as idle/wander | Navigation state transition was allowed to redefine semantic activity | Source trace of `yieldForRetry` and `canContinueToUse` | Keep expedition intent active; replace only disposable path state | Conditional `AI_PATTERN` |
| One path-done observation failed a stage | Treated navigation state as definitive task state | Source trace after short-runtime report | Add bounded arrival grace while retaining stall/retry caps | `PROJECT_SPECIFIC` pending runtime |
| First 1.8.1 compile lost the `Goal` import | Mechanical edit removed a type still used in goal iteration | `compileJava` | Restore import; rerun clean pipeline | Routine, recorded but not promoted |
| README insertion initially landed outside its intended section | Patch context was too weak | Immediate post-edit inspection | Repatch with anchored surrounding text | Workflow reminder; existing inspection rules suffice |

### Step 4 — Breakthroughs

| Breakthrough | Obstacle | Discovery | Final technique | Why it matters | Evidence | Reuse scope |
| --- | --- | --- | --- | --- | --- | --- |
| Objective visibility is an integration contract | Background implementation detail became player-facing behavior | SPM displays every running non-filtered goal, including humanised addon class names | Classify every goal as foreground/background and test both positive/negative display | Prevents false concurrency and misleading diagnostics | Two user reports + pinned host source | `STRONGLY_SUPPORTED`, SPM compatibility |
| Expedition intent survives path replacement | A one-second internal retry looked like abandonment | Goal running state and path object were incorrectly coupled for presentation | Keep semantic expedition active while disposing/replanning navigation | Produces stable player-facing state without preserving stale paths | Static transition/tests only | `EXPERIMENTAL` until runtime |
| Final-reason logging closes an evidence gap | Runtime symptom could map to several safe/failure branches | No existing branch emitted evidence | Log completion or final path/frontier/stale reason with counters | Makes next diagnosis falsifiable | Packaged code; no runtime line yet | `STRONGLY_SUPPORTED` diagnostic pattern |

### Step 5 — Reusable patterns

1. **Foreground/background goal visibility policy** — `COMPATIBILITY_PATTERN`,
   `STRONGLY_SUPPORTED`. Use when a host derives UI/debug state from running goals. Inspect the
   pinned host implementation, classify each addon goal, and validate labels that must appear and
   implementation labels that must not. Do not assume flagless means hidden. The exact filtered
   subclass workaround is SPM-0.86.0-specific and not a universal API.
2. **Same-signature sibling search after a runtime failure** — `DEBUGGING_PATTERN`,
   `STRONGLY_SUPPORTED`. After one always-running/flagless goal leaks, probe every attached goal for
   the same lifecycle signature. This would have caught Antics during 1.8.1.
3. **Intent state separate from navigation state** — `AI_PATTERN`, `EXPERIMENTAL`. Preserve route,
   heading and waypoint index; regenerate `Path`. Keep semantic activity active through internal
   replans only when no real higher-priority action owns the mob. Runtime validation is still needed.
4. **Bounded end-reason diagnostics** — `DEBUGGING_PATTERN`, `STRONGLY_SUPPORTED`. Emit one concise
   line at completion/final abandonment, not per-tick or per-probe spam.

### Step 6 — Lesson → instruction mapping

| New lesson | Existing instruction | Relationship | Evidence | Action |
| --- | --- | --- | --- | --- |
| Background labels can pollute readout | SPM skill/readout §3 only covered missing foreground goals | `CLARIFIES_EXISTING` | Two user-visible failures + host source | Updated SPM skill and canonical goal reference |
| Search all lifecycle siblings | Existing no-cargo-cult/evidence discipline | `STRENGTHENS_EXISTING` | First partial repair caused second report | Added narrowly to SPM goal reference procedure |
| Keep semantic state through replan | No shared instruction | `INSUFFICIENT_EVIDENCE` | Static tests only | Project report only; no shared rule |
| Twenty-tick path-done grace | No shared instruction | `PROJECT_SPECIFIC_ONLY` | No runtime proof or comparative tuning | Kept in project docs/tests |
| Final-reason logging | DBG-1 favors log evidence | `STRENGTHENS_EXISTING` | Prior log could not distinguish branches | Project implementation; no duplicate global gate |

### Step 8 — Rejected proposals

| Rejected proposal | Reason not promoted |
| --- | --- |
| Make every background goal subclass `RandomLookAroundGoal` | SPM-version-specific workaround and runtime-unverified in 1.8.2; use a supported host contract when available |
| Weaken entity-ticking frontier checks | No evidence the safety boundary caused the observed route end |
| Raise waypoint/failure caps | Could increase pathfinding cost and retry loops; no runtime reason evidence yet |
| Treat `Exploring` as uninterrupted through real work | Combat, loot, farming and orders must remain legitimate higher-priority foreground objectives |
| Claim 1.8.2 fixed runtime exploration | Clean build and policy tests are not runtime proof (AV-1) |

### Step 9 — Knowledge-transfer handoff

- **Completed objective:** built 1.8.2 to hide background implementation labels and retain the
  `Exploring` objective across internal path replans.
- **Current artifact:** `build/libs/spmscavenger-1.8.2.jar`, SHA-256
  `092596575D4E0C2F589E108CD433EB7CF1DD48AE554F2DC5DD5367CA46FC7340`.
- **Key files:** `goal/AnticsGoal.java`, `goal/ExplorationActivityGoal.java`,
  `goal/ExploringGoal.java`, `goal/ExplorationPolicy.java`, visibility/policy tests.
- **Architecture:** persistent expedition intent; disposable navigation path; tracked local-wander
  and no-work activation; host-filtered background decorators; bounded replanning/frontier checks.
- **Avoid:** assuming flagless goals are hidden; repairing only the reported class; preserving old
  `Path` across interruption; weakening simulation safety without reason evidence.
- **Proven command:** `.\gradlew.bat clean test build` — 30 tests, successful remapped 1.8.2 JAR.
- **Runtime-unverified:** `Antics` suppression, continuous replan readout, path-done grace, final
  outcome log lines and complete expedition behavior.
- **Highest-priority next action:** install 1.8.2, reproduce one successful expedition and one
  blocked/frontier case, then inspect `latest.log` for the new final reason before tuning limits.
- **Constraints:** explicit approval is required before any Minecraft launch; no commit or push was
  requested; do not copy SPM's PolyForm implementation into the addon.

### Shared-instruction changes made by this reflection

1. `.agents/skills/social-player-mobs-integration/SKILL.md` — added foreground/background readout
   policy and the deliberate-filter exception.
2. `.agents/skills/social-player-mobs-integration/references/adding-goals-to-playermobs.md` — added
   canonical procedure, sibling search, checklist and SPM-version-specific workaround boundary.

No gate was weakened, no wrapper duplicated the workflow, and no commit, push or Minecraft launch
occurred.

## 2026-08-08 — Scavenger 1.8.2 → 1.8.5 exploration repair, companions, interest scoring, Gate SPM-0

Continues from the 1.8.1 → 1.8.2 reflection above. Only work since that report is covered.

### Step 1 — Scope

| Field | Value |
|---|---|
| Project | SPM Scavenger — Fabric 1.21.1 addon (not a port) |
| Host mod | `playermob-fabric-0.86.0+1.21.1` (PolyForm Shield 1.0.0) |
| Task | Diagnose "mobs stand still while the readout says `Exploring`", repair it, then three user-driven design cycles, then a governance change |
| Starting state | 1.8.2 — exploration shipped, runtime `UNVERIFIED` |
| Final state (this agent) | 1.8.5 — hop-limited paths, travelling companions, route interest scoring |
| Systems changed | Exploration goal and policy, host-feeling reads, config + config screen, five shared-instruction surfaces plus AGENTS.md |
| Verification | `gradlew.bat clean build` green at each step; 30 → 44 tests, zero failures |
| **Scope caveat** | The repository moved to **1.9.1** (`EnvironmentalEscapeGoal`, `MiningPolicy`, "real-tool timed environmental mining") in a **parallel session this agent did not run**. That work is deliberately **excluded** — there is no evidence trail for it here and it is not this reflection's unit of work. The final artifact built was `spmscavenger-1.9.1.jar` (SHA-256 `738130B4…44B8E2`, 50 tests) because this agent's last edits landed on top of it. |
| Deferred / unverified | **Every runtime claim.** No session log exists for 1.8.3, 1.8.4 or 1.8.5. |

### Step 2 — Reconstruction

| Stage | Problem | Action | Result | Evidence | Confidence |
|---|---|---|---|---|---|
| Diagnosis | "Standing while Exploring" | DBG-1: located the live instance and searched `latest.log` before reading code | 140 `exploration ended`, **0 completed**; 136 `PATH_FAILURE` | `D:\Minecraft\Instances\Fabulously Optimized\logs\latest.log` | `CONFIRMED` |
| Root cause | Why every path failed | Disassembled mapped `PathFinder`/`PathNavigation` and the SPM jar | `maxRange` = `FOLLOW_RANGE` = **32**; stages were 24–48 blocks, so most were geometrically unreachable | `PathFinder.findPath` offset 210; `PlayerMobEntity.createAttributes` = 32.0 | `CONFIRMED` |
| Second cause | Why *standing*, not wandering | Read 1.8.2's own change | `yieldForRetry` kept the goal running and holding `Flag.MOVE` for the whole replan wait | `ExploringGoal.canUse`, `WrappedGoal.canBeReplacedBy` | `CONFIRMED` |
| Repair (1.8.3) | — | Hop-limited requests, released `MOVE` on failure, elevation-ordered landings, `hops=` diagnostics | Build green, 34 tests | `gradlew clean build` | build `CONFIRMED`, behaviour `UNVERIFIED` |
| Companions (1.8.4) | "Why never two together?" | Disassembled the feeling economy | `FollowLovedOneGoal` is priority **2** and accepts PlayerMobs; the gate is `feelingToward >= 7.0` from a 5.0 start, and the one travel bond routes to `afterCarriageAdvance`, which pays **only when a carriage index changes** — dead off a Dungeon Train | `FeelingLedger.travel` → `FeelingRecord.afterCarriageAdvance` | `CONFIRMED` |
| Interest (1.8.5) | "Destinations are nothing" | Audited every sensing radius in both mods | Nothing looks past 24 blocks, most 8–12; exploration is the only thing that moves that bubble, and it moved it randomly | SPM `registerGoals` constructor args; this mod's config | `CONFIRMED` |
| Governance | "Compatibility first" | Added Gate SPM-0 across 8 surfaces | Precedence order, hardcoding ladder, constants rule, degrade-never-break | grep count per surface | `CONFIRMED` |

### Step 3 — Errors and recovery (self-inflicted included, unminimised)

| Error | Root cause | How found | Recovery | Reusable? |
|---|---|---|---|---|
| **Recommended raising `followRange` via the 3-arg `createPath`** as fix #1 | Proposed a remedy before reading `maxVisitedNodes`, which is fixed at construction to `FOLLOW_RANGE * 16` and does not grow; region snapshot grows cubically | Continued disassembly *before* implementing | Rejected it in the design table and implemented hop-limiting instead | **Yes** — AV-1 applies to recommendations, not only to claims |
| **Published "~20 greeting crouches"** | Read `CROUCH_STEP` but not `kindnessScale` (`1.0 + friendliness*0.1`, default 1.5) — real figure ~14 | Only when the user asked a follow-up question | Corrected with the full table | **Yes** — an arithmetic claim from a partially-read call chain is `INFERRED`, not `CONFIRMED` |
| **Argued POI/structure targeting would duplicate SPM's discovery** | Asserted a Gate SPM-2 conclusion without auditing SPM's actual radii; its discovery is 8–12 blocks reactive and it has **no** long-range target selection | User's empirical report that destinations were empty | Reversed publicly, recorded the reversal in `DECISIONS.md` (1.8.5) | **Yes** — anti-duplication requires measuring the other system's *scope*, not naming it |
| **Hardcoded another mod's constant** (`NEUTRAL_FEELING = 5.0f`) | Copied `FeelingLedger.DEFAULT` by value | Applying the Gate SPM-0 constants rule written minutes earlier to my own code | Now read reflectively with a pinned, warn-once fallback | **Yes** — write the rule, then run it against your own diff |
| **Assumed a wrapper was a junction** | `.claude/skills/minecraft-mod-porting` is a junction, so assumed the SPM one was too; it is an independent wrapper and stayed stale | Verification sweep showed `grep -c SPM-0` = **0** | Updated it; promoted counting into `skill-creator` | **Yes** |
| **Version bump silently no-opped** | `sed` targeted `1.8.5`; the file already said `1.9.1` from the parallel session | Expected artifact name did not exist | Reported the discrepancy instead of restating the intended version | **Yes** — verify the artifact produced, not the version you believe you set |

### Step 4 — Breakthroughs

| Breakthrough | Obstacle | Discovery | Technique | Why it matters | Evidence | Label |
|---|---|---|---|---|---|---|
| Terminal-branch logging paid for itself | 1.8.2 shipped runtime-unverified | Its `reason=` + counter line made the 1.8.3 diagnosis a five-minute log read | Log the terminating branch **and** its counters for any bounded state machine | Converted a guess into arithmetic | 140 log lines | `PROVEN` |
| Counter-signature reading | Log said failure, not *where* | `waypoint=2/N, waypointFailures=3, expeditionFailures=6` uniquely decodes to "never reached any waypoint" by replaying the failure policy | Reverse the policy against the counters | Distinguishes "moved and ran out" from "never moved" | `ExplorationPolicy.failureAction` | `STRONGLY_SUPPORTED` |
| `FOLLOW_RANGE` caps all long-range navigation | Silent, error-free failure | A/`maxRange` cutoff at 32 for PlayerMobs | Hop-limited routing | Applies to **any** mod writing a "go far away" goal | bytecode + log | constraint `PROVEN`, remedy `STRONGLY_SUPPORTED` |
| A dead accrual path found by disassembly | "Friends never form" | `afterCarriageAdvance` pays only on a *changed* carriage index; off-train it is constant | Disassemble the accrual path, don't infer from the consuming goal | Explains an entire absent behaviour in another mod | `FeelingRecord.afterCarriageAdvance` | `PROVEN` |
| Sensory-radius audit as a design tool | "Destinations are empty" | Tabulating every radius showed a 8–24 block world | Audit the mod set's sensing radii before designing where mobs go | Reframed the whole feature | both jars | `STRONGLY_SUPPORTED` |

### Step 5 — Patterns

- **Hop-limited long-range navigation** — `AI_PATTERN`, `STRONGLY_SUPPORTED`. Promoted to
  `adding-goals-to-playermobs.md` §9 with the rejected `followRange` alternative recorded.
- **Terminal-branch + counter logging** — `DEBUGGING_PATTERN`, `PROVEN`. Added to the §8 checklist.
- **Release the movement flag rather than preserve a label** — `AI_PATTERN`,
  `STRONGLY_SUPPORTED`. Promoted to §10 *as a stated trade*, with 1.8.2's opposite decision
  preserved, not deleted.
- **Read foreign constants at runtime; pin and fail closed if copied** — `COMPATIBILITY_PATTERN`,
  `STRONGLY_SUPPORTED`. Now Gate SPM-0.
- **Encode a design bound as a test, not a comment** — `VALIDATION_PATTERN`, `STRONGLY_SUPPORTED`.
  `ROUTE_CAP < 100` is asserted so the anti-repetition guarantee cannot be tuned away silently.
- **Verify instruction propagation by counting** — `WORKFLOW_PATTERN`, `PROVEN` (it caught a real
  miss). Promoted to `skill-creator`.

### Step 6 — Lesson → instruction mapping

| Lesson | Instruction | Relationship | Action |
|---|---|---|---|
| `FOLLOW_RANGE` navigation ceiling | `adding-goals-to-playermobs.md` | `NEW_CAPABILITY` | Added §9 |
| Movement flag vs label | same, and 1.8.2's `DECISIONS.md` | `ADDS_EXCEPTION` / `CONFLICTS_WITH_EXISTING` | Added §10; older reasoning preserved and scoped to short waits |
| Terminal-branch logging | §8 checklist | `STRENGTHENS_EXISTING` | Checklist item |
| Constants belong to their owner | Gate SPM-0 | `NEW_CAPABILITY` | Added (user-directed, this session) |
| Anti-duplication needs a scope measurement | Gate SPM-2 | `CLARIFIES_EXISTING` | **Not edited** — see rejected list |
| Propagation by counting | `skill-creator` Validation | `STRENGTHENS_EXISTING` | Added |

### Step 8 — Rejected proposals

| Rejected | Reason |
|---|---|
| Raise `followRange` on `createPath` to allow whole-stage paths | `maxVisitedNodes` fixed at construction; region grows cubically. More cost, same failure |
| Write to SPM's `FeelingLedger` so co-travel builds real bonds | Private final field, no public mutator; would make this addon a silent author of another mod's social economy. Raised with the user as a separate decision instead |
| Proposed interest magnitudes (spawner +100, chest +25) | Would exactly cancel the −100 recent-destination penalty and send mobs back to the same chunk repeatedly. Ordering kept, scale capped at 40/60, bound asserted by test |
| A `routeRisk` scoring term | No risk signal defined; inventing one would be guessing at intent |
| Rewriting Gate SPM-2 from the POI reversal | One reversal on one mod pair is `PROJECT_SPECIFIC`; recorded in `DECISIONS.md` (1.8.5) rather than promoted |
| Marking companions or interest scoring as working | No runtime evidence. Build success is not runtime correctness |

### Step 9 — Handoff

- **Objective:** make exploration actually move mobs somewhere worth going.
- **Stable state:** builds clean, 50 tests pass, artifact `spmscavenger-1.9.1.jar`.
- **Key files:** `goal/ExploringGoal.java`, `goal/ExplorationPolicy.java`, `goal/ExplorationInterest.java`,
  `goal/ChunkInterest.java`, `PlayerMobs.java`.
- **Architecture:** intent (heading + waypoints) is durable; the `Path` is disposable; long stages
  are walked as ≤16-block hops; route choice = novelty + capped interest − repetition penalties;
  companions receive a heading, never a follow order; all host state is **read**, never written.
- **Failed approaches to avoid:** raising `followRange`; holding `MOVE` through a visible wait;
  count-weighted interest; penalising empty chunks; any chunk accessor that can load or generate.
- **Proven command:** `.\gradlew.bat clean build` — 50 tests, remapped jar.
- **Highest-priority next action:** **run one session and `grep -c "exploration completed"
  logs/latest.log`.** Three of this agent's builds are stacked without a single runtime datapoint,
  and interest scoring cannot be evaluated until hop-limiting is confirmed to move mobs at all.
- **Open risk:** the companion gate needs *mutual* above-neutral regard, but a crouch credits only
  the observer's ledger. If `companions=` never appears while mobs visibly greet, loosen
  `ExplorationPolicy.travelsTogether` first.
- **Constraints:** no Minecraft launch without explicit approval; no commit or push was requested or
  made; SPM is PolyForm Shield — read, never redistribute.

### Shared-instruction changes made by this reflection

1. `.agents/skills/social-player-mobs-integration/references/adding-goals-to-playermobs.md` — new §9
   (`FOLLOW_RANGE` ceiling, rejected alternative, hop remedy), new §10 (movement flag vs label, as a
   stated trade), three checklist items.
2. `.agents/skills/skill-creator/SKILL.md` — propagation verified by counting; junction assumptions
   named as a known failure.

Gate SPM-0 itself was added earlier in this session at the user's direction, not by this reflection.
No gate was weakened, no workflow text was duplicated into a wrapper, and no commit, push or
Minecraft launch occurred.

## 2026-08-08 — EnvironmentalEscapeGoal 1.9.0 → real-tool timed mining 1.9.1

Continues from the exploration reflection above, which explicitly excluded this parallel work. This
section covers only environmental escape, obstruction mining, equipment selection/restoration, and
their validation/documentation.

### Step 1 — Scope resolution

| Field | Value |
|---|---|
| Project / mod | Social Player Mobs: Scavenger addon |
| Host baseline | SPM v0.86.0, commit `4b80b5e849ccabd69e7c9c2f44dc25f7233c7796`, MC 1.21.1 Fabric |
| Task | Add Powder Snow/suffocation recovery, then replace generic deletion with real-tool timed mining |
| Starting state | 1.8.5 loaded successfully but had no environmental escape goal |
| Final state | 1.9.1 source contains priority-0 `EnvironmentalEscapeGoal`, pure mutation/timing policies, config/UI, tests and documentation |
| Systems changed | Goal arbitration, bounded navigation, block mutation, SPM backpack interoperability, main-hand transaction, drops/durability, config UI |
| Claimed stability | Compilation/package/tests `CONFIRMED`; all Minecraft behavior `UNVERIFIED` |
| Verification | Fresh `gradlew.bat clean test build`: 50 tests, zero failures/errors/skips; current artifact hash `738130B4FB121CFE2C5DFC14FC211FA2B95D4352E251E5F0245C2127FE44B8E2` |
| Deferred | Runtime Powder Snow/sand/stone cases, tool animation, correct drops/durability, fire/death interruption, save/reload, claims/protection interoperability, profiling |

Three negative-evidence probes supporting the gap were preserved from implementation: pinned SPM
source had no Powder Snow/freezing goal, no `isInWall`/suffocation escape, and no air-recovery goal.
For tool selection, SPM exposes no public per-block best-tool method; its private toolkit covers
combat categories and explicitly includes sword/axe/pickaxe/ranged, not a universal block selector.

### Step 2 — Reconstruction

| Stage | Problem | Action | Result | Evidence | Confidence |
|---|---|---|---|---|---|
| Gap confirmation | Mob remained in Powder Snow without a crash | Logs-first search plus pinned SPM/mapped API inspection | Silent missing behavior classified `AI_EXTENSION`; native fire handling retained | active `latest.log`; SPM `registerGoals`; mapped `Entity.isInPowderSnow`/`isInWall` | gap `CONFIRMED` |
| First design | Need escape without indiscriminate digging | Priority-0 `MOVE` goal: jump, bounded entity-ticking safe-ground search, delayed exact-intersection removal | Compiled and policy-tested as 1.9.0 | `EnvironmentalEscapeGoal`, `EnvironmentalEscapePolicyTest` | build `CONFIRMED`; behavior `UNVERIFIED` |
| TPS correction | No-path state made `navigation.isDone()` true continuously | Removed it as a replan trigger; fixed one-second cadence and max 12 path calls | Static hot-path defect removed before package | `REPATH_INTERVAL`, `MAX_PATH_ATTEMPTS` | `CONFIRMED` code property |
| Design correction | Generic instant deletion was not player-like; common delay was wrong for head suffocation | Split Powder Snow grace (8 ticks) from `isInWall` (0); add face/swing/cracks/timed break | 1.9.1 mining state machine | `tickMining`, `MiningPolicy` | build `CONFIRMED`; behavior `UNVERIFIED` |
| Equipment integration | Need correct owned tool without a second inventory/evaluator | Read SPM's real `InventoryCarrier` backpack; rank main hand + backpack by vanilla stack speed/correct-tool semantics | Block-specific tool choice, temporary swap, tool-aware loot/durability | pinned SPM `getInventory`; `chooseBestTool`; `equipTemporarily` | static integration `CONFIRMED`; runtime `UNVERIFIED` |
| Packaging | Parallel work changed the same version after the first hash was reported | Rebuilt current source and audited test XML/artifact hash | Current combined 1.9.1 hash pinned; earlier hash retained as chronology | fresh build output and SHA-256 | `CONFIRMED` |

### Step 3 — Errors and recoveries

| Error | Root cause | How found | Recovery | Reusable? |
|---|---|---|---|---|
| Initial 30-tick grace treated Powder Snow and true suffocation alike | Safety ladder was applied without distinguishing whether movement could physically solve the hazard | User challenged the model | Zero grace for `isInWall`; short configurable Powder Snow grace | Yes, but runtime-unverified here |
| Initial last resort instantly deleted a block with a generic tool context | Focused on escape safety and omitted actor fidelity | User requested correct tools and normal breaking time | Real inventory scan, crack/swing timing, tool-aware drops and durability | Yes, but runtime-unverified here |
| Replanned every tick when no path existed | `navigation.isDone()` was incorrectly treated as a useful event even when no path had ever started | Static review before final build | Time-gated replanning plus capped path calls | Yes; reinforces existing SPM-5 guidance |
| First multi-file documentation patch failed atomically | Concurrent file history made one context anchor stale | `apply_patch` verification failure | Re-read exact sections and applied narrow patches | Workflow-level; existing small-change discipline already covers it |
| Reported 1.9.1 hash became stale | Parallel exploration edits rebuilt the same version after the environmental build | Reflection compared artifact timestamp/hash with source history | Fresh clean build; record both chronological and current hashes | Yes: verify final artifact at handoff |

### Step 4 — Breakthroughs

| Breakthrough | Obstacle | Discovery | Final technique | Why it matters | Evidence | Label |
|---|---|---|---|---|---|---|
| Vanilla interface as compatibility boundary | Addon cannot link against PolyForm SPM implementation | PlayerMob implements public vanilla `InventoryCarrier` | Read authoritative backpack through the vanilla interface | No reflection, copied code, second inventory or hard SPM dependency | pinned SPM source + compile | `STRONGLY_SUPPORTED` statically; runtime pending |
| Block-derived tool ranking | SPM has no public block-tool evaluator | Each stack already reports destroy speed and correct-tool status for the actual block | Rank owned stacks by calculated required ticks | Naturally admits modded tools that implement vanilla semantics | policy tests + mapped APIs | `EXPERIMENTAL` until runtime |
| Hazard-specific recovery ladder | Movement is viable for Powder Snow but often impossible for a suffocating head block | The semantic hazard state identifies which rung can work | Movement grace for snow; immediate timed mining for `isInWall` | Avoids both instant griefing and impossible waiting | code/tests only | `EXPERIMENTAL` |
| Bounded replanning independent of navigation completion | Failed navigation reports done forever | Completion is not a scheduling signal when no active path exists | Fixed cadence plus path-attempt cap | Prevents a silent per-entity pathfinding loop | source inspection | `STRONGLY_SUPPORTED` |

### Step 5 — Reusable pattern candidates

1. **Owned-tool, block-derived action selection** — `COMPATIBILITY_PATTERN` / `AI_PATTERN`.
   Use when an NPC exposes an authoritative inventory through a stable interface and target tools
   implement vanilla mining semantics. Do not use for tools whose real action requires a Player,
   capability, custom packet or target API. Validate selection, timing, enchantments, durability,
   drops, interruption and save/reload. Confidence: `EXPERIMENTAL`.
2. **Hazard-specific rung selection** — `AI_PATTERN`. Derive whether navigation can solve the
   observable hazard before applying a universal grace period. Do not infer block ownership or
   permission from friendship. Validate natural and player-built cases plus protection mods.
   Confidence: `EXPERIMENTAL`.
3. **Temporary-equipment transaction** — `PERSISTENCE_PATTERN`. Park the displaced item in the
   selected tool's exact source slot, restore on every stop path, and fail without overwriting if
   the slot changes. Do not claim safety until mid-action save/reload is tested or persisted recovery
   metadata exists. Confidence: `UNVERIFIED`; current implementation has the reload risk below.
4. **Navigation-done is not a replan clock** — `PERFORMANCE_PATTERN`. Replan on explicit cadence or
   state transition, not continuously because an absent/failed path is already done. Validate path
   call counts at scale. Confidence: `STRONGLY_SUPPORTED`, already substantially covered by SPM-5.

### Step 6 — Lesson-to-instruction mapping

| Lesson | Existing instruction | Relationship | Evidence | Action |
|---|---|---|---|---|
| Native interface over implementation coupling | SPM-0 highest-level compatibility + SPM-2 anti-duplication | `DUPLICATES_EXISTING` | `InventoryCarrier` integration compiles | No shared edit |
| Movement → real item → bounded mutation | compatibility-first universal AI reference | `DUPLICATES_EXISTING` | This implementation follows it | No shared edit |
| Never pathfind continuously from failed navigation | SPM-5: no scans/reuse observation/bounded recovery | `STRENGTHENS_EXISTING` | Self-inflicted bug caught statically; no profiler | Project reflection only pending runtime scale evidence |
| Temporary equipment must survive save/reload | Port persistence safety / fixed-slot transaction principles | `INSUFFICIENT_EVIDENCE` | No runtime or persistence test; current state is not serialized | Record project risk; do not promote current technique |
| Block-derived best-tool selection | No exact shared procedure | `NEW_CAPABILITY` | Pure tests and compile only | `EXPERIMENTAL`; do not promote until runtime matrix passes |

### Step 8 — Rejected or quarantined proposals

| Proposal | Reason not promoted |
|---|---|
| Make real-tool environmental mining a mandatory SPM rule | No runtime proof for animation, drops, durability, restoration or compatibility |
| Declare the temporary swap atomic/persistence-safe | Goal fields are not saved; a world save/reload during mining may preserve the temporary equipment arrangement without restoration |
| Use FakePlayer by default | Adds player-only hooks, permission identity and another lifecycle without evidence the vanilla Mob path fails |
| Make friendship suppress block breaking | Relationship does not prove who owns the intersecting block; creates false negatives in natural hazards |
| Claim exact vanilla mining parity | Base hardness/tool divisor is implemented, but Efficiency, Haste, Mining Fatigue, water and airborne Player modifiers are not |
| Add a shared validation gate from compile success | Violates AV-1; relevant validation is runtime and persistence behavior |

### Step 9 — Knowledge-transfer handoff

- **Completed objective:** implemented environmental recovery and real-tool timed obstruction mining
  in Scavenger 1.9.1.
- **Current source:** `goal/EnvironmentalEscapeGoal.java`, `EnvironmentalEscapePolicy.java`,
  `MiningPolicy.java`; config in `ScavengerConfig.java` and `client/ScavengerConfigScreen.java`.
- **Architecture:** priority-0 foreground goal; semantic hazard detection; bounded entity-ticking
  path search; exact-intersection mutation gates; mining sub-state; authoritative vanilla
  `InventoryCarrier` backpack; temporary main-hand swap; tool-aware loot/durability.
- **Commands proven:** `.\gradlew.bat clean test build` from the project root.
- **Current artifact:** `build/libs/spmscavenger-1.9.1.jar`, SHA-256
  `738130B4FB121CFE2C5DFC14FC211FA2B95D4352E251E5F0245C2127FE44B8E2`; 50 tests pass.
- **Avoid:** `navigation.isDone()` as an every-tick replan trigger; generic instant deletion; one
  grace period for every hazard; copying/conjuring tools; overwriting a changed inventory slot;
  claiming exact Player mining parity.
- **Highest-priority next action:** with explicit launch approval, test Powder Snow and head-level
  sand/stone with competing tools, then fire/death interruption and save/reload mid-crack. Inspect
  `latest.log`, inventory before/after, visible cracks, elapsed ticks, drops and durability.
- **Critical open risk:** temporary equipment state is not persisted. Saving/reloading mid-mining
  may leave the tool in main hand and the prior weapon in its backpack. This is not item loss, but
  it violates strict restoration until runtime proves SPM normalizes it or recovery metadata is added.
- **Other unverified:** claim/protection mod behavior, modded tools with custom APIs, scale/TPS,
  player-only mining modifiers and exact visual synchronization.
- **Constraints:** no Minecraft launch without explicit approval; no commit/push requested; pinned
  SPM source is read-only PolyForm Shield reference material.

### Shared-instruction changes made by this reflection

None. The high-level compatible/universal ladder already exists, while every new concrete mining,
equipment and persistence lesson lacks the required runtime evidence for promotion. Project-specific
evidence and risks were appended here and the current artifact chronology was clarified in
`DECISIONS.md`. No gate was weakened, and no commit, push or Minecraft launch occurred.

---

## 2026-08-10 — Photon objective-readout brightness, duplication, and billboard attachment

**Gate PREFL-1.** Incremental reflection only. This covers the SPM decision-label shader repair in
Scavenger 1.9.3; it does not audit unrelated AI, progression, or parity work.

### Scope and evidence

| Field | Result |
| --- | --- |
| Reported defect | With Iris 1.8.8 + Photon 1.3b, SPM objective labels were directionally dark; intermediate overlays duplicated and then detached labels to the left |
| Final implementation | Shader-only bounded HUD redraw using `projection × position/view × billboard`, empty-string host suppression, and explicit solid-terrain visibility adaptation |
| Static evidence | `ShaderReadoutOverlay.java`, `SpmScavengerClient.java`, `PlayerMobRendererReadoutMixin.java`, focused tests, remapped-JAR inspection |
| Build evidence | Prior `clean build`: 605 tests, zero failures/errors/skips; audited `spmscavenger-1.9.3.jar`, SHA-256 `B2F9C72AC8FF1843E4039E089845AF29D82F73792201C63711AD393A27CAAA75`. The artifact was no longer present in `build/libs` during reflection verification; rebuild to reproduce it |
| Runtime evidence | User: “Fixed.” Final Photon brightness, single-copy presentation, and attachment are `RUNTIME_CONFIRMED`; Codex did not launch Minecraft |
| Still unverified | Exact translucent/entity occlusion parity and frame-time/raycast cost |

### Reconstruction and errors

| Stage | Actual result | Evidence/confidence |
| --- | --- | --- |
| Packed-light repair | Full-bright alone did not escape Photon’s final composite; label remained view-dependent | User screenshot/session, `RUNTIME_CONFIRMED FAILURE` |
| Forced background attempt | Made the presentation black and violated host/user backdrop ownership | User report, `RUNTIME_CONFIRMED FAILURE`; reverted |
| First HUD replacement | Alpha-zero did not reliably suppress the shader pass, producing dark + bright duplicates | User screenshot, `RUNTIME_CONFIRMED FAILURE` |
| First projection | Combined the font-local billboard matrix with projection but omitted Minecraft’s separately installed camera/view matrix; text projected far left | Target bytecode/source plus screenshot, `CONFIRMED` |
| Final repair | Captured projection and position/view at world-render start, excluded Iris shadow pass, composed the complete chain, suppressed original text emission, and bounded per-frame state | Code/tests/JAR `CONFIRMED`; final visual `RUNTIME_CONFIRMED` |

The self-inflicted errors were treating packed light as final shader brightness, changing backdrop
semantics before proving the cause, assuming alpha zero meant no rasterization, and treating a
legal font draw matrix as a complete MVP. The user’s screenshots falsified each weak assumption.

### Proven patterns and limits

1. **Complete transform ownership before world-to-HUD projection** — `RENDERING_PATTERN`, `PROVEN`.
   Trace local/billboard, camera/view, and projection ownership in the exact target renderer. A
   partial draw matrix can be valid for the host pipeline yet insufficient for independent screen
   projection.
2. **Suppress replaced geometry at emission** — `SHADER_PATTERN`, `PROVEN` for this text path.
   Zero alpha is not a portable shader-pack suppression contract. Intercept narrowly and emit no
   glyphs while preserving all unrelated host rendering.
3. **HUD replacement requires an explicit depth contract** — `RENDERING_PATTERN`,
   `STRONGLY_SUPPORTED`. The selected solid-block raycast is an adapted approximation, not proof of
   GPU-depth parity. Bound/deduplicate it and measure before making performance claims.
4. **Visual regression matrix must test attachment and duplication, not brightness alone** —
   `VALIDATION_PATTERN`, `PROVEN`. Rotate/move the camera, vary distance and darkness, and assert one
   entity-attached result with shader on/off.

### Lesson-to-instruction mapping

| Lesson | Existing instruction | Relationship | Promotion |
| --- | --- | --- | --- |
| Full world-to-HUD transform chain | Gate 5.2 already requires transform ownership | `STRENGTHENS_EXISTING` | Added a focused billboard/HUD subsection to `rendering-and-shaders.md` |
| Geometry suppression, not alpha-zero | Gate 5.5 backend fallback validation | `NEW_CAPABILITY` | Added a narrow Gate 5.5 requirement; not generalized beyond replaced render output |
| HUD loses depth ownership | Gate 5.5 compatibility/fallback matrix | `CLARIFIES_EXISTING` | Requires an explicit restored/adapted occlusion contract |
| Exact raycast scheme and faint `0x20` alpha | No universal rule | `PROJECT_SPECIFIC` | Kept in project decisions/tests; runtime/performance scope remains limited |

### Alternatives and rejected shortcuts

| Alternative | Outcome |
| --- | --- |
| Pixel offsets or empirical scale multipliers | Rejected: camera/FOV/GUI-scale dependent and does not repair transform ownership |
| Patch Photon | Rejected: third-party mutation and shader-pack-specific maintenance |
| Always use the HUD path | Rejected: needlessly changes vanilla/Sodium behavior |
| Claim exact occlusion parity from one successful visual retest | Rejected under AV-1: translucent surfaces, entities, and cost were not isolated |

### Handoff

- **Reproducible artifact record:** the prior audited output was
  `build/libs/spmscavenger-1.9.3.jar`, hash above; it was absent from `build/libs` when this
  reflection was finalized and must be rebuilt before installation/handoff.
- **Runtime-confirmed:** Photon readability, no duplicate label, correct PlayerMob attachment.
- **Unverified:** translucent/entity occlusion, absent-Iris bootstrap, shadow-pass edge cases, and
  frame-time/heap behavior at many visible PlayerMobs.
- **Shared guidance updated:** rendering/shader reference and Gate 5.5 only; no unrelated rules or
  wrappers were rewritten.
- **Constraints:** no Minecraft launch, commit, push, or PR was performed by Codex.
