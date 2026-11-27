# Chrono Factory 0.3.0-alpha
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

## 0.3.0
- Parallel default: break whatever is at the recorded block location, swing and continue on air, preserve saplings and wait for growth on first recorded log where a sapling is present, gather any nearby available items while moving, optional pickups and deposits; explicit withdrawals still require exact matching item/count, durability ignored.
- Strict: exact block, drops and quantities; recorded handler slots for transfers when identifiable, fallback to matching slots for legacy or ambiguous transfers. Not raw GUI replay.
- Complete inventory emptying into a container becomes a deposit-all action in parallel; strict retains the original actions. Re-record existing tree loops to capture new deposit-all and slot information. Mode can be selected before recording or changed on inactive current/saved timelines.
- Record vertical movement every tick, validate swept player-sized collision with axis-ordered ascent/descent. New obstructions still freeze. No unrestricted pathfinding or automatic tree search outside the recorded block positions.
- Test one-block jumps and descents, differently shaped trees, random drops, full output chest, missing sapling, strict mismatches and differing source slots. Backup world first.

- Air attacks while recording now send bounded swing samples. Parallel replays them against the actual ray target for the sampled duration, preserving saplings; strict expects empty space. New recordings only. Client packets are accepted only while a server-side recording is active.
- Track cursor-held items as part of the temporary inventory, and when needed take a verified vertical-first or horizontal-first ledge step toward the recorded goal. No collision bypass.
