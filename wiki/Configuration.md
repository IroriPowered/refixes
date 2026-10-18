Runtime settings live in `mods/IroriPowered_Refixes/config.json`, created and saved by the server. Patch switches are documented in [Mixins](Mixins). Restart after changing either file.

Settings are grouped under `Early` and `Runtime`. Missing values use defaults. Back up your settings before upgrading. Old paths are not migrated and unknown keys are removed on save.

# Hybox Integration
Section: `Runtime.Diagnostics`

| Key | Default | Description |
|-----|---------|-------------|
| `HyboxIntegration` | `true` | Send metrics to Hybox, if installed. |

# Early Patches
Section: `Early`

These settings require their corresponding Mixin patches.

| Key | Default | Description |
|-----|---------|-------------|
| `MaxSectionsPerSecond` | `360` | Sections per player per second. Requires `Mixins.Tuning.ChunkStreaming`. |
| `MaxSectionsPerTick` | `40` | Sections per player per tick. Requires `Mixins.Tuning.ChunkStreaming`. |
| `VanillaKeepSpawnLoaded` | `true` | Keep the origin chunk loaded. Requires `Mixins.Features.OriginRetention`. |
| `UnloadDistanceOffset` | `4` | Extra loaded chunks beyond player view radius. Requires `Mixins.Tuning.ChunkStreaming`. |
| `PathfindingMaxPathLength` | `200` | Maximum nodes in an NPC path. Requires `Mixins.Tuning.PathfindingLimits`. |
| `PathfindingOpenNodesLimit` | `80` | Maximum open nodes per search. Requires `Mixins.Tuning.PathfindingLimits`. |
| `PathfindingTotalNodesLimit` | `400` | Maximum expanded nodes per search. Requires `Mixins.Tuning.PathfindingLimits`. |
| `PathfindingMaxNewSearchesPerTick` | `8` | Maximum new searches per tick. Requires `Mixins.Experimental.PathfindingBudget`. |
| `PathfindingMaxNodeExpansionsPerTick` | `600` | Maximum A* expansions per tick across searches. Requires `Mixins.Experimental.PathfindingBudget`. |
| `ShutdownSaveTimeoutSeconds` | `10` | Save timeout in seconds, unlimited at `0` or below. Requires `Mixins.Recovery.ShutdownSaveTimeout`. |

## Cylinder Visibility
Section: `Early.CylinderVisibility`

| Key | Default | Description |
|-----|---------|-------------|
| `HeightMultiplier` | `2.0` | Vertical half height as a view radius multiplier. Requires `Mixins.Tuning.CylinderVisibility`. |

## KD Tree Optimization
Section: `Early.KDTreeOptimization`

| Key | Default | Description |
|-----|---------|-------------|
| `SpatialFastSortThreshold` | `64` | Entity threshold for coordinate sorting. Requires `Mixins.Optimizations.KDTree`. |

# Unknown Block Cleaner
Section: `Runtime.Recovery.UnknownBlockCleaner`

Deletes unknown blocks and optional fluid or item data. Back up the world and check `Exclude` before enabling.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Remove unknown blocks from newly loaded sections. |
| `ScanFluids` | `false` | Also remove unknown fluids. |
| `ScanContainers` | `false` | Also remove unknown container items. |
| `ScanPlayerInventories` | `false` | Also remove unknown inventory items when players enter a world. |
| `Exclude` | `[]` | Protected ID prefixes across all scans, such as `dynamicseasons:`. |
| `BudgetMs` | `10` | Scan budget in milliseconds, checked between sections. |
| `IntervalMs` | `50` | Milliseconds between passes, minimum 20. |

# Correctness
Section: `Runtime.Correctness`

| Key | Default | Description |
|-----|---------|-------------|
| `CraftingManager` | `true` | Clears stale crafting bench bindings. |

# Tuning

## Entity Despawn Timer
Section: `Runtime.Tuning.EntityDespawnTimer`

