# Patches

Patch switches live in `mods/IroriPowered_Refixes/Refixes.json`. Restart after editing. Missing values use defaults. A value that is not `true` or `false` is replaced with the default and logged. See [Configuration](configuration.md) for settings in `config.json`.

Use the exact `Mixins` section paths below. Switches from Refixes 0.5.x are moved to these paths once at startup. Other unknown keys under `Mixins` are removed and logged. See [Upgrading](upgrading.md).

Switches for defects that the installed Hytale version fixes itself are removed from `Refixes.json` at startup and logged. See [Switches removed because Hytale fixed the defect](upgrading.md#switches-removed-because-hytale-fixed-the-defect).

Correctness fixes are separated from optional tuning, world behavior and recovery. Check each option's default and risks before enabling it.

The top-level key `AllowUnsupportedServerVersion` is described in [Server version override](installing.md#server-version-override). Switch combinations that stop startup are listed in [Patch dependencies](troubleshooting.md#patch-dependencies).

## Correctness
Section: `Mixins.Correctness`

Fixes that preserve intended server behavior.

| Key | Default | Description |
|-----|---------|-------------|
| `MountPlugin` | `true` | Guards invalid rider references. |
| `PacketAdapters` | `true` | Runs inbound packet filters once on both transports. |
| `RocksDbFlush` | `true` | Flushes all RocksDB data columns on save and close. |
| `WorldGenReload` | `true` | Releases the saving lock when chunk deletion finishes or fails. |
| `OutgoingQueueLimits` | `true` | Limits each connection's outgoing packet queue on Netty QUIC and Quiche and grows Quiche receive buffers only as data arrives. Requires `SetupPacing`. On Quiche it closes the connection when the client stops the main, chunk or world map stream. |
| `SetupPacing` | `true` | Makes setup asset transfers wait for pending writes to drain instead of filling the outgoing queue. Works only while `OutgoingQueueLimits` is on. |
| `QuicCloseRetention` | `true` | Keeps a closing Netty QUIC connection for up to 2 seconds so the client receives the disconnect reason, then releases its resources. |
| `ChunkSectionFences` | `true` | Keeps chunk section updates in order while sections load and drops stale cached chunk snapshots, so clients do not keep outdated blocks, fluids, tints or environments. |
| `WorldMapPacing` | `true` | Limits how many ready world map images are sent to a player per update. |
| `MarkerAddRemoveSystem` | `true` | Drops a cached spawn marker reference that belongs to another world. Prevents "Incorrect store for entity reference" errors after an NPC changes world. |
| `TickingSpawnMarkerSystem` | `true` | Applies the same check in the marker heartbeat and keeps the despawn delay while the NPC is busy. |
| `WorldMapCache` | `true` | Keeps a newer world map image request when an older generation finishes. |
| `TrackedPlacementOnAddRemove` | `true` | Skips the block count update when a removed placement has no block name. |
| `WindowManager` | `true` | Unregisters a window when opening it throws. |
| `WorldNullGuard` | `true` | Returns an empty player list before the entity store is available. |
| `ConfigLoad` | `true` | Orders configuration saves and fixes reloads after a load completes or fails. |
| `PluginPreload` | `true` | Unloads a plugin whose configuration fails when it is loaded on a running server, so it can be loaded again. |
| `PluginLoaderCache` | `true` | Reads a plugin JAR again when the plugin is loaded after a failed or skipped load. |
| `WorldEventSaving` | `true` | Saves world events in order. On shutdown it waits at most `Early.ShutdownSaveTimeoutSeconds` for the last save, and not at all at `0` or below. |
| `WorldTransfer` | `true` | Guards player and entity transfers between worlds. On RocksDB worlds it removes a moved entity from the running source world's save. On indexed storage this happens only when the source world stops. Requires `StorageOrdering`. |
| `WorldReopenGuard` | `true` | Waits for a removed world's cleanup before its save folder is opened again. Refuses to open it if the folder is still in use after 30 seconds. |
| `StorageOrdering` | `true` | Finishes pending chunk and entity saves before a world closes and writes each chunk column in order. |
| `TransferCombatClock` | `true` | Moves a non-player entity's combat and damage timers to the target world's clock when it changes world. |
| `ParallelTaskLifecycle` | `true` | Waits for parallel workers before reusing tasks and reports their failures. |
| `ChunkStoreSectionBackoffDeadlock` | `true` | Avoids deadlock when chunk or section loading fails under the write lock. |
| `DespawnSystem` | `true` | Skips despawn checks when an entity has no despawn data or expiry time, avoiding a missing-value error. |
| `CommandBuffer` | `true` | Guards buffered removals against invalid or foreign entity references. |
| `EntityViewer` | `true` | Skips updates and removals for entities outside the viewer's visible set. |
| `EntityModule` | `true` | Keeps entity component types available while the entity module is disabled. |
| `EffectConditionClock` | `true` | Uses world time instead of the system clock when checking effect conditions. |
| `AssetInheritance` | `true` | Loads contained assets in dependency order. |
| `NPCKillsEntitySystem` | `true` | Treats invalid or cross-world NPC killer references as missing. |
| `PortalDeviceSummonPage` | `true` | Prevents duplicate shared instance return portals and handles missing spawn transforms. |
| `RemovalSystem` | `true` | Defers shared world removal during startup and respects portal lifetimes. |
| `SetMemoriesCapacityInteraction` | `true` | Fails the interaction with a warning when the player memories component type is not registered. |
| `StateSupport` | `true` | Prunes invalid or foreign-store player references from NPC interaction state. |
| `UUIDSystem` | `true` | Skips removal steps when the UUID component is missing. |
| `DeployableOwnerComponent` | `true` | Prunes stale deployable references after world changes. |
| `TeleportToPlayerCommand` | `true` | Avoids stale teleport history references in `/tp <player>` across worlds. |
| `TeleportInstanceInteraction` | `true` | Skips a missing return point offset. |
| `TickingThreadStop` | `true` | Keeps world saves running while a removed world stops. |
| `TickingThreadStart` | `true` | Completes startup futures when a ticking thread fails to start. |
| `SerializableArchetype` | `true` | Fixes the layout of saved entity copies so that adding a component does not fail when the first component type is not saved. |
| `WorldOwnership` | `true` | Checks ownership during world creation and removal. |
| `WorldRetirement` | `false` | Keeps a stopping world's save folder reserved until its thread has exited, also for instance copies and creative hub worlds. Requires `WorldReopenGuard`. |
| `StoredFlockSaving` | `true` | Marks spawn markers and stored-flock NPC sections for saving, so marker NPCs are not duplicated or lost after a restart. |

Setup transfers wait for pending writes to drain instead of filling the outgoing queue. On both transports a backlog defers chunk section updates while `ChunkSectionFences` is on. On Quiche a channel with 512 KiB queued also reports itself unwritable, which pauses chunk and world map sending. The default hard per-connection limit is 64 MiB of estimated packet data or 65,536 pending packets. Configure these limits under `Early` in `config.json`, then restart. See [Outgoing packet queues](configuration.md#outgoing-packet-queues). A write that would exceed a limit closes the connection. A limit set to `0` is not applied.

Netty warnings identify the byte or packet limit and include pending and requested counts. On Netty, packet size calculation failures are reported separately. Quiche reports them as a queue limit overflow without counts.

### Backports
Section: `Mixins.Correctness`

Fixes that Hytale also made in later versions.

| Key | Default | Description |
|-----|---------|-------------|
| `FluidChangeSaving` | `true` | Marks a chunk section for saving whenever its fluid changes, so fluid flow survives unload and restart. |
| `FluidRelightSection` | `true` | Relights the section where the fluid changed instead of the wrong column. |
| `ServerConfigSaveChain` | `true` | Runs server `config.json` saves one after another, so concurrent saves cannot share the temporary file. |
| `TickingThreadAbandon` | `true` | A stop timeout no longer takes over a ticking thread that is already running its shutdown hook. |
| `WorldTaskQueueClose` | `true` | Rejects a task offered while a world stops instead of stranding it, and cancels queued tasks when the world stops accepting tasks. |
| `ServerShutdownWatchdog` | `true` | Server shutdown waits at most 10 seconds for the config save, and the 3 second halt watchdog runs on its own thread. |
| `WorldMapTickingStop` | `true` | Toggling the compass or world map from the world thread no longer stalls the world for up to 30 seconds. |
| `UniverseDeletedCleanup` | `true` | A locked file in `worlds-deleted` logs a warning and is retried on the next start instead of stopping startup. |
| `MountMovementTeleport` | `true` | Ignores mount movement packets until the rider acknowledges a pending teleport. |
| `CommonAssetHashCleanup` | `true` | The server no longer deletes `.hash` files inside zip or jar asset packs. |
| `ObjectiveEntityRemoval` | `true` | Reverting or completing an objective skips unloaded worlds and removes its spawned entity on the world thread. |
| `FluidDropSection` | `true` | The fluid drop-distance check reads the section below at the neighbor block, not at the source column. |
| `SoundChannelEncoding` | `true` | Sound validation reports the channel count instead of failing on files with more than 2 channels. |
| `MessageSelect` | `true` | Text `select` placeholders match on the parameter's value. |
| `ServerManagerShutdown` | `true` | Shutdown no longer fails with a null pointer error when the network transport never started. |
| `EntitySectionLoad` | `true` | An entity that moves into an unloaded section starts one load request instead of one per tick, and is no longer removed when a late answer arrives for a section it has already left. |
| `PrefabPasteLoaded` | `true` | The prefab editor skips a prefab whose area could not be fully loaded and tells the player, instead of saving a truncated prefab over the original file. |
| `MapMarkerLocking` | `true` | Block map markers added or removed while the world map updates no longer crash the world map thread. |
| `PortalDeviceWorldClose` | `true` | Breaking a portal device closes its portal world in the background instead of stalling the origin world until that shutdown finishes. |
| `RocksDbColumnRemoval` | `true` | On RocksDB worlds, removing a chunk column now deletes all of its sections, block entities and stored entities, so old terrain and entities no longer come back. |
| `RocksDbScanBounds` | `true` | On RocksDB worlds, chunk loads after a large removal no longer slow down by stepping over every deleted row. |

## Optimizations
Section: `Mixins.Optimizations`

| Key | Default | Description |
|-----|---------|-------------|
| `CollisionConfig` | `true` | Caches the last fluid section used for collision checks. |
| `KDTree` | `false` | Uses coordinate sorting for small indexes. Changes tie order. |
| `SpawnManagerRecalc` | `true` | Combines pending spawn recalculations. |
| `RepulsionTicker` | `true` | Reuses entity repulsion buffers. |
| `SkipEmptyLightSections` | `true` | Skips empty light sections. |
| `RocksDbCompression` | `false` | On RocksDB worlds, compresses saved chunk data at every level and the write-ahead logs with zstd, and lowers the total log size target from 512 MiB to 128 MiB. |
| `FluidDiscovery` | `true` | Visits nonempty fluid cells during initialization instead of scanning every cell in supported sections. |
| `SoundHeaderValidation` | `true` | Validates sound headers without loading whole files. |
| `AssetPartSnapshotSharing` | `true` | Reuses identical asset packet snapshots across clients. |

With `RocksDbCompression` enabled, override the log size target with `-Dhytale.rocksdb.max_total_wal_size=<bytes>`. Use an integer byte count. An invalid value can stop startup.

## Tuning
Section: `Mixins.Tuning`

Optional changes to visibility, streaming, waiting and pathfinding limits.

| Key | Default | Description |
|-----|---------|-------------|
| `CylinderVisibility` | `false` | Uses a cylinder instead of a sphere for entity visibility. The default height includes more entities above and below players. See `Early.CylinderVisibility`. |
| `ChunkStreaming` | `false` | Applies `Early.MaxSectionsPerSecond`, `Early.MaxSectionsPerTick` and `Early.UnloadDistanceOffset`. |
| `WorldHeightBounds` | `false` | Stops sending sections above and below the standard world height on RocksDB worlds. Worlds whose generator creates sections outside that height are left unchanged. Anything already stored outside that height stays in the world but is not sent to players while this is on. |
| `WorldMapExistingTerrain` | `false` | Draws the world map only from terrain that already exists. The map no longer generates terrain and fills in as players load it. A chunk appears on the map once it and its eight neighbours exist. Worlds whose map is limited to a fixed area are left unchanged. |
| `TickWait` | `false` | Uses brief sleeps between ticks when the system timer is precise enough. Otherwise keeps the server's normal waiting behavior. |
| `PathfindingLimits` | `false` | Caps path length and search nodes using the `Early` settings. Keeps stricter NPC role limits. |

To generate less terrain for the world map, set `ViewRadiusMax` under `WorldMapConfig` in the world's `config.json`. This is a Hytale setting and needs no switch.

## Measured effect

These numbers come from a test server on Hytale 0.7.7 with one simulated player. They show what a performance switch did in one scenario. Results on other servers depend on players, entities, terrain and mods. Differences below about 3 percent of world-thread CPU could not be told apart from noise.

| Switch | Scenario | Result |
|--------|----------|--------|
| `SoundHeaderValidation` | Server boot, six boots with the switch on and six with it off | Boot was 0.45 seconds faster (6.9 percent). The server read 0.85 GB less from disk before boot. |
| `SpawnManagerRecalc` | Server boot | 271 spawn recalculations during startup became 1. The time saved was not measured. |
| `RepulsionTicker` | 250 NPCs that were each given a repulsion component | World-thread allocation was 2.8 percent lower. CPU did not change. Hytale 0.7.7 with stock assets gives no entity a repulsion component, so the switch has no effect there. |
| `CollisionConfig` | 250 walking NPCs, about 5,550 collision checks per tick | No measurable change in CPU or allocation. |
| `SkipEmptyLightSections`, `FluidDiscovery`, `AssetPartSnapshotSharing` | The NPC scenario above | The scenario never ran their code. Not measured. |
| `WorldMapExistingTerrain` | One player flying at 13 blocks per second with view radius 12 on a RocksDB world | Generated columns fell from 21.1 to 10.2 per second. Garbage collection pauses fell from 10.8 to about 5 ms per second. The map held about 700 images instead of 1,793. |
| `WorldHeightBounds` | The same flight at view radius 12 and 24 | No clear change in server CPU or garbage collection. Sections outside the standard height were 47.8 percent of all sections sent at view radius 12 and 73.6 percent at view radius 24, and the switch stops sending those. They carried about 5 percent of the section bytes. |
| `KDTree`, `RocksDbCompression`, `CylinderVisibility`, `ChunkStreaming`, `TickWait`, `PathfindingLimits` | None | Not measured. |

The seven optimizations that are on by default lowered world-thread allocation by 2.8 percent together in the NPC scenario. Refixes as a whole showed no measurable cost or gain there.

To see what the performance patches do on your own server, turn on `Mixins.Diagnostics.PatchCounters` and run `/patchcounters`. See [Diagnostics](#diagnostics).

## Features
Section: `Mixins.Features`

Optional world behavior and storage access used by shared worlds.

| Key | Default | Description |
|-----|---------|-------------|
| `OriginRetention` | `false` | Keeps the origin column and its sections 0-9 loaded when `Early.VanillaKeepSpawnLoaded` is enabled. |
| `SharedInstances` | `false` | Shares instance worlds between players. Can change consumable dungeon behavior. |
| `SharedInstanceStorage` | `true` | Keeps the saved data of `instance-shared-` worlds from being deleted on removal or restart while `Runtime.Compatibility.ExistingSharedInstances` is enabled. Does not create shared worlds. |
| `WorldPauseCommand` | `false` | Lets `/world pause` toggle pause in an empty multiplayer world. |

`SharedInstances` requires `Mixins.Features.SharedInstanceStorage` in `Refixes.json`. It also requires `Runtime.Compatibility.ExistingSharedInstances` in `config.json`. Startup stops if either requirement is disabled while shared-instance creation is enabled.

`SharedInstanceStorage` is also used for existing shared worlds when new shared-instance creation is disabled. See [Configuration](configuration.md) for storage and reset behavior.

## Recovery
Section: `Mixins.Recovery`

All recovery options default to `false`. They can discard saved data or skip failed work rather than fix its cause. Back up affected worlds before enabling options that change saved data.

| Key | Default | Description |
|-----|---------|-------------|
| `ShutdownSaveTimeout` | `false` | Limits the store resource save, world config save and indexed storage close during server shutdown to `Early.ShutdownSaveTimeoutSeconds`. If the server first waits its own 10 seconds for the world config save, this limit starts after that wait. Chunk saves are still awaited in full. Saves that time out may not finish before the process exits. |
| `TickSurvival` | `false` | Skips failed tick work. Rethrows save exceptions after three failures in the same saving system within five minutes. |
| `CorruptSectionReset` | `false` | Replaces unreadable section data with an empty section and keeps the original bytes in `mods/IroriPowered_Refixes/quarantine/block-sections/` as `block-section-<world>-<x>.<y>.<z>-<id>.bin`, or as `block-section-<id>.bin` if it cannot be labelled. Can remove blocks. A section still fails to load if its format is unsupported or the quarantine file cannot be written. |
| `MotionControllerBase` | `false` | Resets nonfinite NPC motion to zero. |
| `TurnOffTeleportersSystem` | `false` | Defers teleporter updates outside chunk loading callbacks. |
| `InvalidSavedHolders` | `false` | Drops saved entities with missing or empty component data and marks the chunk for saving. Dropped entities are lost. |
| `UnresolvedNPCRoles` | `false` | Preserves saved NPCs until their roles can load. The server still logs its own SEVERE line for each NPC whose role is missing. Live NPCs keep their current role when a role change targets a role that cannot load. |
| `InvalidCollisionData` | `false` | Skips block collision checks when the position or movement is nonfinite or the hitbox minimum exceeds its maximum. |
| `InteractionChain` | `false` | Tolerates interaction sync offset gaps and discards stale temporary sync data when the sync position advances. |
| `WorldMapTracker` | `false` | Clears pending map reloads with the tracker, fixes the reload list size and guards null references during map image unloading. |
| `WorldSpawningSystem` | `false` | Returns no chunk when random spawn selection fails. The warning is logged at most once per minute. |
| `DuplicateComponentReferences` | `false` | Keeps the newer block entity holder when a section holds two for the same block. Ignores a saved holder when a live block entity already exists and replaces stale references on load. When two live block entities claim one block, the newer one is removed. |

Transfer cleanup under `Mixins.Correctness.WorldTransfer` runs only while `Early.ShutdownSaveTimeoutSeconds` is above `0`, even when `ShutdownSaveTimeout` is `false`. The timeout does not limit that cleanup.

## Compatibility
Section: `Mixins.Compatibility`

Compatibility with authentication, updates, saved configuration and client behavior.

| Key | Default | Description |
|-----|---------|-------------|
| `DisableLiveConfig` | `false` | Stops all remote live config refreshes when enabled. Feature flags keep their local defaults. |
| `PageManager` | `true` | Ignores unexpected legacy custom-page acknowledgements. |
| `ServerAuthManager` | `true` | Supports external session authentication with OAuth tokens from the `--oauth-access-token`, `--oauth-refresh-token` and `--oauth-access-expires` options or the matching `HYTALE_SERVER_OAUTH_*` environment variables. Refreshed tokens are written back to `../.hytale-auth.json` when that file exists. |
| `UpdateCheckCommand` | `true` | Accepts identity tokens for `/update check`. |
| `UpdateDownloadCommand` | `true` | Accepts identity tokens for `/update download`. |
| `UpdateModule` | `true` | Accepts identity tokens for module update checks. |
| `WorldConfig` | `true` | Persists spawn provider changes. |

### Runtime support hooks

Runtime settings select these hooks automatically at startup while the switches below remain `true`. These switches do not enable the runtime features themselves. Disabling a needed hook can leave a feature or part of it inactive.

| Key | Default | Description |
|-----|---------|-------------|
| `PlayerRadius` | `true` | Tracks radius changes for `Runtime.Tuning.PerPlayerHotRadius` and `Runtime.Tuning.IdlePlayerHandler`. |
| `ServerViewRadius` | `true` | Tracks view-radius changes for `Runtime.Tuning.PerPlayerHotRadius.MemoryGuardEnabled`. |
| `AiTickThrottler` | `true` | Provides movement access and freeze ownership for `Runtime.Tuning.AiTickThrottler.Enabled` and keeps throttle freezes out of world saves. |
| `IdleWorldPause` | `true` | Tracks pauses owned by `Runtime.Tuning.IdleWorldPause.Enabled`. |
| `UnknownBlockCleaner` | `true` | Speeds up `ScanContainers` and `ScanFluids` under `Runtime.Recovery.UnknownBlockCleaner` when the cleaner is enabled. Without it both scans still run but check every block. |
| `Watchdog` | `true` | Provides world-thread access for `Runtime.Recovery.Watchdog.AutoRestartWorlds`. |

`Mixins.Correctness.WorldOwnership` also loads the world-thread accessor. Disabling `Mixins.Compatibility.Watchdog` alone does not remove it.

## Diagnostics
Section: `Mixins.Diagnostics`

Reporting controls. Disabling system metrics is optional.

| Key | Default | Description |
|-----|---------|-------------|
| `SkipSystemMetrics` | `false` | Disables system timing metrics. |
| `HytaleServer` | `true` | Warns when the runtime Refixes plugin is not enabled. |
| `SpawnMarkerBlockStateHeartbeat` | `true` | Reports spawn marker heartbeat messages at warning level. |
| `PatchCounters` | `false` | Counts how often each performance patch was reached and how often it changed the outcome since server start. Read the numbers with `/patchcounters`. Needs a restart to take effect. |

With `PatchCounters` on, `/patchcounters` prints one line for each counted performance switch. Each line shows the switch name, whether the patch is on or off and two numbers. The first number counts how often the server reached the patched code. The second counts how often the patch changed what happened, for example a cache hit, a skipped section, a header-only read, a shared snapshot or a lowered path limit.

A first number of zero means the server never ran that code since it started, so the patch does nothing on this server as it is used now. A patch that is off always shows zero. Both numbers count events, not time saved. A high second number shows the patch is doing work but does not say how much CPU it saved.

`RocksDbCompression`, `CylinderVisibility` and `ChunkStreaming` have no counter because they only apply settings or because their effect cannot be counted cheaply. The counters start at zero on every server start. Counting costs nothing while the switch is off.

## Experimental
Section: `Mixins.Experimental`

These options are experimental and default to `false`. They change pathfinding or concurrency behavior. Test them on a copy of the server before using them with players.

| Key | Default | Description |
|-----|---------|-------------|
| `PathfindingBudget` | `false` | Limits new searches and node expansions per world tick using `Early.PathfindingMaxNewSearchesPerTick` and `Early.PathfindingMaxNodeExpansionsPerTick`. Can delay NPC pathfinding. NPCs that are attacking or have a marked target are exempt. |

### Parallel
Section: `Mixins.Experimental.Parallel`

| Key | Default | Description |
|-----|---------|-------------|
| `CollectVisible` | `false` | Collects visible entities in parallel. |

Requires `Mixins.Correctness.ParallelTaskLifecycle`. Startup stops if `CollectVisible` is enabled without it.
