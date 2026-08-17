# TEMPORARY V2-H PROOF SUPPORT. Run after `quickstart`, before `settle`.
#
# Keep the FIRST candidate board that naturally contains an iron-pickaxe route. Selection is on
# ROUTE PRESENCE ONLY - never price, never enchantment. Whatever the kept board rolled is what the
# proof runs against, which is why this is not "rerolling for a cheap Toolsmith".
#
# Reads Offers NBT; writes none.
tag @e[type=villager,tag=vrt2_smith_candidate] remove vrt2_has_route
execute as @e[type=villager,tag=vrt2_smith_candidate] if data entity @s Offers.Recipes[{sell:{id:"minecraft:iron_pickaxe"}}] run tag @s add vrt2_has_route

execute unless entity @e[type=villager,tag=vrt2_has_route,limit=1] run say [VR-T2] pick REFUSED - no candidate toolsmith rolled an iron-pickaxe route. Run cleanup and quickstart again for a fresh pool.
execute if entity @e[type=villager,tag=vrt2_has_route,limit=1] run function spm_vrt2:_pick_confirmed
