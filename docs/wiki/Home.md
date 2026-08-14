# Social Player Mobs: Scavenger Wiki

This wiki is the durable reference for **implemented Scavenger features and architecture**.

Planning RFCs explain how a feature was designed. The wiki explains how the finished system works, what contracts future code must preserve, and where a new feature should integrate.

## Feature guides

- [Opinion System](Opinion-System.md) — personality, affect, learned opinions, discretionary choice, EXPLORE / REST / SOCIAL, causal learning, and SPM execution handoff.
- [Extending Opinion](Extending-Opinion.md) — how to add another discretionary activity without reopening the original GA-OPINION RFC or creating a mega-goal.

## Architecture guides

- [Compatibility Contracts](Compatibility-Contracts.md) — generic rules for optional host-mod integration, ownership, parity, Mixins, lifecycle, cleanup, and fail-closed behavior.

## Scavenger in one paragraph

Scavenger is an **addon intelligence layer** for Social Player Mobs (SPM). It does not replace SPM's PlayerMob entity framework, combat, orders, relationships, backpack, or native host behaviors. Scavenger adds survival, progression, exploration, shelter, opinion, mining and other autonomous decision systems around that host. Mandatory survival, combat, explicit player authority, shelter/safety, and progression remain authoritative; Opinion only chooses among legitimate discretionary options.

## Authority model

A useful high-level ordering is:

```text
Immediate survival / self-defense
    > explicit player authority
    > shelter and environmental safety
    > survival recovery
    > progression / work
    > Opinion / discretionary activity
```

The exact subsystem may refine this ordering, but a lower layer must never manufacture permission to override a higher one.

**Core rule:** preference affects choice; preference does not create permission.

## Documentation roles

| Location | Purpose |
| --- | --- |
| `docs/wiki/` | Current feature and architecture reference |
| `plans/RFC-*.md` | Active design/planning work |
| `docs/porting/DECISIONS.md` | Important implementation decisions and failure history |
| `docs/porting/TEST_MATRIX.md` | Behavioral contracts and regression scenarios |
| `.superpowers/sdd/` | Task briefs, reports, and progress ledger |

When an RFC is finished, move durable product and architecture knowledge here. Historical planning detail can remain in Git history instead of staying permanently active in `plans/`.
