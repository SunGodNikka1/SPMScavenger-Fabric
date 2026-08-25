# D-VR-084 witness — real Gather publisher acquires a claim (no injected authority).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
function spm_vr:_lib/spawn_ally
# The temporary campaign controller supplies the same mandatory route inventory as VR-T3j.
fill ~3 ~-1 ~-2 ~13 ~-1 ~2 minecraft:grass_block
fill ~3 ~ ~-2 ~13 ~1 ~2 minecraft:air
setblock ~10 ~ ~ minecraft:iron_ore
setblock ~11 ~ ~ minecraft:iron_ore
setblock ~12 ~ ~ minecraft:iron_ore
setblock ~4 ~-1 ~4 minecraft:farmland
setblock ~4 ~ ~4 minecraft:wheat[age=7]
tellraw @a [{"text":"[spm_vr] mandatory_ownership_witness (D-VR-084) — policy-proven iron Gather route must publish real ownership","color":"gold"}]
