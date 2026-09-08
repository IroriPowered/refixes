Early patch toggles live in `mods/IroriPowered_Refixes/Refixes.json`, separate from runtime `config.json`. Missing or invalid booleans use the defaults below. See [Configuration](Configuration) for runtime settings and [Home](Home) for installation.

# Mixins
Section: `Mixins`

## Optimizations
Section: `Mixins.Optimizations`

Enabled by default except `SkipSystemMetrics`.

| Key | Default | Description |
|-----|---------|-------------|
| `FluidPlugin` | `true` | Skips fluid processing before chunk load. |
| `BlockModule` | `true` | Skips block processing before chunk load. |
| `CollectVisible` | `true` | Uses a vertically limited cylinder for entity visibility queries. |
| `CollisionConfig` | `true` | Caches the last fluid section used for collision checks. |
| `CollisionAirFastPath` | `true` | Skips block, filler and rotation lookups in empty sections. |
| `SkipSystemMetrics` | `false` | Saves CPU by disabling system timing metrics. |
| `KDTree` | `true` | Uses a cheaper coordinate sort for small spatial indexes. |
| `ChunkUnloadingSystem` | `true` | Keeps the origin column loaded with `VanillaKeepSpawnLoaded`. |
| `PlayerChunkTrackerSystems` | `true` | Applies section streaming limits and minimum loaded radius on player join. |
| `SpawnManagerRecalc` | `true` | Combines redundant spawn recalculations into one deferred pass per world. |
| `AStarBase` | `true` | Uses binary search for open node insertion and configurable pathfinding limits. |
| `RepulsionTicker` | `true` | Reuses a pooled buffer for entity repulsion. |

## Experimental
Section: `Mixins.Experimental`

Disabled by default. These can affect stability or gameplay. Test one change at a time.

| Key | Default | Description |
|-----|---------|-------------|
| `ShutdownSaveTimeout` | `false` | Bounds Store resource saves during shutdown. |
| `PathfindingBudget` | `false` | Limits A* work per tick, excluding attacking NPCs and those with marked targets. |
| `SkipEmptyLightSections` | `false` | Skips propagation from empty sections containing no light. |
| `SharedInstances` | `false` | Reuses instance worlds across players, with configurable prefix exclusions. Can break consumable dungeon content. |
| `ConnectionBackpressure` | `false` | Limits outbound buffering for slow clients. |
| `TickSurvival` | `false` | Logs exceptions and skips the failed system's remaining work for that tick. Can mask bugs. Save systems retry, then rethrow after three failures within five minutes. `Error`s still propagate. |

### Parallel
Section: `Mixins.Experimental.Parallel`

All disabled by default. The independent group needs no `RelaxStoreAsserts` and remains serial below its parallelism threshold.

| Key | Default | Description |
|-----|---------|-------------|
| `CollectVisible` | `false` | Collects visibility across workers, reading the shared index and writing each viewer's own `visible` set. |
| `SpatialCollection` | `false` | Collects positions into worker buffers before the serial tree rebuild. Ordinary failures drain siblings without merging results. |

The advanced group permits reads only in the exact active store. Structural writes and reuse of tick, fetch or parallel iteration tasks remain restricted to the owner thread. Mutable components and arbitrary callbacks are not universally safe across threads. Watch relaxed assertion logs.

| Key | Default | Description |
|-----|---------|-------------|
| `AllSystems` | `false` | Enables systems using `maybeUseParallel` on large chunks. `ItemPrePhysicsSystem` stays on the owner thread for chunk store access. Forces the helpers and both replication companions below. |
| `Steering` | `false` | Forces NPC steering parallel despite the engine's chunk access restriction. Forces the `RelaxStoreAsserts` helpers. |
| `RelaxStoreAsserts` | `false` | Enables scoped store reads, processing counter synchronization, notification deferral and task context helpers. Logs relaxed call sites. Workers still cannot bypass structural write guards, including the legacy processing assertion override. |
| `FluidReplicateChanges` | `false` | Defers fluid replication packets to the merge phase. Required by `AllSystems`. |
| `ChunkReplicateChanges` | `false` | Defers block replication packets to the merge phase. Required by `AllSystems`. |

Dependencies override explicit `false` values at boot without rewriting those values in the file.

## Crashfixes
Section: `Mixins.Crashfixes`

Enabled by default.

