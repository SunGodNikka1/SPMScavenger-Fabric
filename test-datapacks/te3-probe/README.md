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
- `_merchants` spawns level-1 professions except the toolsmith (level 3, since the iron pickaxe is a
  level-3 listing — VR-T2 runtime finding). Their boards still roll randomly, so a single scan is a
  sample, not a census. Re-run scenarios for a fuller picture.
