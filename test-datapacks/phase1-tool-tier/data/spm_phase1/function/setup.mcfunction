execute unless data storage spm_phase1:main initialized run function spm_phase1:_init_scoreboard
gamerule mobGriefing true
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set day
weather clear
tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"Setup OK. Stand on arena center → anchor/set → arena/build","color":"green"}]
