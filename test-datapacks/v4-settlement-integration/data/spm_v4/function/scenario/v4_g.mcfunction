# DOCUMENTATION ONLY — not invoked by the V4-G controller.
#
# V4FixtureGeometryBuilder is the sole runtime owner of V4-G geometry. It forces the exact
# required chunk set, performs direct server-level mutations, verifies the subject/trader/helper
# spawn spaces, village/corridor/departure samples, bell, six bed halves, and workstation, and only
# then permits checked fixture entity creation. Keeping this resource documents the old command
# entry point without retaining a second unchecked geometry owner.
