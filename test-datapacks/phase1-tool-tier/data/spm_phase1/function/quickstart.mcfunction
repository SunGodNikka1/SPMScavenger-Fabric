function spm_phase1:setup
execute as @p at @s run function spm_phase1:anchor/set
execute if entity @e[type=marker,tag=spm_p1_anchor,limit=1] at @e[type=marker,tag=spm_p1_anchor,limit=1] run function spm_phase1:arena/build
function spm_phase1:spawn/need_cobble
tellraw @s [{"text":"[SPM Phase1] ","color":"gold","bold":true},{"text":"Quickstart done — watch P1Test at anchor+5 stone.","color":"green"}]
