# Configuration

`mods/IroriPowered_Refixes/config.json` holds `Early` settings for the early patches and `Runtime` settings for the plugin. Shared rules are in [Settings files](/mod/refixes/installing-refixes#settings-files).

For Unknown Block Cleaner, AI Tick Throttler, Idle Player Handler and Idle World Pause, the other options apply only when `Enabled` is `true`.

## Early Patches
Section: `Early`

Each value needs its patch:
- `MaxSections` keys and `UnloadDistanceOffset`: `Mixins.Tuning.ChunkStreaming`.
- `VanillaKeepSpawnLoaded`: `Mixins.Features.OriginRetention`.
- Per-search pathfinding keys: `Mixins.Tuning.PathfindingLimits`.
- Per-tick pathfinding keys: `Mixins.Experimental.PathfindingBudget`.

| Key | Default | Description |
|-----|---------|-------------|
| `MaxSectionsPerSecond` | `360` | Chunk sections per second for a remote player. |
| `MaxSectionsPerSecondLan` | `1280` | Sections per second for a LAN player. |
| `MaxSectionsPerSecondLocal` | `2560` | Sections per second for a local player. |
| `MaxSectionsPerTick` | `40` | Sections per player per tick. |
| `VanillaKeepSpawnLoaded` | `true` | Keeps the chunk column at X 0, Z 0 loaded. |
| `UnloadDistanceOffset` | `4` | Chunks beyond the view radius that stay in the minimum loaded radius. |
| `PathfindingMaxPathLength` | `200` | Nodes per NPC path. |
| `PathfindingOpenNodesLimit` | `80` | Open nodes per search. |
| `PathfindingTotalNodesLimit` | `400` | Discovered nodes per search. |
| `PathfindingMaxNewSearchesPerTick` | `8` | New searches per world tick. |
| `PathfindingMaxNodeExpansionsPerTick` | `600` | A* expansions per world tick. |
| `ShutdownSaveTimeoutSeconds` | `10` | Shutdown save timeout in seconds. |

At `0` or below, `MaxSections` keys stop sending new sections, per-search limits fall back to the NPC role's limit, new searches stop except for exempt NPCs and expansions are unlimited. Per-search limits never raise the role's limit. What the minimum loaded radius controls depends on the server version, see [Differences on Hytale 0.8](/mod/refixes/installing-refixes#differences-on-hytale-08).

`ShutdownSaveTimeoutSeconds` is read by these patches:
- `Mixins.Recovery.ShutdownSaveTimeout` limits the store resource save, world config save and indexed storage close at shutdown, not chunk saves. The indexed storage close is limited only while `Mixins.Correctness.StorageOrdering` is on as well. `0` or below means no limit.
- **Hytale 0.7 only.** `Mixins.Correctness.WorldTransfer` runs an extra cleanup save, without time limit, when a world stops, even with `ShutdownSaveTimeout` off. `0` or below skips it.
- **Hytale 0.7 only.** `Mixins.Correctness.WorldEventSaving` limits the wait for the last world events save at shutdown. `0` or below skips the wait.

### Outgoing packet queues
Section: `Early`

Requires `Mixins.Correctness.OutgoingQueueLimits`. Limits apply per connection on Netty QUIC and Quiche. A write over a limit disconnects the player.

| Key | Default | Description |
|-----|---------|-------------|
| `OutgoingQueueMaxBytes` | `67108864` | Estimated queued packet data in bytes, 64 MiB. |
| `OutgoingQueueMaxPackets` | `65536` | Queued packets. |
| `OutgoingQueueBroadcastWaitMs` | `120000` | Milliseconds an asset broadcast waits per asset for a player's queue to drain. |
| `OutgoingQueueBroadcastStallMs` | `30000` | Milliseconds a broadcast waits for a player who reads nothing. |

`0` turns off that limit, wait or stall check. A wait of `0` also makes asset pack broadcasts count against the limits. Invalid values stop startup even with the switch off.

The limits stop runaway backlogs and are not a speed setting. Raising them uses more memory.

A player who stops reading delays an asset reload broadcast for the others by about the stall time. Asset pack additions and removals never wait and bypass the limits.

### Cylinder Visibility
Section: `Early.CylinderVisibility`

Requires `Mixins.Tuning.CylinderVisibility`.

| Key | Default | Description |
|-----|---------|-------------|
| `HeightMultiplier` | `2.0` | Vertical visibility as a multiple of the view radius. Below `2.0` can hide entities vanilla shows. |

## Unknown Block Cleaner
Section: `Runtime.Recovery.UnknownBlockCleaner`

Risk: Removal is permanent. Back up the world and check `Exclude` first. Only sections loaded while the cleaner is on are scanned.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Removes unknown blocks from loading chunk sections. |
| `ScanFluids` | `false` | Also removes unknown fluids. |
| `ScanContainers` | `false` | Also removes unknown container items. |
| `ScanPlayerInventories` | `false` | Also removes unknown items from players entering a world. |
| `Exclude` | `[]` | Case-sensitive ID prefixes to keep, such as `dynamicseasons:`. |
| `BudgetMs` | `10` | Milliseconds per pass, checked between sections. Minimum 1. |
| `IntervalMs` | `50` | Milliseconds between passes. Minimum 20. |

## Correctness
Section: `Runtime.Correctness`

| Key | Default | Description |
|-----|---------|-------------|
| `CraftingManager` | `true` | Clears stale crafting bench bindings. |

## Tuning

### Entity Despawn Timer
Section: `Runtime.Tuning.EntityDespawnTimer`

Runs only while `AddTimerWhenMissing` or `OverrideExistingTimers` is `true`, for items, loose block entities and projectiles as they spawn. Items that cannot be picked up are skipped. `0` means permanent.

| Key | Default | Description |
|-----|---------|-------------|
| `ItemDespawnSeconds` | `300` | Dropped item lifetime in seconds. |
| `BlockEntityDespawnSeconds` | `300` | Loose block entity lifetime in seconds. |
| `ProjectileDespawnSeconds` | `60` | Projectile lifetime in seconds. |
| `AddTimerWhenMissing` | `false` | Adds timers to entities without one, which are normally permanent. |
| `OverrideExistingTimers` | `false` | Replaces existing timers. |

### AI Tick Throttler
Section: `Runtime.Tuning.AiTickThrottler`

Requires `Mixins.Compatibility.AiTickThrottler`. Distant NPCs tick less often, except NPCs in or after combat, changing AI state or targeting a player. Throttle freezes are not saved. A chunk is 32 blocks.

Risk: An NPC that another plugin freezes while throttled stays frozen, and that freeze is saved.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Turns the throttler on. |
| `UpdateIntervalMs` | `150` | Milliseconds between scans. Keep at or below the longest tick interval. Minimum 20. |
| `MaxCycleMs` | `30` | Milliseconds per scan for new freezes. `0` is unlimited. |
| `ScanShards` | `1` | Scans that new freezes are spread across. |
| `NearChunks` | `2` | Full tick rate within this many chunks. |
| `MidChunks` | `4` | Mid tick rate within this many chunks. |
| `FarChunks` | `6` | Far tick rate within this many chunks. |
| `MidTickSeconds` | `0.2` | Seconds between mid band ticks. |
| `FarTickSeconds` | `0.5` | Seconds between far band ticks. |
| `VeryFarTickSeconds` | `1.0` | Seconds between ticks beyond the far band. |
| `MinTickSeconds` | `0.05` | Shortest throttled interval in seconds. The scan interval is also a floor. |
| `MaxStepSeconds` | `1.0` | Longest simulated step in seconds. `0` is uncapped. Lower values slow NPCs. |
| `ActivationHysteresisChunks` | `0` | Extra chunks past a band before an NPC is first throttled. |
| `MaxUnfreezesPerTick` | `10` | NPCs resumed per scan. Newly exempt NPCs are not capped. |
| `MaxFreezesPerTick` | `20` | New freezes per scan. |
| `ThrottleExcludedNpcTypes` | `[]` | NPC types never throttled. |
| `ThrottleExcludeMounts` | `true` | Exempts ridden entities. |
| `ThrottleExcludeFlying` | `true` | Exempts flying NPCs. |
| `ThrottleExcludeAirborneOrDead` | `true` | Exempts airborne, falling or dead NPCs. |
| `StepWithoutPlayers` | `false` | Keeps NPCs stepping in empty worlds. Otherwise they freeze at once, ignoring the per-scan limits. |

### Per Player Hot Radius
Section: `Runtime.Tuning.PerPlayerHotRadius`

Requires `Mixins.Compatibility.PlayerRadius`. Scales the ticking chunk radius with world TPS. The memory guard also needs `Mixins.Compatibility.ServerViewRadius` and lowers the server `MaxViewRadius`. A `mods/IroriPowered_Refixes/viewradius-restore` file present at startup sets `MaxViewRadius` and is deleted, even with the service off.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Turns the service on. |
| `CheckIntervalMs` | `5000` | Milliseconds between adjustments. Minimum 1000. |
| `MinRadius` | `2` | Smallest hot chunk radius. |
| `MaxRadius` | `8` | Largest hot chunk radius. |
| `TPSLowFraction` | `0.75` | TPS fraction that gives the smallest radius. |
| `TPSHighFraction` | `0.90` | TPS fraction that gives the largest radius. |
| `MemoryGuardEnabled` | `false` | Lowers the view radius under memory pressure until it recovers. |
| `MemoryHeapThreshold` | `0.85` | Heap usage after GC that counts as pressure. |
| `MemoryMinViewRadius` | `2` | Lowest view radius the guard sets. |
| `MemoryViewDecreaseFactor` | `0.75` | View radius multiplier per step down. |
| `MemoryRecoveryWaitSeconds` | `60` | Seconds without pressure per step back up. |

### Idle Player Handler
Section: `Runtime.Tuning.IdlePlayerHandler`

Requires `Mixins.Compatibility.PlayerRadius`. Lowers radii for idle players. Walking or turning the camera resets the idle timer.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Turns the handler on. |
| `IdleTimeoutSeconds` | `90` | Seconds before a player is idle. Minimum 30. |
| `CheckIntervalSeconds` | `10` | Seconds between checks. Minimum 5. |
| `ReduceViewRadius` | `true` | Lowers the view radius. |
| `IdleViewRadius` | `4` | Idle view radius. Minimum 2. |
| `ReduceHotRadius` | `true` | Lowers the hot radius. |
| `IdleHotRadius` | `3` | Idle hot radius. Minimum 2. |
| `ReduceMinLoadedRadius` | `true` | Lowers the minimum loaded radius. |
| `IdleMinLoadedRadius` | `2` | Idle minimum loaded radius. Minimum 2. |
| `MovementThreshold` | `0.5` | Blocks moved that reset the timer. |

### Idle World Pause
Section: `Runtime.Tuning.IdleWorldPause`

Requires `Mixins.Compatibility.IdleWorldPause`. Worlds with chunk loaders stay running while `Runtime.Features.ChunkLoader.Enabled` is `true`. Stopping the service undoes only its own pauses.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Pauses empty worlds, including the default world and instances. |
| `CheckIntervalMs` | `10000` | Milliseconds between checks. Minimum 1000. |
| `ExcludedWorlds` | `[]` | Worlds never paused. Not case-sensitive. |

## Chunk Loader
Section: `Runtime.Features.ChunkLoader`

A chunk loader keeps its column and the section set with `/chunkloader add` loaded and ticking. **Hytale 0.7:** also other loaded sections of that column. **Hytale 0.8:** also sections 0 to 9.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Enables `/chunkloader` and saved chunk loaders. |

| Command | Permission | Use |
|---------|------------|-----|
| `/chunkloader add` | `hytale.command.refixes.chunkloader.add` | Adds a loader at your position or at `x` and `z`, with optional `y`, `label` and `world`. `--here` adds every chunk of your `/pos1` and `/pos2` selection. |
| `/chunkloader remove` | `hytale.command.refixes.chunkloader.remove` | Removes the loader at your position, at `x` and `z` or with `label`. `--here` removes every loader in your selection. |
| `/chunkloader list` | `hytale.command.refixes.chunkloader.list` | Lists the loaders in your world or in `world`. |

## Shared Instance Worlds
Section: `Runtime.Features.SharedInstances`

Requires `Mixins.Features.SharedInstances`. Explicit names and instance keys take precedence.

Risk: Sharing can change consumable dungeon behavior. `ResetOnEmpty` permanently deletes shared world data, so back up first.

| Key | Default | Description |
|-----|---------|-------------|
| `ExcludedPrefixes` | `[]` | Case-sensitive instance asset name prefixes never shared. An empty entry excludes all. |
| `ResetOnEmpty` | `false` | Deletes a shared world's data once it is removed for being empty. Without `Mixins.Features.SharedInstanceStorage`, Hytale's own instance removal decides instead. |

`ResetOnEmpty` only deletes worlds named `instance-shared-...` that had players this run, are now empty and have `WorldEmptyCondition` in their removal rules. Crashed worlds and server shutdown are excluded.

## Compatibility
Section: `Runtime.Compatibility`

| Key | Default | Description |
|-----|---------|-------------|
| `ExistingSharedInstances` | `true` | Keeps existing shared worlds. With `false`, Hytale can delete them. |

Without `Mixins.Features.SharedInstanceStorage` and with `ResetOnEmpty` off, Refixes only keeps the folders of removed shared worlds and logs a warning. Hytale still deletes worlds whose saved config has `DeleteOnUniverseStart` set.

### Saved Throttler Cleanup
Section: `Runtime.Compatibility.TickThrottledCleanup`

Clears saved Refixes freezes when entities load, even with Refixes patches off.

Risk: If you remove Refixes, NPCs frozen by an older Refixes stay frozen in chunks not saved since. See [AI-throttled NPCs](#ai-throttled-npcs).

| Key | Default | Description |
|-----|---------|-------------|
| `CleanupFrozenEntities` | `true` | Clears freezes marked by Refixes. |
| `CleanupExcludedNpcTypes` | `[]` | NPC types skipped by marked cleanup. |
| `LegacyCleanup` | `false` | Also clears unmarked freezes from any source, on any non-player entity. |
| `LegacyCleanupExcludedNpcTypes` | `[]` | NPC types skipped by legacy cleanup. Other entities cannot be excluded. |

### AI-throttled NPCs

Refixes before 0.6.0 saved throttle freezes, so older saves can contain frozen NPCs. Keep Refixes installed with `Runtime.Compatibility.TickThrottledCleanup.CleanupFrozenEntities` at its default `true` until the affected chunks have loaded and saved once.

## Watchdog Diagnostics
Section: `Runtime.Diagnostics.Watchdog`

Reports world stalls and crashes. Worlds other than the default world are checked only with `Runtime.Recovery.Watchdog.AutoRestartWorlds`. The watchdog runs while `Enabled`, `ShutdownOnDefaultWorldCrash`, `AutoRestartWorlds` or `HaltOnShutdownTimeout` is `true`.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Adds reports with thread dumps. |
| `DumpAllThreads` | `false` | Dumps all JVM threads, not just world threads. |
| `ActivationDelayMs` | `10000` | Milliseconds after all worlds load before checks start. Checks run every 5 seconds. |
| `ThreadTimeoutMs` | `30000` | Milliseconds without response before a world counts as stalled. |

## Watchdog Recovery
Section: `Runtime.Recovery.Watchdog`

Automatic restart needs `Mixins.Compatibility.Watchdog` or `Mixins.Correctness.WorldOwnership`. Default world monitoring needs no patch.

Only worlds that stopped or reject tasks restart. Stalled worlds are only logged. After 5 failed attempts, or a failed save check, a world is skipped until the server restarts.

Safety: No restart during shutdown or for removed worlds. A registered world restarts only after its save or shutdown succeeds within `RestartSaveTimeoutMs`. An unregistered world reloads without this check.

Risk: `HaltOnShutdownTimeout` exits with code 1, skipping remaining saves and shutdown hooks. Unsaved data can be lost.

| Key | Default | Description |
|-----|---------|-------------|
| `ShutdownOnDefaultWorldCrash` | `true` | Shuts down when the default world stops, stalls for `ThreadTimeoutMs` or rejects tasks. |
| `AutoRestartWorlds` | `false` | Restarts crashed worlds. |
| `HaltOnShutdownTimeout` | `false` | Forces exit when shutdown exceeds `ShutdownTimeoutMs`. |
| `ShutdownTimeoutMs` | `60000` | Milliseconds before forced exit. |
| `RestartSaveTimeoutMs` | `15000` | Milliseconds for the save before a restart. Minimum 1. |
| `AutoRestartingWorldFilter` | `[]` | Exact world names to restart. |

With an empty filter, non-instance worlds restart, except the default world while `ShutdownOnDefaultWorldCrash` is `true`. A nonempty filter restarts only the listed worlds, instances and the default world included, and also loads a listed world that is not running if its save exists.

## Flight Recorder Events
Section: `Runtime.Diagnostics`

| Key | Default | Description |
|-----|---------|-------------|
| `JfrEvents` | `true` | Records Refixes events in JDK Flight Recorder recordings. Read at startup. |

To record and read the events, use the [jfr tool](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jfr.html) or [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html). Category: `Mods`, `Refixes`.

| Event | Fields | Written |
|-------|--------|---------|
| `refixes.Metric` | `name`, `kind`, `value` | Every 5 seconds. A `counter` holds the change since its last event, a `gauge` the current value. |
| `refixes.Action` | `category`, `message` | When Refixes acts, under `Watchdog`, `IdleWorldPause` or `PerPlayerHotRadius`. |
| `refixes.Setting` | `name`, `value` | At startup and at each recording chunk. |

Metrics:
- Gauges: `AiTickThrottler throttled`, `AiTickThrottler active`, `AiTickThrottler cycle ms`, `IdlePlayer idle`, `IdleWorldPause paused worlds`, `PerPlayerHotRadius radius`, `PathfindingBudget deferrals`.
- Counters: `AiTickThrottler froze`, `AiTickThrottler unfroze`, `AiTickThrottler budget exceeded`, `IdlePlayer applied`, `IdlePlayer restored`, `UnknownBlockCleaner removed`, `Netty queue limit disconnects`, `Quiche queue limit disconnects`, `Corrupt sections quarantined`, `Section quarantine failures`.
- With `Mixins.Diagnostics.PatchCounters`: `Patch <switch> reached` and `Patch <switch> effect`, as in `/patchcounters`.

In a recording started after server start, each counter's first event includes earlier counts, so skip it when summing. Messages never contain player names, IP addresses or file paths.

Settings events: `Refixes version`, `Hytale server version`, `Early patches` with the version gate result, `Patch counters`, and every `Refixes.json` switch and `config.json` value that differs from its default, by dotted path such as `Mixins.Tuning.TickWait`.
