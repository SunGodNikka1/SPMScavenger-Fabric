execute unless data storage spm_shelter:main initialized run function spm_shelter:_init_scoreboard
gamerule mobGriefing true
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set night
weather clear
tellraw @s [{"text":"[SPM Shelter] ","color":"gold"},{"text":"Setup complete. Stand on flat ground and run anchor/set.","color":"green"}]
