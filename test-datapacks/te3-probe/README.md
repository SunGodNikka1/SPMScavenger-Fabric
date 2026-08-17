# V2-TE P0-3 — reachability probe

**TEMPORARY PROBE SUPPORT.** Remove with `com.noobk.spmscavenger.debug.Te3ProbeCommand`, the
`modCompileOnly` Trade Everything dependency, and the Modrinth repository block.

**Answers one question:** does a useful intersection exist between what Scavenger may *sell*, what
Trade Everything will *pay*, and what Scavenger currently *wants*? If not, V2-TE would ship as
technically correct dead machinery — the `D-VR-075` lesson, caught one slice earlier.

**Requires** `tradeeverything-fabric-0.3.0.jar` installed in the instance. The mod is
`modCompileOnly` here, so it is not in the Scavenger jar and must come from the modpack.

## Running

```mcfunction
/function te3:scenario/a_iron_frontier
/spmscavenger debug te3 index                              # cold + repeated ensureIndexed
/spmscavenger debug te3 scan minecraft:iron_ingot          # asserts the demand, then classifies

/function te3:scenario/e_torch_chain
/spmscavenger debug te3 scan minecraft:charcoal

/function te3:scenario/d_protected
/spmscavenger debug te3 scan

/function te3:scenario/b_funding_witness             # R12 - the deterministic B witness
/spmscavenger debug te3 index
/spmscavenger debug te3 scan minecraft:iron_ingot

/function te3:cleanup
```

`scan` **refuses** until `index` has run. `OfferQuoter.quote` has zero references to
`MinecraftServer` or `ensureIndexed` — confirmed from bytecode — so it will happily price against an
empty index and return offers from TE's *fallback* economy. A probe that measured the wrong economy
would be worse than no probe (**P0-1**).

## Ownership boundary

| Datapack | Java probe |
|---|---|
| villagers, professions, positions | `RecipeValues.ensureIndexed(server)` |
| mob inventory and equipment | `OfferQuoter.quote(AbstractVillager, ItemStack, MerchantOffers)` |
| scenario progression, reset | exact quote inspection, classification, timing |
| (R12) which professions/levels exist | (R12) `te3 fixture` re-rolls their boards until vanilla produces the required draw |

