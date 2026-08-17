# TEMPORARY V2-H PROOF SUPPORT. Safe to re-run: clears any previous fixture entities first, so a
# second quickstart cannot leave two toolsmiths in range (which /vrt2 setup would refuse anyway,
# but only after the arena had already been rebuilt around them).
kill @e[tag=vrt2]
function spm_vrt2:setup
function spm_vrt2:arena/_clear
function spm_vrt2:arena/build
function spm_vrt2:spawn/_merchants
say [VR-T2] arena built, merchants placed with AI beside their workstations.
say [VR-T2] next: /function spm_vrt2:pick_toolsmith  then wait for POI claim, then /function spm_vrt2:settle
