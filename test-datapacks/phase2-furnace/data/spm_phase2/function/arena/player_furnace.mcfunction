# Busy player furnace at +6 — coal in fuel slot so mayUse should skip (RT-F3)
setblock ~6 ~ ~0 furnace[facing=west]
data merge block ~6 ~ ~0 {Items:[{Slot:1b,id:"minecraft:coal",count:8b}]}
