# Installing Refixes

## Installation

1. Launch the server through [Hyinit](https://github.com/IroriPowered/hyinit/releases/tag/0.5.0) 0.5.0 or newer with Fabric Mixin 0.17.5 or newer.
2. Put `refixes-X.X.X.jar` in `earlyplugins`.

Read the release notes on the [releases](https://github.com/IroriPowered/refixes/releases) page before upgrading.

On a server started without Hyinit a Refixes JAR in `mods` runs its runtime features from `config.json` and applies no early patch.

## Compatibility

One Refixes JAR in `earlyplugins` is built for both Hytale 0.7.7 and Hytale 0.8.0-pre.6. At startup it picks the built-for version nearest to the server and applies the patches made for it. On other versions Refixes decides at startup which early patches to apply. [Other server versions](#other-server-versions) changes this:

| Server version | Nearest 0.7.7 | Nearest 0.8.0-pre.6 | Early patches by default |
| --- | --- | --- | --- |
| Built-for version and its hotfix builds | 0.7.7 | 0.8.0-pre.6, 0.8.0-pre.6.1 | All enabled switches |
| Older | 0.7.6 | 0.8.0-pre.5 | None |
| Newer, same minor version | 0.7.8 | 0.8.0-pre.7, 0.8.0, 0.8.1 | Switches whose Hytale code is unchanged, except [exact-version switches](#exact-version-switches) |
| Newer minor version | | 0.9.0-pre.1, 0.9.0 | None |
| Pre-release of a patch version or of a later minor version | 0.7.8-pre.1 | 0.8.1-pre.1, 0.10.0-pre.1 | None |
| Four-number version | 0.7.7.1 | 0.8.0.1 | None |
| Unreadable | | | None |

Hytale matches a pre-release only against a version range that names a pre-release of the same version, and Refixes follows that rule. Its patches name the pre-releases of 0.8.0 and 0.9.0, the way Hytale has numbered every pre-release so far. So 0.8.0-pre.7 counts as a newer version with the same minor version, and 0.9.0-pre.1 follows `OtherServerVersions` like 0.9.0 does. On a pre-release of any other version and on a version with four numbers Hyinit skips every version-specific Mixin, so Refixes applies no early patches there, whatever `OtherServerVersions` says.

On a newer version the log names every skipped switch and why. When Refixes applies no early patches it logs the reason and `Early mixins disabled`. Update Refixes when a build for your Hytale version exists.

On other versions `Mixins.Features.SharedInstanceStorage` is off, see [Compatibility](/mod/refixes/configuration#compatibility).

### Differences on Hytale 0.8

Moving a server from Hytale 0.7.7 to 0.8.0-pre.6 keeps the same JAR. Switches for defects that Hytale 0.8.0-pre.5 and 0.8.0-pre.6 fix exist only on Hytale 0.7, see [Patches](/mod/refixes/patches). They keep their values in `Refixes.json` and do nothing on Hytale 0.8. On Hytale 0.8 compared with Hytale 0.7:

- The watchdog always ignores world removals while the server stops. On Hytale 0.7 this needs `Mixins.Backports.ShutdownRemoval`.
- `Mixins.Features.OriginRetention` keeps the origin column and its sections 0-9 loaded, because Hytale 0.8.0-pre.5 unloads sections separately from their column.
- Chunk loaders keep the column, sections 0-9 and the section Y values registered with `/chunkloader add`. Sections that load later are no longer added. Y values outside 0-9 that Hytale 0.7 added stay in the saved files and stay loaded. Remove and add a loader again to drop them.
- `Early.UnloadDistanceOffset` and `Runtime.Tuning.IdlePlayerHandler.ReduceMinLoadedRadius` no longer change when chunks unload, only the radius in which pending chunk generation is kept.
- `Mixins.Correctness.WorldTransfer` no longer requires `Mixins.Correctness.StorageOrdering` and no longer keeps a moved entity in the source world's save, so a crash before the target world saves loses the entity.
- `Early.ShutdownSaveTimeoutSeconds` no longer affects the save pass when a world stops.
- `Mixins.Recovery.CorruptSectionReset` does nothing while the server runs with `--verify-worlds`, so Hytale's verify and recovery restore or regenerate the broken sections.

## Exact-version switches

These switches apply only on the built-for version and its hotfix builds:

- Every `Mixins.Recovery` switch, and `Compatibility.Watchdog` and `Compatibility.UnknownBlockCleaner`.
- Switches that delete, move or rewrite saved data: `Correctness.StorageOrdering`, `RocksDbColumnRemoval`, `RocksDbScanBounds`, `RemovalSystem`, `PrefabPasteLoaded`, `PortalDeviceWorldClose`, `WorldGenReload`, `WorldTransfer`, `WorldRetirement`, `Optimizations.SkipUnchangedLightSaves`, `RocksDbCompression`, `CodecMetadata`, `Features.SharedInstanceStorage`, `SharedInstances`, `Backports.ShutdownRemoval` and `Compatibility.AiTickThrottler`.

Switches that only add or order saves follow the code comparison on a newer version with the same minor version and stay off on a newer minor version: `Correctness.ChunkSaveQueueOrder`, `BlockChangeSaving`, `FluidChangeSaving`, `ChunkStoreSectionBackoffDeadlock`, `EntitySectionLoad`, `SerializableArchetype`, `StoredFlockSaving`, `WorldEventSaving`, `RocksDbFlush`, `WorldOwnership`, `WorldReopenGuard`, `WorldTaskQueueClose`, `UniverseDeletedCleanup`, `ServerConfigSaveChain`, `TickingThreadAbandon`, `Features.OriginRetention`, `Tuning.WorldMapExistingTerrain` and `Backports.ShutdownSaveFailure`.

On Hytale 0.8 only some of these switches exist.

## Other server versions

The top-level key `OtherServerVersions` in `Refixes.json` decides what applies on a version other than the built-for one. The default is `"SameMinorVersion"`. Values are not case-sensitive.

| Value | Early patches on other versions |
| --- | --- |
| `"Never"` | None. |
| `"SameMinorVersion"` | Switches whose Hytale code is unchanged on newer versions with the same minor version. |
| `"AllNewerVersions"` | Switches whose Hytale code is unchanged on every newer version. World-data switches stay off on a newer minor version. |
| `"AllPatches"` | Every enabled switch on every version, older versions and changed code included. Unsupported, startup can fail. |

Hytale code counts as unchanged when Hyinit finds the methods each patch injects into, overwrites or names as an injection point unchanged, and every Hytale method, field and class the patch uses still exists. Hyinit compares against fingerprints recorded in the JAR for the built-for version, so `OtherServerVersions` needs Hyinit 0.5.0 or newer. When the comparison cannot run, no early patch applies and the log says why.

## Settings files

Both files are in `mods/IroriPowered_Refixes/`. Restart the server after editing them. Missing values use their defaults.

| File | Contents | Reference |
| --- | --- | --- |
| `config.json` | Runtime services and values used by patches | [Configuration](/mod/refixes/configuration) |
| `Refixes.json` | Which early patches are enabled | [Patches](/mod/refixes/patches) |

At startup `Refixes.json` is rewritten to list every switch for the running Hytale version. Values of switches for the other Hytale version and settings Refixes does not know stay in the file. Unknown settings are named in one log line on every start, also when no early patch applies. Before a rewrite changes the file, the old content is copied to `Refixes.json.bak`. The rewrite and the copy happen only when early patches apply. `config.json` is saved on every start and unknown keys are removed.
