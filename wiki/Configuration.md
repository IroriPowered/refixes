Runtime settings in `mods/IroriPowered_Refixes/config.json`, created by the server. Patch switches are documented in [Mixins](Mixins).

# Blackbox Integration

| Key | Default | Description |
|-----|---------|-------------|
| `BlackboxIntegration` | `true` | Send events and gauges to Blackbox, if installed. |

# Early Patches
Section: `Early`

Requires a full restart. Some settings also require their matching [Mixin](Mixins).

| Key | Default | Description |
|-----|---------|-------------|
| `MaxSectionsPerSecond` | `360` | Sections streamed per player per second. Legacy `MaxChunksPerSecond` is ignored. |
| `MaxSectionsPerTick` | `40` | Sections streamed per player per tick. Legacy `MaxChunksPerTick` is ignored. |
| `VanillaKeepSpawnLoaded` | `true` | Keep spawn chunks loaded. |
| `UnloadDistanceOffset` | `4` | Extra loaded chunks beyond player view radius. |
| `PathfindingMaxPathLength` | `200` | Maximum nodes in an NPC path. |
| `PathfindingOpenNodesLimit` | `80` | Maximum simultaneous A* frontier nodes per search. |
| `PathfindingTotalNodesLimit` | `400` | Maximum expanded nodes per A* search. |
| `PathfindingMaxNewSearchesPerTick` | `8` | Maximum new searches per tick. |
| `PathfindingMaxNodeExpansionsPerTick` | `600` | Maximum A* expansions per tick across searches. |
| `ShutdownSaveTimeoutSeconds` | `10` | Store resource save timeout in seconds. Nonpositive values wait indefinitely. |
| `BackpressureMaxOutboundBytes` | `16777216` | Outbound bytes per connection before backpressure, 16 MiB. |
| `BackpressureGraceMs` | `10000` | Milliseconds before closing a stalled connection. |

## Cylinder Visibility
Section: `Early.CylinderVisibility`

| Key | Default | Description |
|-----|---------|-------------|
| `HeightMultiplier` | `2.0` | Cylinder vertical half height relative to horizontal view radius. |

## KD Tree Optimization
Section: `Early.KDTreeOptimization`

| Key | Default | Description |
|-----|---------|-------------|
| `SpatialFastSortThreshold` | `64` | Below this entity count, use coordinate sorting instead of Morton sorting. |

# Listeners
Section: `Listeners`

Cleaner scans require `UnknownBlockCleaner`. They delete unknown content.

| Key | Default | Description |
|-----|---------|-------------|
| `UnknownBlockCleaner` | `false` | Remove unknown blocks from newly loaded sections. |
| `UnknownBlockCleanerScanFluids` | `false` | Also remove unknown fluids. |
| `UnknownBlockCleanerScanContainers` | `false` | Also remove unknown container items. |
| `UnknownBlockCleanerScanPlayerInventories` | `false` | Also remove unknown inventory items when players enter a world. |
| `UnknownBlockCleanerExclude` | `[]` | Protected ID prefixes across all scans, such as `dynamicseasons:`. |
| `UnknownBlockCleanerBudgetMs` | `10` | Section scan budget in milliseconds, checked between sections. |
| `UnknownBlockCleanerIntervalMs` | `50` | Milliseconds between passes, minimum 20. |

# Systems
Section: `Systems`

| Key | Default | Description |
|-----|---------|-------------|
| `CraftingManager` | `true` | Clears stale crafting bench bindings. |
| `EntityDespawnTimer` | `true` | Manage item, loose block entity and projectile lifetimes. |

## Entity Despawn Timer
Section: `Systems.EntityDespawnTimerConfig`

Requires `EntityDespawnTimer`. Existing lifetimes take precedence by default. Category values apply to null timer repairs, added timers or explicit overrides. Missing timers normally mean permanent entities.

| Key | Default | Description |
|-----|---------|-------------|
| `ItemDespawnSeconds` | `300` | Dropped item lifetime in seconds, `0` means permanent. |
| `BlockEntityDespawnSeconds` | `300` | Loose block entity lifetime in seconds, `0` means permanent. |
| `ProjectileDespawnSeconds` | `60` | Projectile lifetime in seconds, `0` means permanent. |
| `AddTimerWhenMissing` | `false` | Add missing timers at spawn, including intentionally permanent entities. Null timers are repaired regardless. |
| `OverrideExistingTimers` | `false` | Replace existing timers at spawn with category values. `0` removes the timer. Saved entities retain timers on reload. |

# Services

## AI Tick Throttler
Section: `Services.AiTickThrottler`

