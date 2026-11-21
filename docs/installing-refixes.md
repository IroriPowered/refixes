# Installing Refixes

## Installation

1. Launch the server through [Hyinit](https://github.com/IroriPowered/hyinit/releases/tag/0.3.0) 0.3.0 or newer with Fabric Mixin 0.17.5 or newer.
2. Put `refixes-X.X.X.jar` in `earlyplugins`.
3. Remove older Refixes JARs from `earlyplugins` and `mods`.

Upgrading from an older Refixes? Read [Upgrading](/mod/refixes/upgrading) first.

## Compatibility

| Refixes | Built for |
| --- | --- |
| 0.6.1 | Hytale 0.7.7 |
| 0.8.0-beta.1 | Hytale 0.8.0-pre.6 |

Install the Refixes version built for your server's Hytale version. On other versions Refixes decides at startup which early patches to apply. [Other server versions](#other-server-versions) changes this:

| Server version | Refixes 0.6.1 | Refixes 0.8.0-beta.1 | Early patches by default |
| --- | --- | --- | --- |
| Built-for version and its hotfix builds | 0.7.7, 0.7.7.1 | 0.8.0-pre.6, 0.8.0-pre.6.1 | All enabled switches |
| Older | 0.7.6 | 0.8.0-pre.5 | None |
| Newer, same minor version | 0.7.8 | 0.8.0-pre.7, 0.8.0, 0.8.1 | Switches whose Hytale code is unchanged, except [exact-version switches](#exact-version-switches) |
| Newer minor version | 0.8.0 | 0.9.0 | None |
| Unreadable | | | None |

On a newer version the log names every skipped switch and why. When Refixes applies no early patches it logs the reason and `Early mixins disabled`. Update Refixes when a build for your Hytale version exists.

On other versions `Mixins.Features.SharedInstanceStorage` is off, see [Compatibility](/mod/refixes/configuration#compatibility).

## Exact-version switches

These switches apply only on the built-for version and its hotfix builds:

- Every `Mixins.Recovery` switch, and `Compatibility.Watchdog` and `Compatibility.UnknownBlockCleaner`.
- Switches that delete, move or rewrite saved data: `Correctness.StorageOrdering`, `RocksDbColumnRemoval`, `RocksDbScanBounds`, `RemovalSystem`, `PrefabPasteLoaded`, `PortalDeviceWorldClose`, `WorldGenReload`, `WorldTransfer`, `WorldRetirement`, `Optimizations.SkipUnchangedLightSaves`, `RocksDbCompression`, `CodecMetadata`, `Features.SharedInstanceStorage`, `SharedInstances`, `Backports.ShutdownRemoval` and `Compatibility.AiTickThrottler`.

Switches that only add or order saves follow the code comparison on a newer version with the same minor version and stay off on a newer minor version: `Correctness.ChunkSaveQueueOrder`, `BlockChangeSaving`, `FluidChangeSaving`, `ChunkStoreSectionBackoffDeadlock`, `EntitySectionLoad`, `SerializableArchetype`, `StoredFlockSaving`, `WorldEventSaving`, `RocksDbFlush`, `WorldOwnership`, `WorldReopenGuard`, `WorldTaskQueueClose`, `UniverseDeletedCleanup`, `ServerConfigSaveChain`, `TickingThreadAbandon`, `Features.OriginRetention`, `Tuning.WorldMapExistingTerrain` and `Backports.ShutdownSaveFailure`.

Refixes 0.8.0-beta.1 has only some of these switches.

## Other server versions

The top-level key `OtherServerVersions` in `Refixes.json` decides what applies on a version other than the built-for one. The default is `"SameMinorVersion"`. Values are not case-sensitive.

| Value | Early patches on other versions |
| --- | --- |
| `"Never"` | None. |
| `"SameMinorVersion"` | Switches whose Hytale code is unchanged on newer versions with the same minor version. |
| `"AllNewerVersions"` | Switches whose Hytale code is unchanged on every newer version. World-data switches stay off on a newer minor version. |
| `"AllPatches"` | Every enabled switch on every version, older versions and changed code included. Unsupported, startup can fail. |

## Settings files

Both files are in `mods/IroriPowered_Refixes/`. Restart the server after editing them. Missing values use their defaults.

| File | Contents | Reference |
| --- | --- | --- |
| `config.json` | Runtime services and values used by patches | [Configuration](/mod/refixes/configuration) |
| `Refixes.json` | Which early patches are enabled | [Patches](/mod/refixes/patches) |

At startup `Refixes.json` is rewritten to list every switch and each removed or replaced value is logged. This happens only when early patches apply. `config.json` is saved on every start and unknown keys are removed.
