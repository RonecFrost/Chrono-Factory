# Chrono Factory 0.2.1-alpha
Minecraft 1.21.1 / NeoForge 21.1.268 / Java 21. Back up worlds and use a disposable test world.
- Actual custom player-model Echo entity, using the online owner's skin and slim/wide arm model when available. Default skin fallback. Translucent tint, intermittent limb desync, and particle manifestation.
- Right-click an Echo for current loop number, action index, reason, and inventory in chat. Right-click the watch to change the current or each saved timeline's tint (8 choices).
- New recordings capture ground pickup/drop with real quantities and item components. Old recordings lack those actions; re-record them.
- Echoes fade out and remanifest at the start, retaining actual inventory and tool durability between iterations. They do not walk backwards. Manual dismissal still drops actual items.
- No chunk loading, no precise GUI replay, no Paradox Machine, no arbitrary mod compatibility guarantee. Test inventory restoration, physical drop/pickup, skin rendering, and loop transitions.
- Existing armour stand proxies are removed as the corresponding saved loops load. Re-record older timelines if they freeze.

## 0.2.1 changes
- Movement shares tick budget across consecutive waypoints and newly recorded samples store segment timing. Old timelines remain readable.
- Client interpolation for gait, swing, pitch and fade. Full-limb flicker limited to stationary Echoes.
- Remove vanilla background blur from custom screen. Current and saved tint dropdowns support direct choice, click-away, Escape and arrow keys.
- Test straight-line walking, corners, mining swings, blocked movement, GUI text clarity and dropdowns on existing and new timelines.
