Patch switches live in `mods/IroriPowered_Refixes/Refixes.json`. Restart after editing. Missing or invalid values use defaults. Malformed JSON stops startup. See [Configuration](Configuration) for runtime settings.

Use the paths below. Old paths are not migrated and are removed on save.

# Correctness
Section: `Mixins.Correctness`

Fixes that preserve intended server behavior.

| Key | Default | Description |
|-----|---------|-------------|
| `MountPlugin` | `true` | Guards invalid rider references. |
| `TrackedPlacementOnAddRemove` | `true` | Logs and skips missing tracked placements or block names during removal. |
| `WorldNullGuard` | `true` | Returns an empty player list before the entity store is available. |
| `WorldEventSaving` | `true` | Saves world events in order. |
| `WorldTransfer` | `true` | Cancels stale player transfer retries. |
| `ParallelTaskLifecycle` | `true` | Fixes parallel task reuse. |
| `ChunkStoreSectionBackoffDeadlock` | `true` | Avoids deadlock when section loading fails under the chunk store write lock. |
| `DespawnSystem` | `true` | Skips despawn timers with no instant. |
| `CommandBuffer` | `true` | Guards buffered removals against invalid entity references. |
| `EntityViewer` | `true` | Skips updates and removals for entities outside the viewer's visible set. |
| `NPCKillsEntitySystem` | `true` | Returns null for an invalid NPC killer reference. |
| `PortalDeviceSummonPage` | `true` | Prevents duplicate shared instance return portals and handles missing spawn transforms. |
| `RemovalSystem` | `true` | Keeps shared portal worlds until their lifetime expires. |
| `SetMemoriesCapacityInteraction` | `true` | Handles missing player memories components. |
| `StateSupport` | `true` | Prunes invalid or foreign-store player references from NPC interaction state. |
| `UUIDSystem` | `true` | Skips removal steps when the UUID component is missing. |
| `DeployableOwnerComponent` | `true` | Prunes stale deployable references after world changes. |
| `TeleportToPlayerCommand` | `true` | Avoids stale teleport history references in `/tp <player>` across worlds. |

# Optimizations
Section: `Mixins.Optimizations`

Stable optimizations, enabled by default.

| Key | Default | Description |
|-----|---------|-------------|
| `CollisionConfig` | `true` | Caches the last fluid section used for collision checks. |
| `KDTree` | `true` | Uses a cheaper coordinate sort for small spatial indexes. |
| `SpawnManagerRecalc` | `true` | Combines pending spawn recalculations. |
| `RepulsionTicker` | `true` | Reuses entity repulsion buffers. |
| `SkipEmptyLightSections` | `true` | Skips empty light sections. |

# Tuning
Section: `Mixins.Tuning`

Optional changes to visibility, streaming, waiting and pathfinding limits.

| Key | Default | Description |
|-----|---------|-------------|
| `CylinderVisibility` | `false` | Uses cylindrical entity visibility. See `Early.CylinderVisibility`. |
| `ChunkStreaming` | `false` | Applies the `Early` chunk streaming limits. |
| `TickWait` | `false` | Reduces spinning between ticks. |
| `PathfindingLimits` | `false` | Applies configured pathfinding limits. |

# Features
Section: `Mixins.Features`

Optional world behavior and the storage accessors used by existing shared worlds.

| Key | Default | Description |
|-----|---------|-------------|
| `OriginRetention` | `false` | Keeps the origin column loaded when `Early.VanillaKeepSpawnLoaded` is enabled. |
| `SharedInstances` | `false` | Shares instance worlds between players. Can change consumable dungeon behavior. |
| `SharedInstanceStorage` | `true` | Enables shared instance storage access. |
| `WorldPauseCommand` | `false` | Allows `/pause` in empty multiplayer worlds. |

# Recovery
Section: `Mixins.Recovery`

Optional recovery that can discard data or skip failed work.

| Key | Default | Description |
|-----|---------|-------------|
| `ShutdownSaveTimeout` | `false` | Limits save waits to `Early.ShutdownSaveTimeoutSeconds`. |
| `TickSurvival` | `false` | Skips failed tick work. Rethrows save exceptions after three failures in five minutes. |
| `CorruptSectionReset` | `false` | Resets corrupt sections after successful quarantine. |
| `MotionControllerBase` | `false` | Resets nonfinite NPC motion to zero. |
| `TurnOffTeleportersSystem` | `false` | Defers teleporter updates outside chunk loading callbacks. |
| `InvalidSavedHolders` | `false` | Skips broken saved entity records and saves the repaired chunk. |
| `InvalidCollisionData` | `false` | Rejects invalid positions and nonfinite, empty or inverted hitboxes. |
| `InteractionChain` | `false` | Tolerates interaction sync offset gaps. |
| `MarkerAddRemoveSystem` | `false` | Skips failed spawn marker load and removal callbacks. |
| `TickingSpawnMarkerSystem` | `false` | Despawns NPCs whose spawn marker tick uses an incorrect store reference. |
| `WorldMapTracker` | `false` | Guards null references during map image unloading. |
| `WorldSpawningSystem` | `false` | Returns no chunk when random spawn selection fails. |
| `DuplicateComponentReferences` | `false` | Logs and ignores duplicate block component references and holders. |

# Compatibility
Section: `Mixins.Compatibility`

Compatibility with authentication, updates, saved configuration and client behavior.

| Key | Default | Description |
|-----|---------|-------------|
| `DisableLiveConfig` | `false` | Skips remote startup refresh and uses local feature flag defaults when enabled. |
| `PageManager` | `true` | Ignores unexpected legacy custom-page acknowledgements. |
| `ServerAuthManager` | `true` | Supports external session authentication. |
| `UpdateCheckCommand` | `true` | Accepts identity tokens for `/update check`. |
| `UpdateDownloadCommand` | `true` | Accepts identity tokens for `/update download`. |
| `UpdateModule` | `true` | Accepts identity tokens for module update checks. |
| `WorldConfig` | `true` | Persists spawn provider changes. |

# Diagnostics
Section: `Mixins.Diagnostics`

Reporting controls. Disabling system metrics is optional.

| Key | Default | Description |
|-----|---------|-------------|
| `SkipSystemMetrics` | `false` | Disables system timing metrics. |
| `HytaleServer` | `true` | Warns when the runtime Refixes plugin is not enabled. |
| `SpawnMarkerBlockStateHeartbeat` | `true` | Reports spawn marker heartbeat messages at warning level. |

# Experimental
Section: `Mixins.Experimental`

Experimental limits on pathfinding work.

| Key | Default | Description |
|-----|---------|-------------|
| `PathfindingBudget` | `false` | Limits pathfinding work per world tick. Exempts attacking NPCs and marked targets. See `Early`. |

## Parallel
Section: `Mixins.Experimental.Parallel`

Independent parallel experiments. Both are disabled by default.

| Key | Default | Description |
|-----|---------|-------------|
| `CollectVisible` | `false` | Collects visible entities in parallel. |
| `SpatialCollection` | `false` | Collects entity positions in parallel. |

# Dependencies

`Mixins.Experimental.Parallel.CollectVisible` requires `Mixins.Correctness.ParallelTaskLifecycle`. `Mixins.Features.SharedInstances` requires `Mixins.Features.SharedInstanceStorage`. Disabling a required patch stops startup.

Container cleaning, fluid cleaning and world restart enable their internal accessors automatically.

`Runtime.Compatibility.ExistingSharedInstances` requires `Mixins.Features.SharedInstanceStorage`. Update Hyinit and Refixes together for combined JARs.
