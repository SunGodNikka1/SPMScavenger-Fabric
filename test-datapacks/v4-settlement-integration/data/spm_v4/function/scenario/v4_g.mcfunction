# V4-G — single village, pre-home REQUIRED_TRADE return followed by first-home sleep.
function spm_v4:cleanup
gamerule mobGriefing true
weather clear
time set 18000

# Village end: clear the real Gather scan volume and provide a stable floor.
fill ~-24 ~-4 ~-24 ~24 ~4 ~24 minecraft:air
fill ~-24 ~-1 ~-24 ~24 ~-1 ~24 minecraft:stone

# Flat, unobstructed 180-block COMMUTE corridor and departure scan volume.
fill ~0 ~-1 ~-2 ~180 ~-1 ~2 minecraft:stone
fill ~0 ~ ~-2 ~180 ~3 ~2 minecraft:air
fill ~158 ~-4 ~-22 ~202 ~4 ~22 minecraft:air
fill ~158 ~-1 ~-22 ~202 ~-1 ~22 minecraft:stone

# Village POIs and three reachable beds. Helper AI may claim one; at least one remains for Phase B.
setblock ~1 ~ ~0 minecraft:bell
setblock ~-4 ~ ~1 minecraft:red_bed[part=head,facing=south]
setblock ~-4 ~ ~2 minecraft:red_bed[part=foot,facing=south]
setblock ~-7 ~ ~1 minecraft:blue_bed[part=head,facing=south]
setblock ~-7 ~ ~2 minecraft:blue_bed[part=foot,facing=south]
setblock ~-10 ~ ~1 minecraft:white_bed[part=head,facing=south]
setblock ~-10 ~ ~2 minecraft:white_bed[part=foot,facing=south]
setblock ~-1 ~ ~4 minecraft:smithing_table

summon playermob:player_mob ~2 ~ ~ {Tags:["spm_v4.fixture","spm_v4.subject"],PersistenceRequired:1b}
summon minecraft:villager ~-1 ~ ~ {Tags:["spm_v4.fixture","spm_v4.trader"],PersistenceRequired:1b,Age:0}
summon minecraft:villager ~-7 ~ ~1 {Tags:["spm_v4.fixture","spm_v4.helper"],PersistenceRequired:1b,Age:0}

tellraw @a [{"text":"[spm_v4] V4-G fixture created; validation controller owns the remaining preflight.","color":"gold"}]
