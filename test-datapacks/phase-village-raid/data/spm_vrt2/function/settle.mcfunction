# TEMPORARY V2-H PROOF SUPPORT. Run AFTER `quickstart`, once the merchants have stood by their
# workstations long enough for vanilla to claim the POIs.
#
# Fail-closed on the real signal: a naturally acquired job-site memory. Reading the Brain rather
# than waiting on a timer means the fixture cannot proceed on a village vanilla has not formed -
# and reading a memory is not authoring one.
tag @e[type=villager,tag=vrt2_merchant] remove vrt2_sited
execute as @e[type=villager,tag=vrt2_merchant] if data entity @s Brain.memories."minecraft:job_site" run tag @s add vrt2_sited

# Asserted POSITIVELY, per role. "no unsited merchant exists" is vacuously true when a merchant is
# missing - or when none were summoned at all - so the earlier form would have advanced a malformed
# fixture and burned a runtime attempt.
execute unless entity @e[type=villager,tag=vrt2_toolsmith,tag=vrt2_sited,limit=1] run say [VR-T2] settle REFUSED - no sited toolsmith. Wait for it to claim the smithing table, then run settle again.
execute unless entity @e[type=villager,tag=vrt2_fletcher,tag=vrt2_sited,limit=1] run say [VR-T2] settle REFUSED - no sited fletcher. Wait for it to claim the fletching table, then run settle again.
execute if entity @e[type=villager,tag=vrt2_toolsmith,tag=vrt2_sited,limit=1] if entity @e[type=villager,tag=vrt2_fletcher,tag=vrt2_sited,limit=1] run function spm_vrt2:_settled
