function spm_shelter:arena/build
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] run setblock ~8 ~1 ~ red_bed[facing=east,part=foot,occupied=false]
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] run setblock ~9 ~1 ~ red_bed[facing=east,part=head,occupied=false]
function spm_shelter:spawn/_summon
tellraw @s [{"text":"[SCR-1B] ","color":"gold"},{"text":"Free-bed scenario started. Watch one door pause, resumed approach, then sleeping.","color":"aqua"}]
