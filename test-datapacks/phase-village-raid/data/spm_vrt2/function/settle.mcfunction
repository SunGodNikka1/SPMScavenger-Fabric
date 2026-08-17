# TEMPORARY V2-H PROOF SUPPORT. Run AFTER `quickstart`, once the merchants have stood by their
# workstations long enough for vanilla to claim the POIs (a few seconds of game time).
#
# Freeze the merchants only now. Freezing at summon time would stop them ever acquiring their
# workstation, and the village the mob perceives would be one vanilla never actually formed.
execute as @e[type=villager,tag=vrt2_merchant] run data merge entity @s {NoAI:1b}
say [VR-T2] merchants frozen. Spawning the PlayerMob.
function spm_vrt2:spawn/_mob
say [VR-T2] world fixture ready. Now run: /spmscavenger debug vrt2 setup
