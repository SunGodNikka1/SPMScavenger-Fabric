# TEMPORARY V2-H PROOF SUPPORT. Run AFTER `quickstart`, once the merchants have stood by their
# workstations long enough for vanilla to claim the POIs.
#
# Fail-closed on the real thing: a naturally acquired job-site memory. Checking the Brain rather
# than a timer means the fixture cannot proceed on a village vanilla has not actually formed - and
# reading the memory is not authoring one.
tag @e[type=villager,tag=vrt2_merchant] remove vrt2_sited
execute as @e[type=villager,tag=vrt2_merchant] if data entity @s Brain.memories."minecraft:job_site" run tag @s add vrt2_sited

execute if entity @e[type=villager,tag=vrt2_merchant,tag=!vrt2_sited] run say [VR-T2] settle REFUSED - a merchant has not claimed its workstation yet. Wait and run settle again.
execute unless entity @e[type=villager,tag=vrt2_merchant,tag=!vrt2_sited] run function spm_vrt2:_settled
