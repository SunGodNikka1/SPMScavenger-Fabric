# DOCUMENTATION ONLY — never invoked by the V4-G controller.
#
# V4FixtureCleanup owns synchronous cleanup through direct server APIs. It purges any legacy
# scheduled callback before new fixture creation and removes stale/exact-owned entities with
# Entity.discard(), never gameplay damage or a command function.
