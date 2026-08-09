function spm_phase2:setup
execute as @p at @s run function spm_phase2:anchor/set
execute if entity @e[type=marker,tag=spm_p2_anchor,limit=1] at @e[type=marker,tag=spm_p2_anchor,limit=1] run function spm_phase2:arena/build
function spm_phase2:spawn/need_charcoal
tellraw @s [{"text":"[SPM Phase2] ","color":"aqua","bold":true},{"text":"Quickstart done — watch P2Test (RT-F1 charcoal).","color":"green"}]
