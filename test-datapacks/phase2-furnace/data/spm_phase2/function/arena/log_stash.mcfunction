# Log stash as floor item entities near +2,+5 (charcoal inputs without mining)
kill @e[type=item,distance=..2,nbt={Item:{id:"minecraft:oak_log"}}]
summon item ~2 ~1 ~5 {Item:{id:"minecraft:oak_log",count:8},PickupDelay:0s,Age:0s}
summon item ~2.3 ~1 ~5.2 {Item:{id:"minecraft:oak_log",count:8},PickupDelay:0s,Age:0s}
