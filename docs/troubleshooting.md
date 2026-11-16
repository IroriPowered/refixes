# Troubleshooting

Refixes log lines start with `[Refixes]`. Search the server log for that prefix. In the messages below, `<...>` marks a part that changes, such as a path, world name or number.

## Startup errors

Startup stops with a `[Refixes]` error message when any of the following applies. The process exit code depends on Hyinit: Hyinit 0.2.4 exits with code 0, Hyinit 0.3.0 with code 1. Check the log rather than the exit code if a supervisor restarts the server.

| Message | Cause | Fix |
|---------|-------|-----|
| `[Refixes] Malformed Refixes config at <path>: <details>` | `Refixes.json` or `config.json` is not valid JSON or its root is not an object. | Correct the JSON in the named file, or restore it from a backup. |
| `[Refixes] <path>: Invalid Refixes config: Early must be a JSON object` | `Early` in `config.json` is not an object. | Make `Early` a JSON object. See [Configuration](configuration.md#early-patches). |
| `[Refixes] <path>: Invalid Refixes config: Early.<key> must be 0 (no limit) or a positive integer not greater than <maximum>` | `Early.OutgoingQueueMaxBytes` or `Early.OutgoingQueueMaxPackets` is invalid. | Set a whole number in the allowed range. See [Outgoing packet queues](configuration.md#outgoing-packet-queues). |
| `[Refixes] <switch> requires <dependency> to be enabled in mods/IroriPowered_Refixes/Refixes.json` | An enabled patch needs another patch that is disabled. See [Patch dependencies](#patch-dependencies). | Enable the required switch, or disable the switch that needs it. |
| `[Refixes] Refixes requires Fabric Mixin 0.17.5 or newer. Update Hyinit and remove conflicting Mixin JARs.` | Hyinit provides Fabric Mixin older than 0.17.5, or its version cannot be read. | Update Hyinit. Remove other Mixin JARs. See [Installation](installing.md#installation). |
| `[Refixes] Refixes requires Fabric Mixin 0.17.5 or newer (detected compatibility <number>). Update Hyinit and remove older conflicting Mixin runtime JARs.` | Hyinit provides Fabric Mixin older than 0.17.5. | Same as above. |
| `Shared-instance creation requires Runtime.Compatibility.ExistingSharedInstances` | The runtime plugin stops startup when `Mixins.Features.SharedInstances` is enabled while `Runtime.Compatibility.ExistingSharedInstances` is `false`. | Set `Runtime.Compatibility.ExistingSharedInstances` to `true`, or disable `Mixins.Features.SharedInstances`. |

On an unsupported server version without the override, the early checks stop at an invalid `Refixes.json`. The other early checks are skipped because no patch is applied. An invalid `config.json` still stops startup when the runtime plugin loads it.

### Patch dependencies

| Enabled | Requires |
|---------|----------|
| `Mixins.Experimental.Parallel.CollectVisible` | `Mixins.Correctness.ParallelTaskLifecycle` |
| `Mixins.Features.SharedInstances` | `Mixins.Features.SharedInstanceStorage` |
| `Mixins.Correctness.WorldTransfer` | `Mixins.Correctness.StorageOrdering` |
| `Mixins.Correctness.WorldRetirement` | `Mixins.Correctness.WorldReopenGuard` |
| `Mixins.Correctness.OutgoingQueueLimits` | `Mixins.Correctness.SetupPacing` |

## Startup warnings

The server keeps starting after these messages.

| Message | Cause | Fix |
|---------|-------|-----|
| `[Refixes] Early mixins disabled for Hytale <version>. Refixes supports Hytale <supported version>. Update Refixes for this server version.` | Refixes does not accept the server version, or cannot identify it. All early patches are disabled. | Install the Refixes release for your Hytale version. See [Compatibility](installing.md#compatibility). |
| `[Refixes] Version gate overridden for Hytale <version>. Applying configured mixins.` | `AllowUnsupportedServerVersion` is `true` on a version Refixes was not built for. | Unsupported. See [Server version override](installing.md#server-version-override). |
| `[Refixes] WARNING: could not update <path> (<error>). The selected patches are applied; the file keeps its previous content.` | Refixes could not write `Refixes.json` or `config.json`. | Make `mods/IroriPowered_Refixes/` writable. See [Settings file updates](#settings-file-updates). |
| `[Refixes-Early] Refixes Main Plugin is not installed or enabled! Some runtime fixes will not be applied.` | The runtime Refixes plugin is not enabled. Logged by `Mixins.Diagnostics.HytaleServer`. | Install Refixes as described in [Installation](installing.md#installation) and make sure the plugin is enabled. |
| `[Refixes] <feature> is inactive without its early mixins` | A runtime feature is enabled in `config.json`, but a patch it needs is off. Early patches are also off on an unsupported server version. | Enable the patch switch the feature needs. See [Runtime support hooks](patches.md#runtime-support-hooks). |
| `[Refixes] Failed to apply fix: <feature>` | A runtime feature failed to start. The log shows the cause. | Check the cause in the log. Report it on the Support Discord if it is not a settings problem. |
| `[Refixes] Shared instance reset is inactive without Mixins.Features.SharedInstances` | `Runtime.Features.SharedInstances.ResetOnEmpty` is `true`, but `Mixins.Features.SharedInstances` is off. | Enable `Mixins.Features.SharedInstances`, or set `ResetOnEmpty` to `false`. See [Shared Instance Worlds](configuration.md#shared-instance-worlds). |
| `[Refixes] World auto-restart is inactive without its early mixin. Watchdog monitoring remains active.` | `Runtime.Recovery.Watchdog.AutoRestartWorlds` is `true`, but its support patch is off. | Enable `Mixins.Compatibility.Watchdog` or `Mixins.Correctness.WorldOwnership`. See [Watchdog Recovery](configuration.md#watchdog-recovery). |
| `[Refixes] Memory view-radius guard is inactive without Mixins.Compatibility.ServerViewRadius` | `Runtime.Tuning.PerPlayerHotRadius.MemoryGuardEnabled` is `true`, but `Mixins.Compatibility.ServerViewRadius` is off. | Enable `Mixins.Compatibility.ServerViewRadius`. |
| `[Refixes] Failed to pass config values to the early mixins, they will use their default values.` | `config.json` values could not be handed to the early patches. The patches use their defaults. | Check the cause in the log. |

## Runtime messages

| Message | Cause | Fix |
|---------|-------|-----|
| `[Refixes] ========== WATCHDOG ALERT ==========` followed by `[Refixes] Reason: <reason>` | The watchdog found a crashed or unresponsive world. | Read the reason and the thread dump that follows. See [Watchdog Diagnostics](configuration.md#watchdog-diagnostics). |
| `[Refixes] Shutting down the server after watchdog alert` | The default world crashed or stopped responding while `ShutdownOnDefaultWorldCrash` is `true`. | Find the cause in the alert above it. See [Watchdog Recovery](configuration.md#watchdog-recovery). |
| `[Refixes] Aborting auto-restart of '<world>': save or shutdown did not safely complete within <milliseconds>ms` | Before a restart, the world did not save or shut down within `RestartSaveTimeoutMs`. The watchdog stops restarting that world until the server restarts. | Restart the server. Raise `RestartSaveTimeoutMs` if saves are slow. |
| `[Refixes] Giving up on auto-restarting world '<world>' after <count> failures. Resolve the underlying issue and restart the server.` | The world failed to load again after repeated restart attempts. | Check the earlier `Failed to load world` errors, fix the cause and restart the server. |
| `[Refixes] Failed to save max view radius <radius>; it cannot be restored after an unclean shutdown` | Refixes could not write `mods/IroriPowered_Refixes/viewradius-restore`. | Make `mods/IroriPowered_Refixes/` writable. |

## Settings file updates

Refixes rewrites `Refixes.json` at startup to list every switch. A file that is already up to date is not rewritten. If the file cannot be written, Refixes logs a warning, keeps the file and continues with the selected patches. Settings from 0.5.x that were moved are written to `config.json` the same way. The runtime plugin also saves `config.json` on every start and stops startup if it cannot, so the folder must be writable. On an unsupported server version without the override, nothing is migrated, `Refixes.json` is not rewritten and the runtime plugin's save removes 0.5.x settings from `config.json`.
