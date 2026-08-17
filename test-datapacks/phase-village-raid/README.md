# VR-T2 — vanilla trade acceptance fixture

**TEMPORARY V2-H PROOF SUPPORT.** Remove with `com.noobk.spmscavenger.debug` once VR-T2 is captured.

**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` (`D-VR-069`, `D-VR-075`, VR-T2)
**Namespace:** `spm_vrt2` · **Target:** MC 1.21.1 Fabric, Social Player Mobs 0.86.0

Copy into the test world's `datapacks/`, `/reload`, stand on flat ground, then:

```mcfunction
/function spm_vrt2:quickstart          # arena + merchants, AI enabled beside their workstations
# ... wait a few seconds for vanilla to claim the workstation POIs ...
/function spm_vrt2:settle               # verify job sites, freeze, OPEN the stalls, spawn the mob
/spmscavenger debug vrt2 setup
/spmscavenger debug vrt2 status
# ... let the mob act ...
/spmscavenger debug vrt2 trace
/function spm_vrt2:cleanup
```

The two-step start is deliberate. Merchants are summoned **with AI** next to their matching
workstations and are frozen only in `settle`, once vanilla has claimed the POIs by itself. Freezing
at summon time would leave them unable to ever acquire a workstation, and the "village" the mob then
perceived would be one vanilla never formed. The PlayerMob is spawned last, so its own perception
records a settlement that genuinely exists.

`settle` is **fail-closed on the real signal**: it proceeds only when a `vrt2_toolsmith` *and* a
`vrt2_fletcher` each hold a naturally acquired `minecraft:job_site` memory, and otherwise names
which one is missing. The two roles are asserted **positively**, by tag — "no unsited merchant
exists" is vacuously true when a merchant failed to spawn, or when none did, and would have advanced
a malformed fixture. Reading a memory is not authoring one; nothing writes Brain state.

**`quickstart` is safe to re-run.** It clears previously tagged fixture entities first, so a second
run cannot leave two toolsmiths standing in a freshly rebuilt arena.

## What this proves

A PlayerMob with a stone pickaxe, no reachable iron, and four emeralds too few **earns** its way to
an iron pickaxe through the real vanilla economy:

```text
iron_pickaxe_upgrade consumer live, source demand iron_ingot x3
        ↓  real bounded gather scan finds nothing
route UNKNOWN → INFEASIBLE          (the mob publishes this, not the harness)
        ↓  D-VR-075 projection: same consumer, market units
BUY target = minecraft:iron_pickaxe
        ↓  4 × Fletcher 32 sticks → 1 emerald
deficit closed, chain SELL_TO_FUND → BUY_TARGET
        ↓  walk to the Toolsmith
BUY the exact enchanted iron pickaxe
        ↓
