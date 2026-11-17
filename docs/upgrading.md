# Upgrading

## From 0.6.0 to 0.7.0-pre.5

Refixes 0.7.0-pre.5 is the release for Hytale 0.8.0-pre.5 and later 0.8 versions. Hytale 0.8.0-pre.5 fixes many defects that Refixes 0.6.0 patched, so their switches are removed.

### Before the first start

1. Update the server to Hytale 0.8.0-pre.5 or a later 0.8 version. On Hytale 0.7.7, Refixes 0.7.0-pre.5 disables its early patches.
2. Back up `mods/IroriPowered_Refixes/`.
3. Replace the Refixes 0.6.0 JAR in `earlyplugins` with `refixes-0.7.0-pre.5.jar`. The Hyinit requirement is unchanged.
4. After the first start, check the lines that start with `[Refixes] Refixes.json:`. Every switch for a defect that Hytale 0.8.0-pre.5 fixes is removed, whatever its value, and logged as `Removed retired setting` with the reason `fixed in Hytale 0.8.0-pre.5`. Later starts log nothing because the keys are gone.

A `config.json` written by Refixes 0.6.0 keeps all of its keys and defaults. No setting there moves or is removed.

Refixes 0.6.1 adds `Mixins.Tuning.WorldHeightBounds`, `Mixins.Tuning.WorldMapExistingTerrain` and `Mixins.Diagnostics.PatchCounters`. Refixes 0.7.0-pre.5 does not have these switches. On its first start it removes them from a `Refixes.json` written by 0.6.1 and logs each as `Removed unknown setting <path>=<value>`. Their features are not available on 0.7.0-pre.5.

### Changed behavior

- Hytale 0.8.0-pre.5 releases queued world tasks after 10 seconds of world config saving and keeps waiting for the save. `Mixins.Recovery.ShutdownSaveTimeout`, when enabled, still limits that wait during server shutdown, along with the store resource save and indexed storage close.
- `Mixins.Features.OriginRetention`, when enabled, keeps the origin column and its sections 0-9 loaded. Hytale 0.8.0-pre.5 unloads sections separately from their column.
- Chunk loaders keep the column, sections 0-9 and the section Y values registered with `/chunkloader add`. Sections that load later are no longer added to the loader. Y values outside 0-9 that 0.6.0 added this way are still in the saved loader files and stay loaded. Remove and add a loader again to drop them.
- `Early.UnloadDistanceOffset` and `Runtime.Tuning.IdlePlayerHandler.ReduceMinLoadedRadius` no longer change when chunks unload. They only change the radius in which pending chunk generation is kept.
- If the server stops waiting for a removed world after 90 seconds while the world is still stopping, watchdog auto-restart postpones the restart to the next watchdog check instead of counting a failed attempt. The next check runs the save or shutdown check again.

## From 0.5.x to 0.6.0

Refixes 0.6.0 reorganizes `Refixes.json` and `config.json`. Settings from 0.5.x are moved once, at the first start on Hytale 0.7.x. Starting once on another server version discards the 0.5.x `config.json` settings without moving them. Back up `mods/IroriPowered_Refixes/` before upgrading. Refixes 0.7.0-pre.5 applies the same moves, except that switches retired for Hytale 0.8.0-pre.5 are removed.

Every moved, ignored or removed setting is logged with the prefix `[Refixes] Refixes.json:` or `[Refixes] config.json:`. Check these lines after the first start. Later starts log nothing because the old keys are gone.

