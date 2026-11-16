# Configuration

`mods/IroriPowered_Refixes/config.json` holds `Early` settings, which the early patches read, and `Runtime` settings for the runtime plugin. Rules shared with `Refixes.json` are in [Settings files](installing.md#settings-files).

The dotted `Section:` paths below are nested JSON objects. For example, `Runtime.Tuning.IdlePlayerHandler.Enabled` is written as follows. Merge settings into your existing file; do not replace the file with this example.

```json
{
  "Runtime": {
    "Tuning": {
      "IdlePlayerHandler": {
        "Enabled": false
      }
    }
  }
}
```

For Unknown Block Cleaner, AI Tick Throttler, Idle Player Handler and Idle World Pause, the other options take effect only when `Enabled` is `true`.

## Early Patches
Section: `Early`

These values take effect only through the patch named in each row. Changing a value does not enable its patch.

| Key | Default | Description |
|-----|---------|-------------|
| `MaxSectionsPerSecond` | `360` | Sections per player per second. `0` or below stops sending new chunk sections. Requires `Mixins.Tuning.ChunkStreaming`. |
| `MaxSectionsPerTick` | `40` | Sections per player per tick. `0` or below stops sending new chunk sections. Requires `Mixins.Tuning.ChunkStreaming`. |
| `VanillaKeepSpawnLoaded` | `true` | Keep the chunk column at X 0, Z 0 loaded. Requires `Mixins.Features.OriginRetention`. |
| `UnloadDistanceOffset` | `4` | The player's minimum loaded radius is set to the view radius plus this many chunks. On Hytale 0.7.7, chunks in that radius stay loaded and their pending generation is kept. Requires `Mixins.Tuning.ChunkStreaming`. |
| `PathfindingMaxPathLength` | `200` | Maximum nodes in an NPC path. Requires `Mixins.Tuning.PathfindingLimits`. |
| `PathfindingOpenNodesLimit` | `80` | Maximum open nodes per search. Requires `Mixins.Tuning.PathfindingLimits`. |
| `PathfindingTotalNodesLimit` | `400` | Maximum nodes discovered per search. Requires `Mixins.Tuning.PathfindingLimits`. |
| `PathfindingMaxNewSearchesPerTick` | `8` | Maximum new searches per world tick. `0` or below blocks new searches except for exempt NPCs. Requires `Mixins.Experimental.PathfindingBudget`. |
| `PathfindingMaxNodeExpansionsPerTick` | `600` | Maximum A* expansions per world tick across searches. `0` or below means no limit. Requires `Mixins.Experimental.PathfindingBudget`. |
| `ShutdownSaveTimeoutSeconds` | `10` | Shutdown save timeout in seconds. See the table below. |

The three per-search pathfinding caps never raise a limit set by the NPC role. At `0` or below, the NPC role limit applies.

`ShutdownSaveTimeoutSeconds` is used by three patches:

| Patch | Above `0` | `0` or below |
|-------|-----------|--------------|
| `Mixins.Recovery.ShutdownSaveTimeout` | Limits the store resource save, world config save and indexed storage close during server shutdown to this many seconds. Chunk saves are not limited. | No limit. |
| `Mixins.Correctness.WorldTransfer` | Runs an extra transfer cleanup save when a world stops, even when `ShutdownSaveTimeout` is off. Its waits have no time limit. | The cleanup save is skipped. |
| `Mixins.Correctness.WorldEventSaving` | Limits the wait for the last world events save on shutdown to this many seconds. | Shutdown does not wait for it. |

### Outgoing packet queues
Section: `Early`

Requires `Mixins.Correctness.OutgoingQueueLimits`. These per-connection limits apply to Netty QUIC and Quiche and are shared across the connection's streams.

| Key | Default | Description |
|-----|---------|-------------|
| `OutgoingQueueMaxBytes` | `67108864` | Maximum estimated queued packet data in bytes. The default is 64 MiB. `0` = no byte limit. |
| `OutgoingQueueMaxPackets` | `65536` | Maximum number of pending outgoing packets. `0` = no packet limit. |

Values must be whole numbers without quotes, decimals or exponent notation, from `0` up to 9223372036854775807 for bytes and 2147483647 for packets. Invalid values stop startup, even when `OutgoingQueueLimits` is off. They are not checked when Refixes disabled its patches for the server version.

A write that would exceed a limit closes the connection, so a limit below one packet's size closes the connection when that packet is sent. Setup pacing still applies with a limit of `0`. The limits are a fallback, not a transfer-speed setting or total download limit. Raising them allows larger backlogs and higher memory use. The byte count estimates packet data, not total heap usage.

Setup asset transfers do not count time spent waiting for the client to drain the queue against the vanilla `ConnectionTimeouts.SetupSendAssets` stage timeout. They are still capped at 10 times that timeout of wall-clock time. The dedicated server default is 120 seconds, a 20 minute cap. Singleplayer uses 300 seconds, a 50 minute cap. A client that drains too slowly is then disconnected with the normal stage-timeout message.

### Cylinder Visibility
Section: `Early.CylinderVisibility`

| Key | Default | Description |
|-----|---------|-------------|
| `HeightMultiplier` | `2.0` | Vertical height as a view radius multiplier. Values below `2.0` can hide entities vanilla sends above or below the player. Requires `Mixins.Tuning.CylinderVisibility`. |

### KD Tree Optimization
Section: `Early.KDTreeOptimization`

| Key | Default | Description |
|-----|---------|-------------|
| `SpatialFastSortThreshold` | `64` | Item count below which every spatial index uses the simple sort. Requires `Mixins.Optimizations.KDTree`. |

## Unknown Block Cleaner
Section: `Runtime.Recovery.UnknownBlockCleaner`

Risk: Removal is permanent. Back up the world and check `Exclude` before enabling. Only chunk sections loaded while the cleaner is on are scanned.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Remove unknown blocks when chunk sections are added to a world. |
| `ScanFluids` | `false` | Also remove unknown fluids. |
| `ScanContainers` | `false` | Also remove unknown container items. |
| `ScanPlayerInventories` | `false` | Also remove unknown inventory items when players enter a world. |
| `Exclude` | `[]` | Protected ID prefixes across all scans, such as `dynamicseasons:`. Prefixes are case-sensitive. |
| `BudgetMs` | `10` | Scan budget in milliseconds, checked between sections. Values below 1 act as 1. |
| `IntervalMs` | `50` | Milliseconds between passes, minimum 20. |

## Correctness
Section: `Runtime.Correctness`

| Key | Default | Description |
|-----|---------|-------------|
| `CraftingManager` | `true` | Clears stale crafting bench bindings. |

## Tuning

### Entity Despawn Timer
Section: `Runtime.Tuning.EntityDespawnTimer`

Runs only while `AddTimerWhenMissing` or `OverrideExistingTimers` is `true`. Applies to dropped items, loose block entities and projectiles when they spawn, not when they load from a save. Items that cannot be picked up are skipped. Missing timers normally mean permanent entities.

| Key | Default | Description |
|-----|---------|-------------|
| `ItemDespawnSeconds` | `300` | Dropped item lifetime in seconds, `0` means permanent. |
| `BlockEntityDespawnSeconds` | `300` | Loose block entity lifetime in seconds, `0` means permanent. |
| `ProjectileDespawnSeconds` | `60` | Projectile lifetime in seconds, `0` means permanent. |
| `AddTimerWhenMissing` | `false` | Add timers at spawn, including permanent entities. |
| `OverrideExistingTimers` | `false` | Replace existing spawn timers. `0` makes them permanent. |

### AI Tick Throttler
Section: `Runtime.Tuning.AiTickThrottler`

Ticks distant NPCs less often by pausing movement and gravity between steps. NPCs that are attacking, recently fought, changed AI state or target a player stay active. These rules cannot be turned off. While the throttler is enabled, its freezes are not saved.

Requires `Mixins.Compatibility.AiTickThrottler`.

Risk: If another plugin freezes a throttled NPC, Refixes stops managing it and leaves it frozen. That freeze is saved.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Enable the service. |
| `UpdateIntervalMs` | `150` | Milliseconds between scans. Keep at or below the longest tick interval. Values below 20 act as 20. |
| `MaxCycleMs` | `30` | Millisecond budget for new freezes. `0` is unlimited. Does not limit cleanup or scheduled NPC steps. |
| `ScanShards` | `1` | Spread new freezes across scans. Check existing freezes every scan. |
| `NearChunks` | `2` | Full tick rate within this many chunks, about 64 blocks. |
| `MidChunks` | `4` | Mid tick rate within this many chunks, about 128 blocks. |
| `FarChunks` | `6` | Far tick rate within this many chunks, about 192 blocks. |
| `MidTickSeconds` | `0.2` | Seconds between mid band ticks. |
| `FarTickSeconds` | `0.5` | Seconds between far band ticks. |
| `VeryFarTickSeconds` | `1.0` | Seconds between ticks beyond the far band. |
| `MinTickSeconds` | `0.05` | Minimum throttled interval in seconds. The scan interval also sets a floor. With defaults the effective minimum is 0.15 seconds. |
| `MaxStepSeconds` | `1.0` | Maximum simulated seconds per step. `0` is uncapped. Lower caps slow NPC simulation. |
| `ActivationHysteresisChunks` | `0` | Extra chunks beyond each band limit before an active NPC is first throttled. |
| `MaxUnfreezesPerTick` | `10` | Nearby NPCs resumed per scan. Newly exempt NPCs bypass this cap. |
| `MaxFreezesPerTick` | `20` | New freezes per scan when NPC stepping is active. |
| `ThrottleExcludedNpcTypes` | `[]` | NPC types exempt from throttling. |
| `ThrottleExcludeMounts` | `true` | Exempt ridden entities. |
| `ThrottleExcludeFlying` | `true` | Exempt flying NPCs. |
| `ThrottleExcludeAirborneOrDead` | `true` | Exempt airborne, falling or dead NPCs. |
| `StepWithoutPlayers` | `false` | Step NPCs in worlds without players. When `false`, every NPC that is not exempt is frozen at once in those worlds, without the `MaxCycleMs`, `ScanShards` and `MaxFreezesPerTick` limits. |

### Per Player Hot Radius
Section: `Runtime.Tuning.PerPlayerHotRadius`

Adjusts ticking chunk radii using world TPS. The optional memory guard lowers the server `MaxViewRadius`, not each player's view radius. When `mods/IroriPowered_Refixes/viewradius-restore` exists at startup, its value replaces the server `MaxViewRadius` and the file is deleted, even when the service or memory guard is off. Requires `Mixins.Compatibility.PlayerRadius`. The memory guard also needs `Mixins.Compatibility.ServerViewRadius`.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Enable the service. |
| `CheckIntervalMs` | `5000` | Milliseconds between adjustments. Values below 1000 act as 1000. |
| `MinRadius` | `2` | Minimum hot chunk radius. |
| `MaxRadius` | `8` | Maximum hot chunk radius. |
| `TPSLowFraction` | `0.75` | TPS fraction for minimum radius. |
| `TPSHighFraction` | `0.90` | TPS fraction for maximum radius. |
| `MemoryGuardEnabled` | `false` | Reduce the server view radius under pressure and restore it after recovery. |
| `MemoryHeapThreshold` | `0.85` | Heap usage ratio after GC that signals pressure. |
| `MemoryMinViewRadius` | `2` | Minimum guarded view radius. |
| `MemoryViewDecreaseFactor` | `0.75` | View radius multiplier per reduction. |
| `MemoryRecoveryWaitSeconds` | `60` | Seconds without pressure before restoring one radius unit. |

### Idle Player Handler
Section: `Runtime.Tuning.IdlePlayerHandler`

Reduces ticking and view radii for idle players. Horizontal movement or camera turns reset the idle timer. Requires `Mixins.Compatibility.PlayerRadius`.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Enable the service. |
| `IdleTimeoutSeconds` | `90` | Seconds of inactivity before becoming idle. Values below 30 act as 30. |
| `CheckIntervalSeconds` | `10` | Seconds between checks. Values below 5 act as 5. |
| `ReduceViewRadius` | `true` | Reduce idle view radius. |
| `IdleViewRadius` | `4` | Idle view radius. Values below 2 act as 2. |
| `ReduceHotRadius` | `true` | Reduce idle hot radius. |
| `IdleHotRadius` | `3` | Idle hot radius. Values below 2 act as 2. |
| `ReduceMinLoadedRadius` | `true` | Reduce idle minimum loaded chunk radius. On Hytale 0.7.7, this narrows the radius in which chunks stay loaded and pending chunk generation is kept. |
| `IdleMinLoadedRadius` | `2` | Idle minimum loaded chunk radius. Values below 2 act as 2. |
| `MovementThreshold` | `0.5` | Blocks moved to reset the idle timer. |

### Idle World Pause
Section: `Runtime.Tuning.IdleWorldPause`

Requires `Mixins.Compatibility.IdleWorldPause`. Empty worlds with chunk loaders stay unpaused while `Runtime.Features.ChunkLoader.Enabled` is `true`. Stopping the service restores its own pauses and preserves later manual pauses.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Pause worlds without players. This includes the default world and instances unless they are in `ExcludedWorlds`. |
| `CheckIntervalMs` | `10000` | Milliseconds between empty world checks. Values below 1000 act as 1000. |
| `ExcludedWorlds` | `[]` | Worlds never paused when empty. Names are not case-sensitive. |

## Chunk Loader
Section: `Runtime.Features.ChunkLoader`

A chunk loader keeps its chunk column and the section Y registered with `/chunkloader add` loaded and ticking. Other sections of that column that are already loaded, or that load later, are added to the loader and saved with it.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Enable `/chunkloader` and saved chunk loaders. |

## Shared Instance Worlds
Section: `Runtime.Features.SharedInstances`

Requires `Mixins.Features.SharedInstances`. Explicit names and instance keys take precedence.

Risk: Sharing can change consumable dungeon behavior. Back up shared worlds before enabling `ResetOnEmpty`, which permanently deletes their saved data.

| Key | Default | Description |
|-----|---------|-------------|
| `ExcludedPrefixes` | `[]` | Case-sensitive prefixes of the instance asset name excluded from sharing. An empty entry excludes every instance. |
| `ResetOnEmpty` | `false` | Delete a shared world's saved data after it is removed for being empty. Conditions below. |

`ResetOnEmpty` applies only when all of these conditions hold:
- The world's name starts with `instance-shared-`.
- It had players during this server run and is now empty.
- Its removal rules include `WorldEmptyCondition`, which removes empty instances.

Deletion happens after the world stops during normal instance removal. Crashed worlds and server shutdown are excluded.

## Compatibility
Section: `Runtime.Compatibility`

| Key | Default | Description |
|-----|---------|-------------|
| `ExistingSharedInstances` | `true` | Preserve existing shared worlds. Requires `Mixins.Features.SharedInstanceStorage`. With `false`, native instance removal can delete existing shared worlds. |

### Saved Throttler Cleanup
Section: `Runtime.Compatibility.TickThrottledCleanup`

Clears saved Refixes freezes when entities load, even with throttling or Refixes patches disabled.

Risk: Removing Refixes also removes this cleanup. NPCs frozen by Refixes versions before 0.6.0 stay frozen in chunks that have not loaded and saved since. See [AI-throttled NPCs](upgrading.md#ai-throttled-npcs).

| Key | Default | Description |
|-----|---------|-------------|
| `CleanupFrozenEntities` | `true` | Clear freezes marked by Refixes. |
| `CleanupExcludedNpcTypes` | `[]` | NPC types exempt from marked cleanup. |
| `LegacyCleanup` | `false` | Clear unmarked freezes too, including freezes from other systems. Also applies to items and other non-player entities. Only NPC types can be excluded. |
| `LegacyCleanupExcludedNpcTypes` | `[]` | NPC types exempt from legacy cleanup. |

## Watchdog Diagnostics
Section: `Runtime.Diagnostics.Watchdog`

Reports world stalls and crashes. Worlds other than the default world are checked only with `Runtime.Recovery.Watchdog.AutoRestartWorlds` and its support patch.

The watchdog runs while any of `Enabled`, `ShutdownOnDefaultWorldCrash`, `AutoRestartWorlds` or `HaltOnShutdownTimeout` is `true`. With `Enabled` set to `false`, default world alerts skip the thread dump. The default world is still monitored while `ShutdownOnDefaultWorldCrash` is `true`, and the server still shuts down when it stops responding.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Enable watchdog diagnostic reporting. |
| `DumpAllThreads` | `false` | Dump all JVM threads. When `false`, an alert dumps all world threads and a hung world in the restart list dumps its own thread. |
| `ActivationDelayMs` | `10000` | Milliseconds to wait after all worlds load. Checks then run every 5 seconds. |
| `ThreadTimeoutMs` | `30000` | Unresponsive milliseconds before flagging a stall. |

## Watchdog Recovery
Section: `Runtime.Recovery.Watchdog`

Automatic world restart needs the support patch supplied by `Mixins.Compatibility.Watchdog` or `Mixins.Correctness.WorldOwnership`. Default world monitoring works without Refixes patches.

Automatic restart only restarts crashed worlds. A world counts as crashed when it is no longer running or cannot accept tasks. A world that stops responding is logged and not restarted. After 5 failed restart attempts the watchdog gives up on that world until the server restarts.

Safety: Restart is skipped during server shutdown and for intentionally removed worlds. If the world is still registered, restart first needs a successful save check or confirmed shutdown within `RestartSaveTimeoutMs`. A world that is no longer registered is reloaded without this check. A failed check stops further restart attempts for that world until the server restarts.

Risk: `HaltOnShutdownTimeout` forces the process to exit without waiting for remaining saves. The forced exit uses exit code 1 and skips shutdown hooks. Unsaved data can be lost.

| Key | Default | Description |
|-----|---------|-------------|
| `ShutdownOnDefaultWorldCrash` | `true` | Shut down when the default world stops, stops responding for `ThreadTimeoutMs` or cannot accept tasks. Other worlds never trigger this. |
| `AutoRestartWorlds` | `false` | Automatically restart crashed worlds. |
| `HaltOnShutdownTimeout` | `false` | Force exit if shutdown exceeds the timeout. |
| `ShutdownTimeoutMs` | `60000` | Milliseconds before forced exit, when enabled. |
| `RestartSaveTimeoutMs` | `15000` | Save or shutdown timeout in milliseconds before restart. Values below 1 act as 1. |
| `AutoRestartingWorldFilter` | `[]` | Exact world names for automatic restart. Selection rules below. |

`AutoRestartingWorldFilter` is a list of world names, not prefixes. Names are trimmed and blank entries ignored. It only matters when `AutoRestartWorlds` is `true`.

- Empty after trimming: Choose non-instance worlds that were loaded when the watchdog started or were added later, minus worlds removed intentionally. Exclude the default world while `ShutdownOnDefaultWorldCrash` is `true`.
- Nonempty: Choose only the listed worlds. A listed world is loaded when it is not running and its save folder exists. This bypasses the automatic instance and default-world exclusions, not restart safety checks.

## Hybox Integration
Section: `Runtime.Diagnostics`

| Key | Default | Description |
|-----|---------|-------------|
| `HyboxIntegration` | `true` | Send metrics and events to Hybox, if installed. Events include world names. |
