kill @e[tag=spm_v4.fixture]
schedule clear spm_v4:cleanup
tellraw @s [{"text":"[spm_v4] Exact tagged fixture entities removed; placed blocks preserved.","color":"yellow"}]