Requirements changed too:
- Hyinit and Fabric Mixin versions: see [Installation](installing.md#installation).
- Refixes 0.6.0 is built for Hytale 0.7.7. Refixes 0.5.0 was built for Hytale 0.6.4.

### Refixes.json

Switches move from the old `Crashfixes` and `Helpers` sections and from some `Optimizations` and `Experimental` keys to the sections in [Patches](patches.md):

| Old switches | New section |
|---|---|
| `Crashfixes.BlockSectionSafety`, `MotionControllerBase`, `TurnOffTeleportersSystem`, `EntityChunkLoadingSystem`, `CollisionModule` | `Recovery` as `CorruptSectionReset`, `MotionControllerBase`, `TurnOffTeleportersSystem`, `InvalidSavedHolders`, `InvalidCollisionData` |
| Other `Crashfixes.*` | `Correctness`, except `PageManager` in `Compatibility` |
| `Helpers.BlockComponentChunk`, `InteractionChain`, `WorldMapTracker`, `WorldSpawningSystem` | `Recovery` as `DuplicateComponentReferences`, `InteractionChain`, `WorldMapTracker`, `WorldSpawningSystem` |
| `Helpers.HytaleServer`, `SpawnMarkerBlockStateHeartbeat` | `Diagnostics` |
| `Helpers.ServerAuthManager`, `UpdateCheckCommand`, `UpdateDownloadCommand`, `UpdateModule`, `WorldConfig` | `Compatibility` |
| `Helpers.TickSleep`, `WorldPauseCommand` | `Tuning.TickWait`, `Features.WorldPauseCommand` |
| Other `Helpers.*` with a successor | `Correctness` |
| `Optimizations.CollectVisible`, `PlayerChunkTrackerSystems`, `ChunkUnloadingSystem`, `SkipSystemMetrics` | `Tuning.CylinderVisibility`, `Tuning.ChunkStreaming`, `Features.OriginRetention`, `Diagnostics.SkipSystemMetrics` |
| `Experimental.ShutdownSaveTimeout`, `TickSurvival`, `SharedInstances`, `SkipEmptyLightSections` | `Recovery`, `Recovery`, `Features`, `Optimizations` |
| `HypixelServices.LiveConfig` | `Compatibility.DisableLiveConfig` |

Rules:
- A value equal to the 0.5.x default is not carried over. Refixes 0.5.x wrote every switch with its default, so carrying those values over would undo the new defaults. The log line reads `Removed … (old default)`.
- A value you changed is copied to the new switch. If the new switch already has a value, that value wins and the old one is logged as `Ignored`.
- `Helpers.WorldMapTracker` is copied to both `Recovery.WorldMapTracker` and `Correctness.WorldMapCache`. Refixes 0.7.0-pre.5 removes it instead, because both successors are retired.
- An empty `HypixelServices` object is removed.
- Switches without a successor are removed and logged as `Removed retired setting` with the reason.
- `Optimizations.KDTree` keeps its path. A value of `true`, the 0.5.x default, is treated as an old default and removed, so the new default `false` applies. A value of `false` is kept.

Development builds after 0.5.1 used some other paths. These are moved with the same rules:
- `Correctness.QuicheChannel` is split into `OutgoingQueueLimits`, `SetupPacing`, `QuicCloseRetention`, `ChunkSectionFences` and `WorldMapPacing`. Refixes 0.7.0-pre.5 no longer sets `WorldMapPacing`.
- `Correctness.WorldTransfer` is split into `WorldTransfer`, `WorldReopenGuard`, `StorageOrdering` and `TransferCombatClock`. `WorldReopenGuard` stays on while `WorldRetirement` is on. Refixes 0.7.0-pre.5 no longer sets `WorldReopenGuard`.
- `Recovery.MarkerAddRemoveSystem` and `Recovery.TickingSpawnMarkerSystem` move to `Correctness`. A `false` at these old paths was their old default, so it is not kept and the new default `true` applies. Check both after upgrading.

### config.json

The old root sections move under `Runtime`. Each value is moved on its own:

| Old path | New path |
|---|---|
| `BlackboxIntegration` | `Runtime.Diagnostics.HyboxIntegration` |
| `HyboxIntegration` from 0.5.1 and 0.5.2 | `Runtime.Diagnostics.HyboxIntegration` |
| `Listeners.UnknownBlockCleaner` and `Listeners.UnknownBlockCleaner*` | `Runtime.Recovery.UnknownBlockCleaner.Enabled` and the matching keys in that section |
| `Systems.CraftingManager` | `Runtime.Correctness.CraftingManager` |
| `Systems.EntityDespawnTimerConfig` | `Runtime.Tuning.EntityDespawnTimer` |
| `Services.PerPlayerHotRadius`, `IdlePlayerHandler`, `AiTickThrottler`, `IdleWorldPause` | `Runtime.Tuning` |
| `Services.AiTickThrottler.CleanupFrozenEntities`, `CleanupExcludedNpcTypes`, `LegacyCleanup`, `LegacyCleanupExcludedNpcTypes` | `Runtime.Compatibility.TickThrottledCleanup` |
| `ChunkLoader.Enabled` | `Runtime.Features.ChunkLoader.Enabled` |
| `SharedInstanceWorlds` | `Runtime.Features.SharedInstances` |
| `Watchdog.Enabled`, `DumpAllThreads`, `ActivationDelayMs`, `ThreadTimeoutMs` | `Runtime.Diagnostics.Watchdog` |
| `Watchdog.ShutdownOnDefaultWorldCrash`, `AutoRestartWorlds`, `ShutdownTimeoutMs`, `RestartSaveTimeoutMs`, `AutoRestartingWorldFilter` | `Runtime.Recovery.Watchdog` |

Rules:
- If the new path already has a value, that value wins and the old one is logged as `Ignored`.
- `Watchdog.Enabled` set to `false` also sets `Runtime.Recovery.Watchdog.ShutdownOnDefaultWorldCrash` and `AutoRestartWorlds` to `false`. The watchdog stays off as in 0.5.x.
- `Systems.EntityDespawnTimer` is removed. If it was `false`, both `AddTimerWhenMissing` and `OverrideExistingTimers` under `Runtime.Tuning.EntityDespawnTimer` are set to `false`.
- `Services.AiTickThrottler.CleanupFrozenEntities` set to `false` was the 0.5.x default and is not carried over. The new default is `true`.
- `Early.BackpressureMaxOutboundBytes`, `Early.BackpressureGraceMs` and `Experimental.ParallelSteeringThreshold` are removed.
- Any other key left under `Listeners`, `Systems`, `Services`, `ChunkLoader`, `SharedInstanceWorlds`, `Watchdog` or `Experimental` is removed and logged.
- The other `Early` settings keep their paths.

### Changed defaults

Set a switch explicitly to keep the 0.5.x behavior.

| Setting | 0.5.x | 0.6.0 |
|---|---|---|
| `Mixins.Recovery.CorruptSectionReset` (was `Crashfixes.BlockSectionSafety`) | on | off |
| `Mixins.Recovery.MotionControllerBase` | on | off |
| `Mixins.Recovery.TurnOffTeleportersSystem` | on | off |
| `Mixins.Recovery.InvalidSavedHolders` (was `Crashfixes.EntityChunkLoadingSystem`) | on | off |
| `Mixins.Recovery.InvalidCollisionData` (was `Crashfixes.CollisionModule`) | on | off |
| `Mixins.Recovery.DuplicateComponentReferences` (was `Helpers.BlockComponentChunk`) | on | off |
| `Mixins.Recovery.InteractionChain` | on | off |
| `Mixins.Recovery.WorldMapTracker` | on | off |
| `Mixins.Recovery.WorldSpawningSystem` | on | off |
| `Mixins.Tuning.CylinderVisibility` (was `Optimizations.CollectVisible`) | on | off |
| `Mixins.Tuning.ChunkStreaming` (was `Optimizations.PlayerChunkTrackerSystems`) | on | off |
| `Mixins.Tuning.PathfindingLimits` (was part of `Optimizations.AStarBase`) | on | off |
| `Mixins.Tuning.TickWait` (was `Helpers.TickSleep`) | on | off |
| `Mixins.Features.OriginRetention` (was `Optimizations.ChunkUnloadingSystem`) | on | off |
| `Mixins.Features.WorldPauseCommand` | on | off |
| `Mixins.Optimizations.KDTree` | on | off |
| `Mixins.Optimizations.SkipEmptyLightSections` | off | on |
| `Runtime.Compatibility.TickThrottledCleanup.CleanupFrozenEntities` | `false` | `true` |
| `Runtime.Recovery.Watchdog.HaltOnShutdownTimeout` (new) | always on while the watchdog ran | `false` |
| `Runtime.Tuning.AiTickThrottler.MaxStepSeconds` (new) | uncapped | `1.0` |

`Optimizations.AStarBase` is not migrated. Enable `Mixins.Tuning.PathfindingLimits` to apply the three per-search `Early.Pathfinding*` limits again. The per-tick limits need `Mixins.Experimental.PathfindingBudget`.

`Mixins.Correctness` is new in 0.6.0. All its switches except `WorldRetirement` default to `true`. See [Patches](patches.md#correctness).

### Removed features

- `/copychunks` and `/pastechunks`. The `refixes-clipboards/` folder in the server directory is no longer used and can be deleted.
- The WorldGen prefab browser root (`Helpers.PrefabListExtraRoots`). The prefab browser shows the vanilla roots only.
- Connection backpressure (`Experimental.ConnectionBackpressure`, `Early.BackpressureMaxOutboundBytes`, `Early.BackpressureGraceMs`). Replaced by `Mixins.Correctness.OutgoingQueueLimits` and [Outgoing packet queues](configuration.md#outgoing-packet-queues).
- Parallel ticking (`Experimental.Parallel.SpatialCollection`, `AllSystems`, `Steering`, `RelaxStoreAsserts`, `FluidReplicateChanges`, `ChunkReplicateChanges` and `Experimental.ParallelSteeringThreshold`). Only `Parallel.CollectVisible` remains.
- Blackbox metrics. `Runtime.Diagnostics.HyboxIntegration` sends metrics and events to Hybox instead.
- Watchdog restarts of worlds that were never loaded. With an empty `AutoRestartingWorldFilter`, automatic restart now only restarts worlds that were loaded.
- Watchdog restarts of hung worlds. A world that stops responding is logged with a thread dump. Only crashed worlds are restarted.

Development builds after 0.5.1 also had these. They are removed in 0.6.0:
- Cross-world spawn marker membership. NPCs that change world follow vanilla behavior again. They despawn after losing their marker, and the marker spawns new ones. The files `refixes-spawn-marker-ownership.txt` and `refixes-deleted-worlds.txt` in the universe folder, and any `.tmp` copies, are no longer used and can be deleted. Saved marker data written by those builds is ignored on load.
- Cleanup of entities in stopped source worlds. An entity that left a world which had already stopped is no longer removed from that world's region file. After a crash or restart it can exist twice, as in vanilla.
- `Mixins.Optimizations.InteractionMetaSizing`.
- `Mixins.Optimizations.LegacySeparation`.
- `Mixins.Correctness.RespawnPointPage`. Hytale 0.7.7 claims beds correctly itself.

### AI-throttled NPCs

Refixes 0.6.0 no longer writes throttle freezes to world saves. Saves made with older versions can still contain frozen NPCs. Keep Refixes installed with `Runtime.Compatibility.TickThrottledCleanup.CleanupFrozenEntities` set to `true`, which is the default, until the affected chunks have loaded and saved once. After that, removing Refixes leaves no frozen NPCs.
