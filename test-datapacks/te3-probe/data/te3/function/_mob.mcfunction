# TEMPORARY V2-TE P0-3 PROBE SUPPORT.
# NoAI, like the fixture villagers. scenario -> index -> scan is a MANUAL sequence with ticks in
# between, and a live PlayerMob would move, craft, smelt or equip during them - changing the very
# inventory the scenario established. The demand assertion catches one class of mutation; freezing
# is what keeps the rest of the fixture inventory the fixture inventory.
#
# This is a market-state probe, not an autonomous-behaviour test, so an inert mob is the correct
# subject. Inventory and equipment remain fully inspectable.
summon playermob:player_mob ~ ~ ~2 {CustomName:'"TE3Mob"',PersistenceRequired:1b,NoAI:1b,Tags:["te3","te3_mob"]}
