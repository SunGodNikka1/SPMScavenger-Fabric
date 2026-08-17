# TEMPORARY V2-H PROOF SUPPORT.
# Promote exactly one, then remove the rest so `/vrt2 setup` sees a single unambiguous Toolsmith.
tag @e[type=villager,tag=vrt2_has_route,limit=1,sort=nearest] add vrt2_toolsmith
tag @e[type=villager,tag=vrt2_toolsmith] add vrt2_merchant
tag @e[type=villager,tag=vrt2_toolsmith] remove vrt2_smith_candidate
kill @e[type=villager,tag=vrt2_smith_candidate]
say [VR-T2] toolsmith selected on route presence; unselected candidates removed.
