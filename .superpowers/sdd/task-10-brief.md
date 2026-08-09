# Task 10 brief: MI-3/MI-23 resource need layers

## Target and source reference

- Target: `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`
- Read-only host oracle: `D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`
  at `4b80b5e849ccabd69e7c9c2f44dc25f7233c7796`.

## Scope and evidence

MI-1 is implemented and provides the gather-intent seam. Implement only the generic NEED half of
`ResourceWealthPolicy`: immediate, replacement, project, and reserve shortfalls with deterministic
priority. Existing consumer specs remain the owners of actual immediate deficits. Do not add curves,
greed, opportunity, profiles, config, or gather integration; those belong to MI-24/25/4.

Use one category-neutral policy, not iron/diamond classes. Allocate current stock to blocking needs
in priority order (immediate, replacement, project) before working reserve. Inputs are non-negative;
invalid contexts fail fast. No Minecraft launch, commit, push, or reference edit.

Alternatives: selected staged allocation makes the priority observable and prevents one item from
satisfying several layers. Independent `max(0, need-current)` calculations are rejected because they
double-count stock. A weighted float formula is deferred because MI-24 owns marginal utility.

Must happen: project and replacement shortfalls survive after immediate allocation; remaining stock
then satisfies reserve. Must not: double-count stock, produce negative values, or report wealth.
Write RED tests first, run focused GREEN and `gradlew.bat clean build`, then report in task 10.