Runs when either timer option is enabled and also repairs null timers. Existing lifetimes are kept unless overridden. Missing timers normally mean permanent entities.

| Key | Default | Description |
|-----|---------|-------------|
| `ItemDespawnSeconds` | `300` | Dropped item lifetime in seconds, `0` means permanent. |
| `BlockEntityDespawnSeconds` | `300` | Loose block entity lifetime in seconds, `0` means permanent. |
| `ProjectileDespawnSeconds` | `60` | Projectile lifetime in seconds, `0` means permanent. |
| `AddTimerWhenMissing` | `false` | Add timers at spawn, including permanent entities. |
| `OverrideExistingTimers` | `false` | Replace spawn timers. `0` makes them permanent. Saved timers are kept on reload. |

## AI Tick Throttler
Section: `Runtime.Tuning.AiTickThrottler`

Throttles distant NPCs by pausing movement and gravity between steps. Respects unmarked freezes and the global freeze. Later freezes on marked NPCs cannot be distinguished. Saved cleanup is under `Runtime.Compatibility.TickThrottledCleanup`.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Enable the service. |
| `UpdateIntervalMs` | `150` | Milliseconds between scans. |
| `MaxCycleMs` | `30` | Budget for new freezes in milliseconds. `0` disables it. Does not cap cleanup or due steps. |
| `ScanShards` | `1` | Spread new freezes across scans. Check existing freezes every scan. |
| `NearChunks` | `2` | Full tick rate within this distance, about 64 blocks. |
| `MidChunks` | `4` | Mid tick rate within this distance, about 128 blocks. |
| `FarChunks` | `6` | Far tick rate within this distance, about 192 blocks. |
| `MidTickSeconds` | `0.2` | Seconds between mid band ticks. |
| `FarTickSeconds` | `0.5` | Seconds between far band ticks. |
| `VeryFarTickSeconds` | `1.0` | Seconds between ticks beyond the far band. |
| `MinTickSeconds` | `0.05` | Minimum throttled interval in seconds. |
| `ActivationHysteresisChunks` | `0` | Extra chunk margin before changing bands. |
| `MaxUnfreezesPerTick` | `10` | Ordinary near thaws per scan. Newly exempt NPCs bypass this cap. |
| `MaxFreezesPerTick` | `20` | New freezes per scan when NPC stepping is active. |
| `ThrottleExcludedNpcTypes` | `[]` | NPC types exempt from throttling. |
| `ThrottleExcludeMounts` | `true` | Exempt ridden entities. |
| `ThrottleExcludeFlying` | `true` | Exempt flying NPCs. |
| `ThrottleExcludeAirborneOrDead` | `true` | Exempt airborne, falling or dead NPCs. |
| `StepWithoutPlayers` | `false` | Step NPCs without players. Otherwise keep them frozen. |

## Per Player Hot Radius
Section: `Runtime.Tuning.PerPlayerHotRadius`

Adjusts ticking chunk radii using each world's TPS. Healthy worlds use `MaxRadius`. The optional memory guard lowers view radius under heap pressure.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Enable the service. |
| `CheckIntervalMs` | `5000` | Milliseconds between adjustments. |
| `MinRadius` | `2` | Minimum hot chunk radius. |
| `MaxRadius` | `8` | Maximum hot chunk radius. |
| `TPSLowFraction` | `0.75` | TPS fraction for minimum radius. |
| `TPSHighFraction` | `0.90` | TPS fraction for maximum radius. |
| `MemoryGuardEnabled` | `false` | Reduce view radius under pressure and restore it after recovery. |
| `MemoryHeapThreshold` | `0.85` | Heap usage ratio after GC that signals pressure. |
| `MemoryMinViewRadius` | `2` | Minimum guarded view radius. |
| `MemoryViewDecreaseFactor` | `0.75` | View radius multiplier per reduction. |
| `MemoryRecoveryWaitSeconds` | `60` | Seconds without pressure before restoring one radius unit. |