Distance throttling pauses movement and gravity between NPC steps, not just decisions. Motion exemptions default to `true`, but stored `false` values remain respected. Only freezes owned by this service are released. World pause, global NPC freeze and unrelated freezes still apply.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Enable the service. |
| `UpdateIntervalMs` | `150` | Milliseconds between scans. |
| `MaxCycleMs` | `30` | Milliseconds for new freeze admissions, `0` disables the budget. Owned cleanup and due steps run regardless, so this is not a total scan cap. |
| `ScanShards` | `1` | Spread new freeze admissions over this many scans. Check owned NPCs for thaws and due steps every scan. |
| `NearChunks` | `2` | Full tick rate within this distance, about 64 blocks. |
| `MidChunks` | `4` | Mid tick rate within this distance, about 128 blocks. |
| `FarChunks` | `6` | Far tick rate within this distance, about 192 blocks. |
| `MidTickSeconds` | `0.2` | Seconds between mid band ticks. |
| `FarTickSeconds` | `0.5` | Seconds between far band ticks. |
| `VeryFarTickSeconds` | `1.0` | Seconds between ticks beyond the far band. |
| `MinTickSeconds` | `0.05` | Minimum throttled interval in seconds. |
| `ActivationHysteresisChunks` | `0` | Extra chunk margin before changing bands. |
| `MaxUnfreezesPerTick` | `10` | Ordinary near thaws per scan. Newly exempt NPCs bypass this cap. |
| `MaxFreezesPerTick` | `20` | New freezes per scan with players present or `StepWithoutPlayers` enabled. |
| `ThrottleExcludedNpcTypes` | `[]` | NPC types exempt from throttling. |
| `ThrottleExcludeMounts` | `true` | Exempt ridden entities. |
| `ThrottleExcludeFlying` | `true` | Exempt flying controllers or movement states. `false` allows throttling. |
| `ThrottleExcludeAirborneOrDead` | `true` | Exempt airborne, falling or dead NPCs. `false` allows throttling. |
| `StepWithoutPlayers` | `false` | Step at `VeryFarTickSeconds` without players. Otherwise remain frozen and cancel pending owned steps. |
| `CleanupFrozenEntities` | `false` | Release stranded throttler freezes on load. |
| `CleanupExcludedNpcTypes` | `[]` | NPC types exempt from cleanup. |
| `LegacyCleanup` | `false` | Enable legacy orphan freeze cleanup. |
| `LegacyCleanupExcludedNpcTypes` | `[]` | NPC types exempt from legacy cleanup. |

## Per Player Hot Radius
Section: `Services.PerPlayerHotRadius`

Adjusts fully ticking chunk radii using each world's TPS. Healthy worlds use `MaxRadius`, matching the engine default of 8. The optional memory guard reduces server view radius after sustained heap pressure following major garbage collection, then restores it.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Enable the service. |
| `CheckIntervalMs` | `5000` | Milliseconds between adjustments. |
| `MinRadius` | `2` | Minimum hot chunk radius. |
| `MaxRadius` | `8` | Maximum hot chunk radius. |
| `TPSLowFraction` | `0.75` | Target TPS fraction at or below which `MinRadius` applies. |
| `TPSHighFraction` | `0.90` | Target TPS fraction at or above which `MaxRadius` applies. |
| `MemoryGuardEnabled` | `false` | Reduce server view radius under pressure, restore on recovery or shutdown. |
| `MemoryHeapThreshold` | `0.85` | Heap usage ratio after GC that signals pressure. |
| `MemoryMinViewRadius` | `2` | Minimum guarded view radius. |
| `MemoryViewDecreaseFactor` | `0.75` | View radius multiplier per reduction. |
| `MemoryRecoveryWaitSeconds` | `60` | Seconds without pressure before restoring one radius unit. |

## Idle Player Handler
Section: `Services.IdlePlayerHandler`

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
Section: `Services.IdleWorldPause`

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `false` | Pause worlds without players. |
| `CheckIntervalMs` | `10000` | Milliseconds between empty world checks. |
| `ExcludedWorlds` | `[]` | Worlds never paused when empty. |

# Chunk Loader
Section: `ChunkLoader`

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Register `/chunkloader` and load kept chunks. When disabled, saved loaders remain on disk but are not applied. |

# Shared Instance Worlds
Section: `SharedInstanceWorlds`

Controls persistent instance reuse and resets, such as dungeons.

| Key | Default | Description |
|-----|---------|-------------|
| `ExcludedPrefixes` | `[]` | World name prefixes excluded from shared instance handling. |
| `ResetOnEmpty` | `false` | Reset shared instances when their last player leaves. |

# Watchdog
Section: `Watchdog`

Monitors world threads for stalls and crashes.

| Key | Default | Description |
|-----|---------|-------------|
| `Enabled` | `true` | Enable monitoring. |
| `ShutdownOnDefaultWorldCrash` | `true` | Shut down if the default world thread crashes. |
| `AutoRestartWorlds` | `false` | Restart failed worlds after a successful save. |
| `DumpAllThreads` | `false` | Dump all JVM threads, not just the stalled thread. |
| `ActivationDelayMs` | `10000` | Milliseconds after startup before monitoring. |
| `ThreadTimeoutMs` | `30000` | Unresponsive milliseconds before flagging a stall. |
| `ShutdownTimeoutMs` | `60000` | Milliseconds allowed for watchdog shutdown. |
| `RestartSaveTimeoutMs` | `15000` | Milliseconds allowed for saving before restart. Failure aborts restart. |
| `AutoRestartingWorldFilter` | `[]` | Empty excludes instances, plus the default world when `ShutdownOnDefaultWorldCrash` is enabled. |

# Experimental
Section: `Experimental`

| Key | Default | Description |
|-----|---------|-------------|
| `ParallelSteeringThreshold` | `64` | Minimum entities per chunk for parallel steering. Requires `Mixins.Experimental.Parallel.Steering`. |