`te3 fixture` is a deliberate, documented crossing of that line. Two boards the B witness needs are
ordinary vanilla draws that are merely *uncertain* — the all-sell novice armorer (p=0.6) and the
level-3 toolsmith holding the iron pickaxe (p=0.4, the exact draw that failed VR-T2's first setup).
It **discards vanilla boards until vanilla rolls the one it needs** and writes no `Offers` tag, so
the pickaxe's price and enchantment stay whatever vanilla chose. Authoring the listing instead would
have made "read its exact live price" vacuous — the price would be the one the fixture picked.

No pricing is reimplemented. An oracle that recreated Trade Everything's valuation would share the
assumptions of the thing it is measuring, and its agreement would prove nothing.

## Buckets

| | |
|---|---|
| **A DIRECT** | payout exactly satisfies the current demand or its `D-VR-075` projection |
| **B FUNDING** | payout is emeralds *and* a concrete current BUY can be funded by them — tested **pairwise across fixture villagers**, since V2-E R7 supports a SELL on one merchant funding a BUY on another and VR-T2 proved it (Fletcher sticks→emeralds ×4, then Toolsmith emeralds→pickaxe) |
| **C IRRELEVANT** | safe quote, no current consumer benefits |
| **D ILLEGAL** | input is not disposition-authorized — checked **before** value, per `W-5` |
| **E REPRESENTATION MISS** | payout would actually serve the consumer, but the demand names a different item |

**If A + B = 0, stop.** Do not implement the bridge and call green tests success; find the semantic
gap first.

## Scenario B — the deterministic witness (R12)

One run, no reroll loop. It demonstrates the route the source census established:

- **Escalation is structurally impossible** for logs/planks/sticks. `TradePricer.payoutFor` weighs
  **one** item's value — `valueSixteenths` ignores `getCount()` — at `1 x 0.75 = 0.75`, against
  `unit x cap`, whose smallest possible value is `1`. So no authorized input can ever overflow a
  preferred commodity into emeralds.
- **The fallback is the only door.** `DefaultBuyItemSelector.select` returns `EMERALD` exactly when
  no non-synthetic offer carries a non-emerald cost. The novice armorer pool is
  `EmeraldForItems(COAL,15)` plus four `ItemsForEmeralds` armour listings with 2 drawn, so 60% of
  novice armorers are that board. Novice toolsmiths are identical in shape; weaponsmith and
  leatherworker reach it at 1/3. Every other profession's level-1 pool has too few sell listings.
- The resulting quote is **`22 oak_log -> 1 emerald`** — `22 x 1 x 0.75 = 16.5`, `floor(16.5/16) = 1`,
  waste 3.03% which is inside TE's 10% acceptance — and it carries
  `SyntheticOfferFactory.MAX_USES = 999_999`.

### Value is not capacity

The census first proposed ">=484 logs, no emeralds". That is right as *value* and wrong as
*inventory*: the backpack has 8 slots, and 8 stacks of logs leaves the first `22 oak_log -> 1 emerald`
nowhere to put its emerald. `VillagerTradeAdapter` stages the debit and requires the result to insert
before it commits, so a 17-use plan dies on use one. The shipped fixture is

```
backpack   6 x 64 oak_log (384) | 5 emerald | 16 torch
main hand  stone_pickaxe        offhand  iron_axe
```

Purchasing power is unchanged at 22 — `floor(383/22) = 17` TE uses plus 5 held — which covers the
whole confirmed 8..22 iron-pickaxe envelope. The torches keep the SURVIVAL charcoal demand off, since
SURVIVAL outranks PROGRESSION. The final BUY empties the emerald stack, which is what frees the slot
the pickaxe arrives in.

**Capacity is reported, never classified on.** `classify` asks whether a route can be *assembled*;
insertion headroom is not part of that question, and writing a second transaction model inside the
probe would have produced an oracle that agrees with itself. So `scan` prints `free slots` and
`mergeable emerald stack`, and warns when both are absent. Execution capacity remains **P0-2**.
`mustNotHappen_aBFundingResultIsReadAsPhysicallyExecutable` pins the gap: an all-logs backpack is
worth *more* and classifies identically as `B_FUNDING` while being physically unable to start.

## The E case is the one to watch

Scenario `e_torch_chain` exists for it specifically. `WorkDemandPolicy` demands **`CHARCOAL`**;
vanilla villagers buy **`COAL`**; TE's payout chooser prefers a non-emerald commodity the villager
buys. Coal and charcoal are interchangeable as torch fuel, so a `logs → coal` quote against a
charcoal demand is **not** "irrelevant" — it is a demand-representation defect wearing a
compatibility costume.

If bucket E is non-empty, the fix is whether the torch chain should demand *a fuel* rather than
*charcoal* — a `D-VR-075`-shaped question about units, **not** compatibility code.

## Limitations

- **Only the first `index` after a fresh launch is genuinely cold.** Trade Everything memoizes on
  `(RecipeManager, config)` identity, and `te3 reset` clears only the probe's own flag — it cannot
  and must not clear TE's index. Later `index` calls measure the memoized path.
- **`scan` takes an optional expected demand** and refuses when `WorkDemandPolicy` selected
  something else. Without it, `a_iron_frontier` could silently run as a torch scenario: `SURVIVAL`
  outranks `PROGRESSION`, so surplus logs with torches below target select `CHARCOAL` over
  `IRON_INGOT`. The scenario now stocks torches first; the assertion is what proves it worked.
- **The fixture mob is `NoAI`.** `scenario → index → scan` is a manual sequence with ticks in
  between; a live mob would mutate the inventory the scenario established. Pairing is done over
  one exact BUY plus one exact TE SELL at a time — never a flattened global board, because
  `OfferSnapshot.index()` is buyer-local and a fake global market would reintroduce the
  flattened-index defect R8 removed.
- **Never executed.** Written from source and verified by compilation only.
- Reachability is not desirability. A non-empty A/B says a bridge *could* pay off, not that
  autonomous behaviour would be sensible — that is V2-TE's own design question.
- Quote timing is measured per call in a scan loop, which is not the shape production would use.
  Treat it as an order-of-magnitude signal, not a budget.
- **Scenario B is the only deterministic one.** `_merchants` (scenarios A/D/E) rolls boards freely
  and a single scan there is a sample, not a census. `_merchants_b` plus `te3 fixture` is what makes
  the B witness repeatable.
- `_merchants` spawns level-1 professions except the toolsmith (level 3, since the iron pickaxe is a
  level-3 listing — VR-T2 runtime finding). Their boards still roll randomly, so a single scan is a
  sample, not a census. Re-run scenarios for a fuller picture.
