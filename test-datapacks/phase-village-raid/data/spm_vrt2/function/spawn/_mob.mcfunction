# TEMPORARY V2-H PROOF SUPPORT.
#
# Spawned LAST, after the village POIs are real and claimed, so the mob's own perception records a
# settlement it can actually anchor a relationship to. `/spmscavenger debug vrt2 setup` refuses when
# there is no anchor - the V2-G one-episode requirement cannot be proven without one.
#
# Inventory is NOT seeded here: the emerald count is E-4, and E only exists once the Toolsmith has
# generated its offers. A datapack cannot know E.
summon playermob:player_mob ~ ~ ~2 {CustomName:'"VRT2Mob"',PersistenceRequired:1b,Tags:["vrt2","vrt2_mob"]}