consumer closes · exactly ONE settlement trade episode
```

## The economy is untouched

`D-VR-069` requires an uncontaminated vanilla baseline. The fixture may **observe** world truth and
choose initial conditions; it may not shape the market.

| Allowed | Forbidden |
|---|---|
| read the live price `E`, seed `E-4` emeralds and 131 sticks | author or edit any `Offers` NBT |
| place exactly one vanilla Toolsmith and one vanilla Fletcher | reroll until the price is favourable |
| clear the gather prism, freeze time and weather | publish route exhaustion, force a transaction |
| let vanilla claim POIs, then freeze the merchants | author Brain memories, POI occupancy, `KnownVillage` or relationship state |

No offer is written anywhere in this datapack. Prices come out of vanilla's own
`EnchantedItemForEmeralds` roll, verified as `8..22` emeralds by `VanillaTradeRouteContractTest`.

## Why the geometry is proof machinery, not scenery

**The prism must be sterile for the *whole* gather intent.** `GatherResourcesGoal` publishes
exhaustion only on **zero pass-one candidates**, across logs, coal, cobble, raw iron and diamond
together. A single log in range yields `CANDIDATES_ALL_REJECTED_PROTECTION` instead, and the
`UNKNOWN → INFEASIBLE` transition this whole proof waits for never happens. "No iron nearby" is not
the precondition — *nothing the intent wants, anywhere in the prism* is. Hence smooth stone (not
cobblestone, which *is* a candidate), no logs, and a clear margin beyond the radius-20 / `dy ±4`
scan volume. Planks are **not** a gather candidate — the intent covers logs, coal, cobblestone, raw
iron and diamond — they are simply avoided because they come from logs.

**The village must be real.** `VillagePerception` reads `PoiTypeTags.VILLAGE` with
`Occupancy.IS_OCCUPIED`, so a bell and some furniture are not enough: a villager has to have claimed
the site. The fixture therefore builds bounded stalls with a smithing table and a fletching table,
lets the AI-enabled merchants acquire them, and only then freezes. Nothing writes POI occupancy or
Brain memories directly — a hand-authored village would prove the mob can trade in a world state
vanilla never produces.

**The stalls are warm-up machinery, and `settle` removes them.** They exist only to give POI
acquisition a short unambiguous path. Production navigates directly to the Villager entity until
within 3 blocks, so a merchant left walled in would either burn the path and approach budget on an
unreachable target or permit an unnatural through-wall transaction — neither is the physical trade
VR-T2 is meant to prove. `settle` freezes first, then removes only the walls
(`replace smooth_stone`); the claimed workstations are deliberately **not** re-set afterwards, since
re-asserting a block a villager has already claimed raises a question about its POI record that this
fixture has no reason to ask.

**The merchants are 18 blocks apart.** Both are inside the 16-block trade candidate radius of the
centre but far outside each other's 3-block interaction range, so after the fourth sale the mob must
**walk**. That walk is what gives the 1-tick observer a window to witness the same chain sitting in
`BUY_TARGET` before the purchase closes the consumer — without instrumenting the transaction itself.

**The iron axe is in the offhand, not a stone axe.** `activeIronToolRecipe` is ordered
pickaxe-then-axe and the default config targets DIAMOND for both, so a stone axe would mean the
purchase hands the frontier straight to `iron_axe_upgrade` rather than closing anything, and the mob
would trade on past the five transactions this proof bounds. Seeded by the command, not here.

## Acceptance

The verdict is `/spmscavenger debug vrt2 trace`, which compares against an oracle captured at T0 —
merchant UUIDs, exact offer indexes, baseline uses, and the exact quoted result stack. It never
infers a sale from inventory. PASS requires **all** of:

- T0 consumer is `spmscavenger:iron_pickaxe_upgrade`, route `UNKNOWN`, no exhaustion evidence
- `UNKNOWN → INFEASIBLE` witnessed, and **no merchant use changed before it**
- Fletcher's exact captured offer: uses delta **exactly +4**
- Toolsmith's exact captured offer: uses delta **exactly +1**
- the acquired stack matches the quoted result by item **and components**
- one chain throughout — same `consumerKey`, `createdAtTick` **and `expiresAtTick`**, buying
  `minecraft:iron_pickaxe`, target quantity 1 — reaching `BUY_TARGET` only after exactly four sales
- settlement trade episodes delta **exactly +1** — four sales and a purchase are *one* visit

Any must-not condition latches `FAIL` permanently; a later correct-looking state cannot restore
`PASS`.

## Known limitations

- **Never executed.** Every part of this fixture and its harness has been reasoned from source and
  verified by compilation only. Treat a first-run `PASS` with more suspicion than a `FAIL`.
- `/setup` refuses rather than guessing: no unique Toolsmith/Fletcher pair, no settlement anchor, an
  unresolvable offer index, a non-emerald-only purchase, or a Fletcher quote that is not
  `32 sticks → 1 emerald` all abort with a reason.
- The mob must already remember a settlement or `/setup` aborts — the one-episode requirement
  cannot be proven without an anchor. That is why the merchants claim their POIs *before* the mob
  is spawned, and why the mob may need a few seconds of perception time before `setup` succeeds.
- **How long POI acquisition takes has not been measured.** `settle` now refuses rather than
  proceeding early, so the failure mode is an explicit "wait and run settle again" rather than a
  silently under-formed village — but how many attempts that needs is a first-run question.
- The job-site check reads `Brain.memories."minecraft:job_site"`. That NBT path is **unverified at
  runtime**; if it does not resolve, `settle` will refuse indefinitely rather than proceed, which is
  the correct direction to fail but would need the path corrected before the fixture is usable.
