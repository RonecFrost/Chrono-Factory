# Chrono Factory 0.1.0-alpha
Experimental first implementation for Minecraft 1.21.1 NeoForge 21.1.268. Use a disposable world, back up saves. Both client and server need the mod.
- Left-click the Timekeeper to record, finish, manifest, dismiss. Right-click in air opens the management screen. Creative watch is in Tools & Utilities.
- Recording swaps the ordinary inventory for a temporary one and restores it on finishing/logout/death. Creative recording temporarily changes to survival. The real demonstration consumes real resources.
- Demonstrate withdrawing a tool from a chest, walking to a block, mining, depositing resources, returning the tool, and returning to the start. Refill chest before testing. Return to the start yourself at the end of the demonstration.
- Echoes use a distinct simulated survival player; item type and components must match, except damage. Actual tool damage persists. Recordings and active inventory are stored with the world/watch.
- No force loading, only runs with the owner online and watch in inventory. No arbitrary GUI replay, offhand replay, advanced player-skin renderer, full permission integration, or portable generator verification yet. Visual proxy is a named armor stand. Creative timeline menu supports up to 20 saved timelines. Temporary inventory recovery and item transfers need extensive testing. Avoid protected worlds and valuable equipment.
- This is source code, not a verified gameplay release. GitHub Actions compiles a JAR and publishes it as a prerelease only after a successful build.
