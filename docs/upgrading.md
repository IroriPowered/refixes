# Upgrading

## From 0.6.0 to 0.6.1

The Hyinit requirement is unchanged.

### Before the first start

1. Back up `mods/IroriPowered_Refixes/`.
2. Replace the Refixes 0.6.0 JAR in `earlyplugins` with `refixes-0.6.1.jar`.
3. After the first start, check the lines that start with `[Refixes] Refixes.json:` and `[Refixes] config.json:`. They name each removed switch and setting.

`config.json` keeps its keys. Only `Early.KDTreeOptimization` and `Runtime.Diagnostics.HyboxIntegration` are removed, see [Removed switches](#removed-switches) and [JFR events](#jfr-events).

### Changed behavior

- Early patches apply fully only on Hytale 0.7.7 and its hotfix builds. 0.6.0 applied every patch on any 0.7.x version. See [Compatibility](/mod/refixes/installing-refixes#compatibility).
- The new top-level `Refixes.json` key `OtherServerVersions` replaces `AllowUnsupportedServerVersion`. A `true` there becomes `"AllPatches"` and a `false` is removed. See [Other server versions](/mod/refixes/installing-refixes#other-server-versions).
- With `Mixins.Tuning.ChunkStreaming` on, `Early.MaxSectionsPerSecond` applies only to players that are neither local nor on the LAN. Local players get `Early.MaxSectionsPerSecondLocal`, default `2560`, and LAN players `Early.MaxSectionsPerSecondLan`, default `1280`. Set both to keep your old limit for them.
- `Mixins.Compatibility.ServerAuthManager` ends the server session on shutdown only in external-session mode. 0.6.0 ended it in every mode except singleplayer.
- With `Mixins.Backports.ShutdownRemoval` on, a server stop removes each world with reason `GENERAL` instead of `EXCEPTIONAL`. `RemoveWorldEvent` is sent earlier, before the world stops and its stores are saved. Cancelling it does not keep the world.
- `Mixins.Features.SharedInstanceStorage` applies only on Hytale 0.7.7 and its hotfix builds, see [Compatibility](/mod/refixes/configuration#compatibility).
- Failures that 0.6.0 dropped or logged without a cause now log a warning with the stack trace.

### Changed defaults

An existing `Refixes.json` keeps its values. Set a switch yourself to use the new default.

| Setting | 0.6.0 | 0.6.1 |
|---|---|---|
| `Mixins.Optimizations.SpawnManagerRecalc` | on | off |
| `Mixins.Optimizations.RepulsionTicker` | on | off |
| `Mixins.Optimizations.AssetPartSnapshotSharing` | on | off |
| `Mixins.Tuning.TickWait` | off | on |

See [Measured effect](/mod/refixes/patches#measured-effect).

### New switches

- `Mixins.Correctness.BlockChangeSaving`, on.
- `Mixins.Correctness.ChunkSaveQueueOrder`, on. Costs world-thread CPU when unsaved columns hold many NPCs.
- `Mixins.Optimizations.CodecMetadata`, on.
- `Mixins.Optimizations.LightNeutralBlocks`, `NPCComponentType`, `DefaultMovementResistance`, `RepulsionNeighbors` and `SkipUnchangedLightSaves`, off.
- `Mixins.Tuning.WorldHeightBounds` and `Mixins.Tuning.WorldMapExistingTerrain`, off.
- `Mixins.Diagnostics.PatchCounters`, off. Enables `/patchcounters`.
- `Mixins.Backports.ShutdownRemoval`, `ShutdownSaveFailure`, `StaticLayers` and `ModelParticleScale`, on, in the new `Mixins.Backports` section. They port fixes from Hytale 0.8.0-pre.6, see [Backports](/mod/refixes/patches#backports).

New `Early` settings `OutgoingQueueBroadcastWaitMs` and `OutgoingQueueBroadcastStallMs`. With `Mixins.Correctness.OutgoingQueueLimits` on, asset reload broadcasts now wait for slow players instead of disconnecting them. See [Outgoing packet queues](/mod/refixes/configuration#outgoing-packet-queues).

### Removed switches

These switches are removed for Hytale 0.7.7 and 0.8. At the first start each one left in `Refixes.json` is removed, whatever its value, and logged as `Removed retired setting` with the reason. Startup continues. Their 0.5.x paths are removed the same way.

Removed: `Mixins.Optimizations.KDTree`, `CollisionConfig`, `FluidDiscovery` and [`SkipEmptyLightSections`](#from-06x-or-070-pre5-to-080-beta1) on Hytale 0.7.7 only, `Mixins.Diagnostics.SkipSystemMetrics` and `SpawnMarkerBlockStateHeartbeat`, `Mixins.Correctness.SetMemoriesCapacityInteraction`, `UUIDSystem` and `WorldNullGuard`, `Mixins.Recovery.InvalidSavedHolders`, `Mixins.Compatibility.PageManager`, and the `Early.KDTreeOptimization` section in `config.json`, logged with `[Refixes] config.json:`.

### JFR events

`Runtime.Diagnostics.HyboxIntegration` is removed and its value does not carry over. Refixes records [JDK Flight Recorder events](/mod/refixes/configuration#flight-recorder-events) instead, on by default. To keep them out, set `Runtime.Diagnostics.JfrEvents` to `false`.

## From 0.6.x or 0.7.0-pre.5 to 0.8.0-beta.1

Refixes 0.8.0-beta.1 is the release for Hytale 0.8.0-pre.6 and replaces 0.7.0-pre.5. From 0.8 on, the Refixes minor version matches Hytale's. Switches for defects that Hytale 0.8.0-pre.5 and 0.8.0-pre.6 fix are removed.

### Before the first start

1. Update the server to Hytale 0.8.0-pre.6. On other versions see [Compatibility](/mod/refixes/installing-refixes#compatibility).
2. Back up `mods/IroriPowered_Refixes/`.
3. Replace the Refixes 0.6.x or 0.7.0-pre.5 JAR in `earlyplugins` with `refixes-0.8.0-beta.1.jar`. The Hyinit requirement is unchanged.
4. After the first start, check the lines that start with `[Refixes] Refixes.json:` and `[Refixes] config.json:`. Coming from 0.6.x, every switch for a defect that Hytale 0.8.0-pre.5 fixes is removed, whatever its value, and logged as `Removed retired setting` with the reason `fixed in Hytale 0.8.0-pre.5`.

### Changes shared with 0.6.1

Compared with 0.6.0 and 0.7.0-pre.5, Refixes 0.8.0-beta.1 makes every change in [From 0.6.0 to 0.6.1](#from-060-to-061), except:

- `Mixins.Tuning.WorldHeightBounds`, `Mixins.Optimizations.NPCComponentType` and the `Mixins.Backports` switches `ShutdownRemoval`, `ShutdownSaveFailure`, `StaticLayers` and `ModelParticleScale` do not exist. Coming from 0.6.1, each is removed and logged as `Removed retired setting` with its reason.
- The watchdog always ignores world removals while the server stops. In 0.6.1 this needs `Mixins.Backports.ShutdownRemoval`.
- `Mixins.Optimizations.SkipEmptyLightSections` stays and is now off by default. A `Refixes.json` from 0.6.0 or 0.7.0-pre.5 keeps `true`.

### Changed behavior on Hytale 0.8

- `Mixins.Features.OriginRetention` keeps the origin column and its sections 0-9 loaded, because Hytale 0.8.0-pre.5 unloads sections separately from their column.
- Chunk loaders keep the column, sections 0-9 and the section Y values registered with `/chunkloader add`. Sections that load later are no longer added. Y values outside 0-9 that 0.6.x added stay in the saved files and stay loaded. Remove and add a loader again to drop them.
- `Early.UnloadDistanceOffset` and `Runtime.Tuning.IdlePlayerHandler.ReduceMinLoadedRadius` no longer change when chunks unload, only the radius in which pending chunk generation is kept.
- `Mixins.Correctness.WorldTransfer` no longer requires `Mixins.Correctness.StorageOrdering` and no longer keeps a moved entity in the source world's save, so a crash before the target world saves loses the entity.
- `Early.ShutdownSaveTimeoutSeconds` no longer affects the save pass when a world stops.
- `Mixins.Recovery.CorruptSectionReset` does nothing while the server runs with `--verify-worlds`, so Hytale's verify and recovery restore or regenerate the broken sections.

## From 0.5.x or older

Back up `mods/IroriPowered_Refixes/` first. Settings from 0.4.4 and 0.5.x are moved once at the first start, and every moved or removed setting is logged with `[Refixes] Refixes.json:` or `[Refixes] config.json:`. The [0.6.0 release notes](https://github.com/IroriPowered/refixes/releases/tag/0.6.0) list what changed in 0.6.0.

### AI-throttled NPCs

Refixes before 0.6.0 saved throttle freezes, so older saves can contain frozen NPCs. Keep Refixes installed with `Runtime.Compatibility.TickThrottledCleanup.CleanupFrozenEntities` at its default `true` until the affected chunks have loaded and saved once.
