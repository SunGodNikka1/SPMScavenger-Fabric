# TEMPORARY V2-H PROOF SUPPORT.
# Sterile gather prism. GatherResourcesGoal scans radius 20 with dy -4..4 and publishes exhaustion
# ONLY on zero pass-one candidates for the WHOLE combined intent. One log or one exposed ore
# anywhere in that prism yields CANDIDATES_ALL_REJECTED_PROTECTION instead, and the iron exhaustion
# this proof waits for never fires. So "no iron nearby" is not the precondition - "nothing the
# intent wants, anywhere in the prism" is. Cleared with a margin beyond the scan bounds.
fill ~-26 ~-6 ~-26 ~26 ~12 ~26 air replace #minecraft:logs
fill ~-26 ~-6 ~-26 ~26 ~12 ~26 air replace #minecraft:leaves
fill ~-26 ~-6 ~-26 ~26 ~12 ~26 air replace iron_ore
fill ~-26 ~-6 ~-26 ~26 ~12 ~26 air replace deepslate_iron_ore
fill ~-26 ~-6 ~-26 ~26 ~12 ~26 air replace coal_ore
fill ~-26 ~-6 ~-26 ~26 ~12 ~26 air replace deepslate_coal_ore
fill ~-26 ~-6 ~-26 ~26 ~12 ~26 air replace diamond_ore
fill ~-26 ~-6 ~-26 ~26 ~12 ~26 air replace deepslate_diamond_ore
