# Chrono Factory 0.5.0-alpha
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

## 0.3.1 held inputs
- New recordings store held right-click duration in server ticks and actual repeated block interaction offsets. Replays preserve the timing (not extra fabricated clicks) in Strict and Parallel. Missing targets/tools pause the active clock. Mining holds have a minimum recorded duration when start/break events are available. Air-swing final sample is shortened when released.
- Test Create hand crank: record holding right-click continuously for 20 seconds, release, then record another action. Right-click Echo to inspect elapsed/total time and interaction count. Check both modes and brief taps. Re-record old timelines to capture duration; old recordings still load as one-tick uses.
- No generic in-air held item use or simultaneous movement/use playback yet. Server tick time slows if TPS drops. Create has its own short spin-down time. No hard Create dependency.

## 0.4.0 Strict input engine
- Strict uses per-server-tick movement, look, hotbar, attack, use, jump, sprint, sneak, drop and screen input samples. No semantic prerequisite waiting. GUI slot clicks use the real server menu and current stack; a wrong or empty slot is never corrected. New recordings contain both streams. Re-record any older timeline used in Strict.
- Parallel retains semantic matching; crank/valve holds replay at four-tick intervals over the measured duration. Re-record crank loops. Verify both modes in-game.
- Strict movement is collision/gravity-aware, approximate vanilla physics; not a perfect client simulation. Entity attacks, offhand swapping, custom GUI packets/buttons, and exact item-use progression are not implemented. Recorded ordinary container clicks are replayed server-side. Menu state across unload/restart needs further testing. Input capture limited to 12,000 ticks (~10 minutes at 20 TPS).
- Releases include version, build number and UTC build date. Use disposable test worlds.

## 0.4.1 transport reliability
- Input (formerly Strict): record actual per-tick movement deltas, replay with vanilla collision resolution, no acceleration or catch-up. A wall consumes recorded movement time; later movement is relative to current position. New recordings required for Input. Actual block-use events and container click/close packets recorded in one ordered stream, including exact menu slot and selected hotbar slot. Clicks act on current actual contents. Shift-click follows real menu routing. No exact custom GUI button packet replay.
- Adaptive (formerly Parallel): withdrawals search matching source slots; deposits start at recorded destination slot then advance and wrap. Empty-inventory bulk deposits first reserve recorded destinations for matching items, then spill over; extra collected items use remaining space. Capture transfer deltas after individual GUI clicks. New recordings recommended for accurate layout. Existing Adaptive recordings still load.
- Right-click Echo for live, read-only inventory inspection and task, status, cycle, progress, armor/offhand and cursor. Updates twice per second within 8 blocks; no transfer access yet.
- Test single vanilla chest source and destination first, manual click transfer into bottom-right slot and repeat with full slot, then multiple wood types. Input: change source item and verify exact destination, remove source item and verify clock continues; block the path and verify no overshoot. Back up worlds.

## 0.4.2 net transfer quantity and names
- Adaptive records the net quantity moved during a chest visit, not transient cursor pickups. A source stack of 5 reduced to 4 records 1. Matching quantity may be spread across source slots. Wait only if fewer than the outstanding required quantity are extractable or inventory space is inadequate. Destination preferences retained. Same-item rearrangements within one chest with zero net movement are not Adaptive transport; use Input for those. Re-record affected old timelines.
- Click the current timeline name or a saved timeline's name to rename it (48 chars). Renaming active timelines updates their world state without restarting. Inspector displays recorded transfer quantity and availability.
- Regression checks cover net transfer allocation, not actual Minecraft GUI or third-party shift-scroll behavior. Test vanilla split and Mouse Tweaks shift-scroll in a disposable world, with source counts 5,4,3,2,1,0 and replenishment.

## 0.5.0 finite repeats and Temporal Relay
- Repeat settings: infinite by default (including existing timelines) or 1–9999 completed cycles, per current/saved timeline. Change while inactive. Final loop dissolves and drops remaining real inventory, recording stays intact. No need to re-record for repeat settings.
- Right-click Temporal Relay with a watch to insert it; empty hand opens management. Owner/admin controls ejection, selection, repeat count and cancellation. One selected timeline per Relay, including creative saved timelines. Inserting a watch with an active Echo is rejected.
- Finite run starts on rising redstone edge and finishes despite signal falling. Constant signal does not repeatedly start finite runs; busy pulses ignored. Infinite run requires sustained redstone; falling edge cancels and drops actual inventory. New rising edge starts a new job. Stop and eject cancel jobs. Normal docked watches accept external FE (no passive recharge while docked), creative ignore FE; power loss stalls docked jobs. Redstone is a control signal, not FE. No chunk loading, same dimension as recording, owner can be offline while chunks remain loaded.
- Test once, five, infinite, finite pulse and sustained signal, ejection/break, power depletion/recharge, save/reload, creative selection. Relay texture uses vanilla crying obsidian and lamp faces initially. No gameplay verification by CI.
