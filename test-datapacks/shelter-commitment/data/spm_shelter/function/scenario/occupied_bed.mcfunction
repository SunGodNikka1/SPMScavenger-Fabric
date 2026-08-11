function spm_shelter:arena/build
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] run setblock ~8 ~1 ~ red_bed[facing=east,part=foot,occupied=true]
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] run setblock ~9 ~1 ~ red_bed[facing=east,part=head,occupied=true]
function spm_shelter:spawn/_summon
tellraw @s [{"text":"[SCR-1A] ","color":"gold"},{"text":"Occupied-bed scenario started. Watch one door pause, then entry to the covered interior; no reopen/close loop.","color":"aqua"}]
