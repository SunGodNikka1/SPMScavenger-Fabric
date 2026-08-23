# D-VR-084 witness — real Gather publisher acquires a claim (no injected authority).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
function spm_vr:_lib/spawn_ally
# Remove torches; place gather targets so GatherResourcesGoal publishes a real claim.
item replace entity @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] container.0 with minecraft:air
fill ~11 ~ ~11 ~13 ~2 ~13 minecraft:oak_log replace air
setblock ~4 ~-1 ~4 minecraft:farmland
setblock ~4 ~ ~4 minecraft:wheat[age=7]
tellraw @a [{"text":"[spm_vr] mandatory_ownership_witness (D-VR-084) — observe real gather claim; village work must defer","color":"gold"}]
