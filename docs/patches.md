# Patches

`mods/IroriPowered_Refixes/Refixes.json` turns early patches on and off. Shared rules are in [Settings files](/mod/refixes/installing-refixes#settings-files).

Use the exact `Mixins` section paths below. On other Hytale versions some switches stay off, see [Other server versions](/mod/refixes/installing-refixes#other-server-versions) and [Exact-version switches](/mod/refixes/installing-refixes#exact-version-switches).

Startup stops when an enabled switch requires a switch that is off, see [Startup errors](/mod/refixes/troubleshooting#startup-errors).

This page covers Refixes 0.6.x for Hytale 0.7.7 and Refixes 0.8.0-beta.1 for Hytale 0.8.0-pre.6. Rows marked **0.6.x only** or **0.8 only** exist only in that version, and text marked **0.6.x** inside a row applies only there. A switch your version lacks is removed from `Refixes.json` at startup.

## Correctness
Section: `Mixins.Correctness`

| Key | Default | Description |
|-----|---------|-------------|
| `MountPlugin` | `true` | Guards invalid rider references. |
| `OutgoingQueueLimits` | `true` | Limits each connection's outgoing packet queue, see [Outgoing packet queues](/mod/refixes/configuration#outgoing-packet-queues). Requires `SetupPacing`. On Quiche, a client that stops the main, chunk or world map stream is disconnected. |
| `SetupPacing` | `true` | Paces setup asset transfers to the client's speed. Works only while `OutgoingQueueLimits` is on. |
| `QuicCloseRetention` | `true` | Keeps a closing Netty QUIC connection for up to 2 seconds so the client sees the disconnect reason. |
| `ChunkSectionFences` | `true` | Keeps chunk section updates in order, so clients do not keep outdated blocks, fluids, tints or environments. |
| `MarkerAddRemoveSystem` | `true` | Prevents "Incorrect store for entity reference" errors after an NPC changes world. |
| `TickingSpawnMarkerSystem` | `true` | Applies the same check in the marker heartbeat and keeps the despawn delay while the NPC is busy. |
| `TrackedPlacementOnAddRemove` | `true` | Skips the block count update for a removed placement without a block name. |
| `WindowManager` | `true` | Unregisters a window when opening it fails. |
| `ConfigLoad` | `true` | Orders configuration saves and fixes reloads after a load. |
| `PluginPreload` | `true` | Unloads a plugin whose configuration fails to load on a running server, so it can be loaded again. |
| `PluginLoaderCache` | `true` | Reads a plugin JAR again after a failed or skipped load. |
| `WorldTransfer` | `true` | Guards player and entity transfers between worlds. **0.6.x:** requires `StorageOrdering` and keeps a moved entity in the source world's save until the target world has saved it. On 0.8, Hytale deletes the source copy first, so a crash in between loses the entity. |
| `StorageOrdering` | `true` | Finishes pending chunk and entity saves before a world closes and writes each chunk column in order. On 0.8 it also runs a second save pass when a world stops. |
| `ChunkSaveQueueOrder` | `true` | Saves changed chunk columns in the order they changed, so every column is saved within one pass instead of some never. Costs world-thread CPU when those columns hold many NPCs. |
| `BlockChangeSaving` | `true` | Saves block changes from random ticks and multi-block updates, such as grass spreading, which Hytale otherwise loses on a stop or crash. |
| `TransferCombatClock` | `true` | Moves an entity's combat and damage timers to the target world's clock when it changes world. |
| `ParallelTaskLifecycle` | `true` | Waits for parallel workers before reusing tasks and reports their failures. |
| `ChunkStoreSectionBackoffDeadlock` | `true` | Avoids a deadlock when chunk or section loading fails. |
| `DespawnSystem` | `true` | Avoids an error for entities without despawn data. |
| `CommandBuffer` | `true` | Guards buffered removals against invalid or foreign entity references. |
| `EntityViewer` | `true` | Skips updates for entities outside the viewer's visible set. |
| `EffectConditionClock` | `true` | Checks effect conditions against world time instead of the system clock. |
| `AssetInheritance` | `true` | Loads contained assets in dependency order. |
| `NPCKillsEntitySystem` | `true` | Treats invalid or cross-world NPC killer references as missing. |
| `PortalDeviceSummonPage` | `true` | Prevents duplicate shared instance return portals and handles missing spawn points. |
| `RemovalSystem` | `true` | Defers shared world removal during startup and respects portal lifetimes. |
| `StateSupport` | `true` | Prunes invalid player references from NPC interaction state. |
| `DeployableOwnerComponent` | `true` | Prunes stale deployable references after world changes. |
| `TeleportToPlayerCommand` | `true` | Avoids stale teleport history in `/tp <player>` across worlds. |
| `TickingThreadStart` | `true` | Completes startup when a ticking thread fails to start. |
| `WorldOwnership` | `true` | Checks ownership during world creation and removal. |

While `ChunkSectionFences` is on, a backlog delays chunk section updates.

### 0.6.x only
Section: `Mixins.Correctness`

Hytale 0.8.0-pre.5 makes these fixes itself.

| Key | Default | Description |
|-----|---------|-------------|
| `PacketAdapters` | `true` | Runs inbound packet filters once on both transports. |
| `RocksDbFlush` | `true` | Flushes all RocksDB data columns on save and close. |
| `WorldGenReload` | `true` | Releases the saving lock when chunk deletion ends. |
| `WorldMapPacing` | `true` | Limits world map images sent to a player per update. |
| `WorldMapCache` | `true` | Keeps a newer world map image request when an older one finishes. |
| `WorldEventSaving` | `true` | Saves world events in order. The shutdown wait is set by [`Early.ShutdownSaveTimeoutSeconds`](/mod/refixes/configuration#early-patches). |
| `WorldReopenGuard` | `true` | Waits for a removed world's cleanup before its folder is opened again, and refuses after 30 seconds. |
| `EntityModule` | `true` | Keeps entity component types available while the entity module is disabled. |
| `TeleportInstanceInteraction` | `true` | Skips a missing return point offset. |
| `TickingThreadStop` | `true` | Keeps world saves running while a removed world stops. |
| `SerializableArchetype` | `true` | Fixes saved entity copies so adding a component does not fail. |
| `WorldRetirement` | `false` | Keeps a stopping world's save folder reserved until its thread has exited, also for instance copies and creative hub worlds. Requires `WorldReopenGuard`. |
| `StoredFlockSaving` | `true` | Stops marker NPCs from being duplicated or lost after a restart. |
| `FluidChangeSaving` | `true` | Saves fluid changes, so fluid flow survives unload and restart. |
| `FluidRelightSection` | `true` | Relights the section where the fluid changed instead of the wrong column. |
| `ServerConfigSaveChain` | `true` | Runs server `config.json` saves one after another so they cannot clash. |
| `TickingThreadAbandon` | `true` | A stop timeout no longer takes over a thread already running its shutdown hook. |
| `WorldTaskQueueClose` | `true` | Rejects or cancels tasks offered to a stopping world instead of stranding them. |
| `ServerShutdownWatchdog` | `true` | Shutdown waits at most 10 seconds for the config save, and the 3 second halt watchdog runs on its own thread. |
| `WorldMapTickingStop` | `true` | Toggling the compass or world map no longer stalls the world for up to 30 seconds. |
| `UniverseDeletedCleanup` | `true` | A locked file in `worlds-deleted` logs a warning and is retried next start instead of stopping startup. |
| `MountMovementTeleport` | `true` | Ignores mount movement until the rider acknowledges a pending teleport. |
| `CommonAssetHashCleanup` | `true` | The server no longer deletes `.hash` files inside zip or jar asset packs. |
| `ObjectiveEntityRemoval` | `true` | Reverting or completing an objective skips unloaded worlds and removes its entity safely. |
| `FluidDropSection` | `true` | The fluid drop-distance check reads the correct section below. |
| `SoundChannelEncoding` | `true` | Sound validation accepts files with more than 2 channels. |
| `MessageSelect` | `true` | Text `select` placeholders match on the parameter's value. |
| `ServerManagerShutdown` | `true` | Shutdown no longer fails when the network transport never started. |
| `EntitySectionLoad` | `true` | An entity moving into an unloaded section starts one load instead of one per tick and is no longer removed by a late answer. |
| `PrefabPasteLoaded` | `true` | The prefab editor skips a prefab whose area could not fully load and tells the player, instead of saving a truncated prefab over the original. |
| `MapMarkerLocking` | `true` | Block map markers changed during a world map update no longer crash the map thread. |
| `PortalDeviceWorldClose` | `true` | Breaking a portal device closes its world in the background instead of stalling the origin world. |
| `RocksDbColumnRemoval` | `true` | On RocksDB worlds, removing a chunk column deletes all its data, so old terrain and entities no longer come back. |
| `RocksDbScanBounds` | `true` | On RocksDB worlds, chunk loads after a large removal no longer slow down. |

## Backports
Section: `Mixins.Backports`

**0.6.x only.** Fixes from Hytale 0.8.0-pre.6.

| Key | Default | Description |
|-----|---------|-------------|
| `ShutdownRemoval` | `true` | A server stop removes worlds normally instead of as crashed, which logged `Removing world exceptionally`. Plugins get reason `GENERAL` instead of `EXCEPTIONAL` and no default world reload is scheduled. |
| `ShutdownSaveFailure` | `true` | A chunk that fails to save while a world stops no longer skips the rest of the stop, so game time and the `resources` folder are still saved. Logs `Some chunks failed to save on shutdown`. |
| `StaticLayers` | `true` | Newly generated terrain gets every static layer of its biome, including bedrock, which some biomes such as lava beaches and deserts lacked. Existing terrain is unchanged, so old and new columns can differ at the bottom. |
| `ModelParticleScale` | `true` | Particles stay on scaled models instead of drifting until players could be disconnected. |

## Optimizations
Section: `Mixins.Optimizations`

| Key | Default | Description |
|-----|---------|-------------|
| `SpawnManagerRecalc` | `false` | Combines pending spawn recalculations. |
| `RepulsionTicker` | `false` | Reuses entity repulsion buffers. |
| `RepulsionNeighbors` | `false` | Skips sorting repulsion neighbors by distance. Every neighbor still gets the same push. |
| `LightNeutralBlocks` | `false` | Reuses a section's light when block changes keep opacity and light color, such as grass turning into dirt. Same result. |
| `SkipUnchangedLightSaves` | `false` | Saves a section after a relight only when its light changed. Requires `Mixins.Correctness.BlockChangeSaving` and, **0.6.x**, `Mixins.Correctness.FluidChangeSaving`. |
| `RocksDbCompression` | `false` | **0.6.x only.** On RocksDB worlds, compresses chunk data and logs with zstd and lowers the log size target from 512 MiB to 128 MiB. |
| `SoundHeaderValidation` | `true` | **0.6.x only.** Validates sound headers without loading whole files. |
| `SkipEmptyLightSections` | `false` | **0.8 only.** Skips light propagation out of neighboring sections that hold only air and no light. |
| `AssetPartSnapshotSharing` | `false` | Reuses identical asset packet snapshots across clients. |
| `NPCComponentType` | `false` | **0.6.x only.** Caches the NPC component type. |
| `DefaultMovementResistance` | `false` | Shares one default movement resistance for NPC pushes. |
| `CodecMetadata` | `true` | Shares one metadata map while saving and loading data. |

With `RocksDbCompression` on, `-Dhytale.rocksdb.max_total_wal_size=<bytes>` overrides the log size target. An invalid value can stop startup.

## Tuning
Section: `Mixins.Tuning`

| Key | Default | Description |
|-----|---------|-------------|
| `CylinderVisibility` | `false` | Uses a cylinder instead of a sphere for entity visibility, which by default shows more entities above and below players. See `Early.CylinderVisibility`. |
| `ChunkStreaming` | `false` | Applies `Early.MaxSectionsPerSecond`, `Early.MaxSectionsPerSecondLan`, `Early.MaxSectionsPerSecondLocal`, `Early.MaxSectionsPerTick` and `Early.UnloadDistanceOffset`. |
| `WorldHeightBounds` | `false` | **0.6.x only.** On RocksDB worlds, stops sending sections outside the standard world height. Data stored there stays but is hidden from players. Worlds whose generator builds outside that height are unchanged. |
| `WorldMapExistingTerrain` | `false` | Draws the world map only from existing terrain instead of generating it. A chunk appears once it and its eight neighbors exist. Fixed-area maps are unchanged. |
| `TickWait` | `true` | Lowers CPU spent waiting between world ticks. Falls back to normal waiting on hosts with imprecise sleep. |
| `PathfindingLimits` | `false` | Applies the per-search `Early.Pathfinding*` limits. |

## Measured effect

Measured on test servers with one switch on versus off. Your results depend on players, entities, terrain and mods.

Scenarios:
- **Boot:** a server boot.
- **N NPCs:** 250 or 800 NPCs on a flat world with one simulated player, **repulsion** if each NPC was given a repulsion component, which stock Hytale never does.
- **Flight:** one player flying at 13 blocks per second over a RocksDB world, view radius 12 or 24.
- **Spawn file change:** a world NPC spawn file changes on a server with 20 worlds and 2,420 loaded chunks.

Results on Hytale 0.8.0-pre.5 were not measured again on 0.8.0-pre.6.

| Switch | Scenario | Hytale 0.7.7 | Hytale 0.8.0-pre.5 |
|--------|----------|--------------|--------------------|
| `SoundHeaderValidation` | Boot | Boot 6.8 percent faster. 33 percent less read from disk before boot. | Not in this build. |
| `RepulsionTicker` | 250 NPCs, repulsion | World-thread allocation 2.7 percent lower. | World-thread allocation 2.9 percent lower. |
| `RepulsionNeighbors` | 250 NPCs, repulsion | World-thread CPU 1.4 percent, world-thread allocation 15.7 percent and process allocation 4.8 percent lower. | World-thread busy time 5.1 percent, world-thread CPU 2.8 percent, world-thread allocation 15.9 percent and process allocation 7.8 percent lower. |
| `DefaultMovementResistance` | 250 NPCs, repulsion | World-thread allocation 15.5 percent and process allocation 4.7 percent lower. | World-thread allocation 15.5 percent and process allocation 7.3 percent lower. |
| `CodecMetadata` | 250 NPCs, repulsion | World-thread allocation 1.5 percent and process allocation 3.5 percent lower. | World-thread allocation 1.3 percent and process allocation 5.7 percent lower. |
| `CodecMetadata` | 250 NPCs | World-thread allocation 2.0 percent and process allocation 4.0 percent lower. | World-thread allocation 3.6 percent and process allocation 7.5 percent lower. |
| `TickWait` | 250 NPCs, repulsion | World-thread CPU 35.3 percent lower. | World-thread CPU 35.3 percent lower. |
| `TickWait` | 800 NPCs, repulsion | World-thread CPU 3.1 percent lower. | World-thread CPU 3.0 percent lower. |
| `TickWait` | Flight, radius 12 | None listed. | World-thread CPU 34.4 percent and process CPU 8.9 percent lower. |
| `LightNeutralBlocks` | 250 NPCs, repulsion | Lighting CPU time about 20 seconds without the switch and none with it. Process CPU time 73.0 percent and world-thread CPU 1.0 percent lower. | Lighting CPU time about 20 seconds without the switch and none with it. Process CPU time 68.4 percent lower. |
| `LightNeutralBlocks` | Flight, radius 12 | Lighting CPU 6.5 percent and process CPU 1.5 percent lower. | Lighting CPU 13.2 percent lower. |
| `LightNeutralBlocks` | Flight, radius 24 | None listed. | Lighting CPU 12.0 percent lower. |
| `SpawnManagerRecalc` | Spawn file change | In the next 30 seconds, process CPU 20.4 percent and world-thread CPU 18.3 percent lower. | In the next 30 seconds, process CPU 10.2 percent lower. |
| `WorldMapExistingTerrain` | Flight, radius 12 | Generated columns per second halved. Process CPU 25.5 percent, world-thread CPU 3.8 percent, garbage collection pause time 60 percent and collections 64 percent lower. 701 map images instead of 1,793. | Generated columns per second halved. Process CPU 14.5 percent, world-thread CPU 4.7 percent, garbage collection pause time 40 percent and collections 44 percent lower. 701 map images instead of 1,793. |
| `WorldMapExistingTerrain` | Flight, radius 24 | Generated columns per second 27 percent lower. Process CPU 18.7 percent, world-thread CPU 3.2 percent, garbage collection pause time 29 percent and collections 34 percent lower. 2,155 map images instead of 3,209. | Generated columns per second 27 percent lower. Garbage collection pause time 55 percent and collections 46 percent lower. 2,155 map images instead of 3,209. |
| `WorldHeightBounds` | Flight, radius 12 | World-thread allocation 3.3 percent lower. Section bytes sent 1.4 percent lower. | Not in this build. |
| `WorldHeightBounds` | Flight, radius 24 | World-thread CPU 4.2 percent and allocation 10.7 percent lower. Section bytes sent 5.0 percent lower. | Not in this build. |

`TickWait` saves CPU only in worlds with players.

To see what the performance patches do on your server, use `/patchcounters`, see [Diagnostics](#diagnostics).

## Features
Section: `Mixins.Features`

| Key | Default | Description |
|-----|---------|-------------|
| `OriginRetention` | `false` | Keeps the chunk column at X 0, Z 0 loaded when `Early.VanillaKeepSpawnLoaded` is on. |
| `SharedInstances` | `false` | Shares instance worlds between players. Can change consumable dungeon behavior. Requires `SharedInstanceStorage` and `Runtime.Compatibility.ExistingSharedInstances` set to `true` in `config.json`. |
| `SharedInstanceStorage` | `true` | Keeps the saved data of `instance-shared-` worlds on removal or restart while `Runtime.Compatibility.ExistingSharedInstances` is on. Does not create shared worlds. Without it, see [Compatibility](/mod/refixes/configuration#compatibility). |
| `WorldPauseCommand` | `false` | Lets `/world pause` work in an empty multiplayer world. |

## Recovery
Section: `Mixins.Recovery`

These options can discard saved data or skip failed work instead of fixing its cause. Back up affected worlds before enabling options that change saved data.

| Key | Default | Description |
|-----|---------|-------------|
| `ShutdownSaveTimeout` | `false` | Limits shutdown saves to `Early.ShutdownSaveTimeoutSeconds`, see [`ShutdownSaveTimeoutSeconds`](/mod/refixes/configuration#early-patches). The process can exit before a timed-out save finishes. |
| `TickSurvival` | `false` | Skips failed tick work. Save failures still throw after three in the same saving system within five minutes. |
| `CorruptSectionReset` | `false` | Replaces unreadable section data with an empty section, which can remove blocks. The original bytes go to `mods/IroriPowered_Refixes/quarantine/block-sections/` as `block-section-<world>-<x>.<y>.<z>-<id>.bin`, or `block-section-<id>.bin` if unlabeled. A section with an unsupported format, or whose quarantine file cannot be written, still fails to load. On 0.8 it does nothing with `--verify-worlds`, so Hytale's own recovery handles the section. |
| `MotionControllerBase` | `false` | Resets nonfinite NPC motion to zero. |
| `TurnOffTeleportersSystem` | `false` | Defers teleporter updates outside chunk loading. |
| `UnresolvedNPCRoles` | `false` | Keeps saved NPCs until their roles can load. The server still logs a SEVERE line for each. Live NPCs keep their role when a role change targets one that cannot load. |
| `InvalidCollisionData` | `false` | Skips block collision checks for nonfinite positions or inverted hitboxes. |
| `InteractionChain` | `false` | Tolerates interaction sync gaps and discards stale sync data. |
| `WorldMapTracker` | `false` | **0.6.x only.** Fixes pending map reloads and null errors when map images unload. |
| `WorldSpawningSystem` | `false` | Returns no chunk when random spawn selection fails, warning at most once per minute. |
| `DuplicateComponentReferences` | `false` | Keeps the newer of two saved block entity holders for one block and ignores a saved holder when a live block entity exists. When two live block entities claim one block, the newer one is removed. |

## Compatibility
Section: `Mixins.Compatibility`

| Key | Default | Description |
|-----|---------|-------------|
| `DisableLiveConfig` | `false` | Stops remote live config refreshes. Feature flags keep their local defaults. |
| `ServerAuthManager` | `true` | Supports external session authentication with the `--oauth-access-token`, `--oauth-refresh-token` and `--oauth-access-expires` options or the matching `HYTALE_SERVER_OAUTH_*` environment variables. Refreshed tokens are written back to `../.hytale-auth.json` if it exists. |
| `UpdateCheckCommand` | `true` | Accepts identity tokens for `/update check`. |
| `UpdateDownloadCommand` | `true` | Accepts identity tokens for `/update download`. |
| `UpdateModule` | `true` | Accepts identity tokens for module update checks. |
| `WorldConfig` | `true` | Saves spawn provider changes. |

### Runtime support hooks

These hooks let the runtime settings in `config.json` work. They do not turn the features on. Without its hook a feature stays off and logs that it is inactive.

| Key | Default | Description |
|-----|---------|-------------|
| `PlayerRadius` | `true` | Needed by `Runtime.Tuning.PerPlayerHotRadius` and `Runtime.Tuning.IdlePlayerHandler`. |
| `ServerViewRadius` | `true` | Needed by `Runtime.Tuning.PerPlayerHotRadius.MemoryGuardEnabled`. |
| `AiTickThrottler` | `true` | Needed by `Runtime.Tuning.AiTickThrottler.Enabled`. Keeps throttle freezes out of world saves. |
| `IdleWorldPause` | `true` | Needed by `Runtime.Tuning.IdleWorldPause.Enabled`. |
| `UnknownBlockCleaner` | `true` | Speeds up `ScanContainers` and `ScanFluids` of `Runtime.Recovery.UnknownBlockCleaner`. Without it both scans still run, slower. |
| `Watchdog` | `true` | Supports `Runtime.Recovery.Watchdog.AutoRestartWorlds`, see [Watchdog Recovery](/mod/refixes/configuration#watchdog-recovery). |

## Diagnostics
Section: `Mixins.Diagnostics`

| Key | Default | Description |
|-----|---------|-------------|
| `HytaleServer` | `true` | Warns when the runtime Refixes plugin is not enabled. |
| `PatchCounters` | `false` | Counts patch activity since server start for `/patchcounters`. |

`/patchcounters` prints each counted switch, whether it is on, how often the server reached the patched code and how often the patch changed the outcome. A first number of zero means the patch does nothing on this server as it is used now. The numbers count events, not time saved.

| Command | Permission |
|---------|------------|
| `/patchcounters` | `hytale.command.refixes.patchcounters` |

`RocksDbCompression`, `CylinderVisibility`, `ChunkStreaming`, `PathfindingBudget` and `Parallel.CollectVisible` have no counter.

## Experimental
Section: `Mixins.Experimental`

These options change pathfinding or concurrency. Test them on a copy of the server first.

| Key | Default | Description |
|-----|---------|-------------|
| `PathfindingBudget` | `false` | Applies the per-tick `Early.PathfindingMaxNewSearchesPerTick` and `Early.PathfindingMaxNodeExpansionsPerTick` limits. Can delay NPC pathfinding. NPCs that are attacking or have a marked target are exempt. |
| `Parallel.CollectVisible` | `false` | Collects visible entities in parallel. Requires `Mixins.Correctness.ParallelTaskLifecycle`. |
