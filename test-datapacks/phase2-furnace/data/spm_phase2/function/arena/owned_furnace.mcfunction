# Empty non-owned furnace (communal opt-in only) at +4,+3 (no fuel/input; claimable when placeFurnaces off)
setblock ~4 ~ ~3 furnace[facing=south]
data merge block ~4 ~ ~3 {Items:[]}

