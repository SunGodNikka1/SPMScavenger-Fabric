execute unless data storage spm_phase2:main initialized run function spm_phase2:_init_scoreboard
gamerule mobGriefing true
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set day
weather clear
tellraw @s [{"text":"[SPM Phase2] ","color":"aqua"},{"text":"Setup OK. Stand on arena center → anchor/set → arena/build","color":"green"}]
