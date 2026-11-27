# Chrono Factory 0.7.2-alpha
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

## 0.5.1 ownership, multi-Relay, inventory and cleanup
- Watch data is per physical item. Duplicate IDs in player inventory are re-keyed; actual existing local recordings are preserved as independent snapshots with new timeline IDs. Newly obtained blank watches remain blank. Online owner losing a non-docked watch dismisses its Echoes. Active timelines cannot be cleared/deleted until dismissal completes; dismissal preserves timeline even if retain is off for safety.
- Creative Relay supports selecting multiple timeline rows; each selected timeline has independent repeat count. Rising edge arms each selected job; falling edge stops only infinite jobs. Finite jobs finish independently. Eject/stop cancels all. Selection changes while idle only. Existing single-selection Relay migrates.
- Owner/admin right-click opens a real two-way inventory container with player inventory and Echo inventory. Normal/shift clicks transfer real items, Echo pauses during access, only one viewer. No watches in Echo inventory. Cursor used by internal Input replay is not editable. Closing resumes.
- /chronofactory dismissall dismisses caller's Echoes in all dimensions; /chronofactory dismissall all requires operator permission and dismisses everyone's. Loaded orphan entities are cleaned; unloaded tracked echoes dismiss when loaded. Pickup search uses 1.5 horizontal and 0.75 vertical block expansion beyond the body bounds: 150% of vanilla player pickup expansion.
- Test in disposable world: duplicate watches, independent names, multiple selected timelines with mixed finite/infinite, transfer to/from Echo and closing, active delete rejection, emergency dismissal. CI cannot verify gameplay.

## 0.6.0 empty remanifestation and crafting
- At all cycle boundaries remaining actual inventory, equipment and Input cursor/menu contents drop at the ending position. New manifestation begins empty; delayed start chunk does not cause repeated drops. Both playback modes. Existing timelines use this rule.
- Adaptive records successful crafting output takes from vanilla 2x2/3x3 crafting menus and stonecutters, comparing real inventory+grid+cursor changes to the live recipe. Shift-click batches are counted by net recipe changes. Playback checks recipe ID, actual ingredients and result, then stages consumption, output and container remainders atomically. Missing materials or inventory space stalls. Standard modded recipes using these menus work in principle; custom GUI, non-deterministic/special recipes, crafting hooks and recipe-book auto-placement are not guaranteed. Re-record crafting timelines. Input retains actual slot clicks, now also captures menu buttons (stonecutter).
- Furnace, smoker and blast furnace use native input/fuel/output slots, no bulk dump or overflow to unrelated slots; output must exist before withdrawal. Other item-handler machines retain existing transfer mechanics. Re-record furnace timelines. Test regular crafting, shift-crafting, buckets/remainders, stonecutting, furnace fuel/input/output, no-input stasis, full inventory, repeated remanifestation, and modded recipes in a disposable world.

- Furnace transactions are captured per server click to distinguish player transfers from smelting activity occurring during the visit. Input personal crafting grid is cleared through native menu cleanup on closing and at loop boundaries.

## 0.7.0 experimental combat and interactions
- Death saves recording and preserves only the recording watch on respawn; other gear uses vanilla death rules. New main-hand air-use action and held item durations, native projectile use, nearby XP orbs, entity attacks; Input support is preliminary (held bow timing especially needs testing). XP reset between manifestations. Echoes still invulnerable and not independent combat AI.
- Optional Ars bridge matches spellbooks by item/tier, records changed spell slots from server book metadata, edits only those slots, and casts the current spell in recorded slot. Mana uses Ars API where available; no hard dependency. No guarantee of every glyph, add-on, or fake-player compatibility. Main hand only. Re-record.
- Adaptive generic server-menu transactions with no exposed storage record net ingredients/results/XP and attempt native menu clicks with item matching; actual output is computed by workstation. Enchanting random offers can vary. Custom client-only GUIs (including Rechiseled) and non-inventory resource costs require specific adapters. Persistent inventories still use storage transfers. No arbitrary delta-to-item generation.
- Test in disposable world; compile does not establish gameplay compatibility.

- Input air-use holds now track elapsed charge ticks separately from the fake player's transient item stack; release uses real current stack. Actual entity attack events are captured. These combat paths remain experimental.

## 0.7.1 action timing, release aim, enchanting and Ars fixes
- Adaptive preserves recorded intervals between item-use attempts (including held RMB throwing). Input uses actual recorded item/block use events rather than inventing extra clicks for new recordings. Native release packet records bow draw length and firing yaw/pitch; crossbow shot/throw uses the actual use-event angle. Native projectile spread remains. Drops record actual velocity, angle and quantity in both modes. Re-record these timelines. Native cooldowns tick for Echoes.
- Arrow pickup invokes native arrow playerTouch for pickup-allowed nearby arrows, honoring grounded/shake and inventory restrictions. Infinity/creative-only and nonrecoverable arrows remain nonrecoverable.
- Dedicated Adaptive enchanting records a successful option 1/2/3, minimum level shown and actual equipment. Waits for recorded/current required level and lapis; calls real menu option, pays native costs, keeps real randomized output. Input continues replaying actual button selection. Re-record enchantments.
- Ars spell tier is a class with value field, not enum; fixed mana initialization. Spell use calls native caster directly, with explicit slot validity and mana diagnostics. Actual book metadata stays intact; no guarantee of glyph or addon fake-player support.
- Test in disposable world: throw five items with held RMB, record turning during bow draw, crossbow load/fire, arrow recovery, enchant each row with insufficient/sufficient levels, preconfigured and edited Ars books. Build is not gameplay verification.

- Adaptive arrow recovery also runs while stationary. For a non-bulk deposit after an enchantment, the recording's observed output is resolved to the actual newly enchanted stack for that cycle. Bulk deposits still empty actual inventory and may use overflow placement if the recorded enchantment differs. Old block-use spell actions can fall back to the native caster after a rejected block interaction.

## 0.7.2 timing, workstations, offhand and speech
- Adaptive block uses now preserve time between distinct target interactions, including pillar building. Crossbow direction synchronizes head/body/current/previous rotations before native use. Re-record.
- Anvil and grindstone record individual successful result-slot transactions (actual input quantities, output, name, level cost). Replay uses real workstation slots and native result take, checks legal result before spending levels. Multi-stage repair/enchant is recorded as separate transactions. Generic menus retain exact before/after ledger and native click path; arbitrary recorded outputs are not fabricated. Re-record. Unnamed enchanted damageable gear matches by item and enchanted status; named equipment requires name and enchanted books retain exact enchantments. Native results vary with actual inputs.
- F swap and main/offhand use events recorded; renderer shows both hands and chooses swing arm, shield pose experimental. Echo inventory uses opaque contrasting slots. Chat records messages, Adaptive pauses briefly then emits labelled system chat; Input uses recorded event timing. Commands execute at Echo via fake-player source with current online owner's permissions, no command execution offline, no management-command recording. Some commands requiring a real connected player may fail. Re-record all new actions.