| Key | Default | Description |
|-----|---------|-------------|
| `BlockSectionSafety` | `true` | Replaces corrupt block section data with an empty section. |
| `MotionControllerBase` | `true` | Resets nonfinite NPC motion to zero. |
| `TurnOffTeleportersSystem` | `true` | Defers teleporter updates outside chunk loading callbacks. |
| `EntityChunkLoadingSystem` | `true` | Skips broken entity records and saves the repaired chunk. |
| `CollisionModule` | `true` | Rejects invalid positions and empty or inverted hitboxes. |
| `PageManager` | `true` | Ignores unexpected client page acknowledgements. |
| `TeleportToPlayerCommand` | `true` | Avoids stale teleport history references in `/tp <player>` across worlds. |
| `DeployableOwnerComponent` | `true` | Prunes stale deployable references after world changes. |
| `MountPlugin` | `true` | Guards invalid rider references. |
| `DespawnSystem` | `true` | Skips despawn timers with no instant. |
| `ChunkStoreSectionBackoffDeadlock` | `true` | Avoids deadlock when section loading fails under the chunk store write lock. |

## Helpers
Section: `Mixins.Helpers`

Supporting patches, enabled by default. Accessors have no standalone effect.

| Key | Default | Description |
|-----|---------|-------------|
| `ArchetypeChunk` | `true` | Logs and skips component access outside valid entity indexes. |
| `BeaconAddRemoveSystem` | `true` | Despawns NPCs missing their beacon spawn controller. |
| `BlockComponentChunk` | `true` | Logs and ignores duplicate component references and holders. |
| `BlockHealthSystem` | `true` | Catches and logs null pointer exceptions during block health ticks. |
| `CommandBuffer` | `true` | Guards buffered removals against invalid entity references. |
| `EntityViewer` | `true` | Skips updates and removals for entities outside the viewer's visible set. |
| `FluidSection` | `true` | Exposes unknown fluid detection for the runtime cleaner. |
| `GamePacketHandler` | `true` | Catches and logs null pointer exceptions in movement packets. |
| `HytaleServer` | `true` | Warns at boot if the main plugin is missing. |
| `InteractionChain` | `true` | Tolerates interaction sync offset gaps. |
| `MarkerAddRemoveSystem` | `true` | Discards spawn markers whose add or removal callbacks fail. |
| `NPCKillsEntitySystem` | `true` | Returns null for an invalid NPC killer reference. |
| `Options` | `true` | Registers Refixes launch options. |
| `PlayerViewRadius` | `true` | Sets minimum loaded radius from client view radius plus a configurable offset. |
| `PortalDeviceSummonPage` | `true` | Prevents duplicate shared instance return portals and handles missing spawn transforms. |
| `PortalWorldAccessor` | `true` | Exposes the portal world's removal condition. |
| `PrefabListExtraRoots` | `true` | Adds a searchable WorldGen prefab root and opens the browser at its top level. |
| `RemovalSystem` | `true` | Keeps shared portal worlds until their lifetime expires. |
| `ServerAuthManager` | `true` | Persists and refreshes OAuth tokens for external session authentication. |
| `SetMemoriesCapacityInteraction` | `true` | Handles missing player memories components. |
| `SpawnMarkerBlockStateHeartbeat` | `true` | Warns about unresolved spawn marker heartbeat references. |
| `StateSupport` | `true` | Discards NPC role updates with incorrect store references. |
| `TickingSpawnMarkerSystem` | `true` | Despawns NPCs whose spawn marker tick uses an incorrect store reference. |
| `TickSleep` | `true` | Reserves the final 1ms for brief parking instead of busy spinning. |
| `TickingThread` | `true` | Preserves exact owner identity for `isInThread()`. Workers are not world threads. |
| `TickingThreadAssert` | `true` | Suppresses a harmless shutdown thread assertion. |
| `ChunkStoreAccess` | `true` | Exposes the cubic chunk loader and saver for storage wrapping. |
| `TrackedPlacementAccessor` | `true` | Exposes block names for `TrackedPlacementOnAddRemove`. |
| `TrackedPlacementOnAddRemove` | `true` | Logs and skips missing tracked placements or block names on removal. |
| `UUIDSystem` | `true` | Skips removal steps when the UUID component is missing. |
| `UpdateCheckCommand` | `true` | Accepts identity tokens for `/update check`. |
| `UpdateDownloadCommand` | `true` | Accepts identity tokens for `/update download`. |
| `UpdateModule` | `true` | Accepts identity tokens for module update checks. |
| `WorldPauseCommand` | `true` | Allows `/pause` in empty multiplayer worlds. |
| `World` | `true` | Guards `getPlayers()`, retries player join races, caps shutdown config saves at 10 seconds and manages pathfinding budgets. |
| `WorldConfig` | `true` | Persists spawn provider changes. |
| `WorldMapTracker` | `true` | Guards null references during map image unloading. |
| `WorldSpawningSystem` | `true` | Returns no chunk when random spawn selection fails. |

# Hypixel Services
Section: `HypixelServices`

| Key | Default | Description |
|-----|---------|-------------|
| `LiveConfig` | `false` | Skips remote startup refresh and uses local feature flag defaults. |