## Idle Player Handler
Section: `Runtime.Tuning.IdlePlayerHandler`

Reduces inactive players' ticking and view radii.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Enable the service. |
| `IdleTimeoutSeconds` | `90` | Seconds of inactivity before becoming idle. |
| `CheckIntervalSeconds` | `10` | Seconds between checks. |
| `ReduceViewRadius` | `true` | Reduce idle view radius. |
| `IdleViewRadius` | `4` | Idle view radius. |
| `ReduceHotRadius` | `true` | Reduce idle hot radius. |
| `IdleHotRadius` | `3` | Idle hot radius. |
| `ReduceMinLoadedRadius` | `true` | Reduce idle minimum loaded chunk radius. |
| `IdleMinLoadedRadius` | `2` | Idle minimum loaded chunk radius. |
| `MovementThreshold` | `0.5` | Blocks moved to reset the idle timer. |

## Idle World Pause
Section: `Runtime.Tuning.IdleWorldPause`

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Pause worlds without players. |
| `CheckIntervalMs` | `10000` | Milliseconds between empty world checks. |
| `ExcludedWorlds` | `[]` | Worlds never paused when empty. |

# Chunk Loader
Section: `Runtime.Features.ChunkLoader`

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Enable `/chunkloader` and saved chunk loaders. |

# Shared Instance Worlds
Section: `Runtime.Features.SharedInstances`

Requires `Mixins.Features.SharedInstances`. Explicit names and instance keys take precedence. Sharing can change consumable dungeon behavior.

| Key | Default | Description |
|-----|---------|-------------|
| `ExcludedPrefixes` | `[]` | Instance prefixes excluded from sharing. |

# Compatibility
Section: `Runtime.Compatibility`

| Key | Default | Description |
|-----|---------|-------------|
| `ExistingSharedInstances` | `true` | Persist existing shared worlds. Requires `Mixins.Features.SharedInstanceStorage`. Does not enable new shared instances. |

## Saved Throttler Cleanup
Section: `Runtime.Compatibility.TickThrottledCleanup`

Runs on saved entity load, even with throttling disabled. Released NPCs run until the next throttler scan. Markers cannot distinguish later admin freezes.

| Key | Default | Description |
|-----|---------|-------------|
| `CleanupFrozenEntities` | `true` | Clear freezes marked by Refixes. |
| `CleanupExcludedNpcTypes` | `[]` | NPC types exempt from marked cleanup. |
| `LegacyCleanup` | `false` | Clear unmarked freezes too, including freezes from other systems. |
| `LegacyCleanupExcludedNpcTypes` | `[]` | NPC types exempt from legacy cleanup. |

# Watchdog Diagnostics
Section: `Runtime.Diagnostics.Watchdog`

Reports world stalls and crashes. Recovery actions keep monitoring active even if reporting is disabled.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Enable watchdog diagnostic reporting. |
| `DumpAllThreads` | `false` | Dump all JVM threads, not just the stalled thread. |
| `ActivationDelayMs` | `10000` | Milliseconds after startup before monitoring. |
| `ThreadTimeoutMs` | `30000` | Unresponsive milliseconds before flagging a stall. |

# Watchdog Recovery
Section: `Runtime.Recovery.Watchdog`

Restart requires stopped store threads and a successful save.

| Key | Default | Description |
|-----|---------|-------------|
| `ShutdownOnDefaultWorldCrash` | `true` | Shut down after a default world crash or watchdog alert. |
| `AutoRestartWorlds` | `false` | Restart eligible failed worlds after a successful save. |
| `HaltOnShutdownTimeout` | `false` | Force process exit after the shutdown timeout. |
| `ShutdownTimeoutMs` | `60000` | Milliseconds before forced exit, when enabled. |
| `RestartSaveTimeoutMs` | `15000` | Save timeout before restart. Failure cancels restart. |
| `AutoRestartingWorldFilter` | `[]` | Empty excludes instances, plus the default world when `ShutdownOnDefaultWorldCrash` is enabled. |
