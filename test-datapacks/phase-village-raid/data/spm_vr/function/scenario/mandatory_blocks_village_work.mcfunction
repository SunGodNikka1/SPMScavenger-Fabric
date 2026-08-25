# VR-T3j — mandatory ownership blocks village work (no fake authority injection).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
setblock ~4 ~-1 ~4 minecraft:farmland
setblock ~4 ~ ~4 minecraft:wheat[age=7]
function spm_vr:_lib/spawn_ally
# The temporary campaign controller supplies the mandatory route inventory after subject discovery:
# stone pickaxe + two sticks + eight torches + diamond axe, with iron/raw iron absent.
fill ~3 ~-1 ~-2 ~13 ~-1 ~2 minecraft:grass_block
fill ~3 ~ ~-2 ~13 ~1 ~2 minecraft:air
setblock ~10 ~ ~ minecraft:iron_ore
setblock ~11 ~ ~ minecraft:iron_ore
setblock ~12 ~ ~ minecraft:iron_ore
tellraw @a [{"text":"[spm_vr] mandatory_blocks_village_work (VR-T3j) — policy-proven iron Gather route must own before village crop work","color":"gold"}]
