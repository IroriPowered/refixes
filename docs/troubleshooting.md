# Troubleshooting

Refixes log lines start with `[Refixes]`. The early patches log as `[Refixes-Early]`. Two startup errors from the runtime plugin are exception messages without either prefix; they are marked below. In the messages below, `<...>` marks a part that changes, such as a path, world name or number.

## Startup errors

These errors stop startup. The process exit code depends on Hyinit: Hyinit 0.2.4 exits with code 0, Hyinit 0.3.0 with code 1. Check the log rather than the exit code if a supervisor restarts the server.

| Message | Cause | Fix |
|---------|-------|-----|
| `[Refixes] Malformed Refixes config at <path>: <details>` | `Refixes.json` or `config.json` is not valid JSON or its root is not an object. | Correct the JSON in the named file, or restore it from a backup. |
| `[Refixes] <path>: Invalid Refixes config: Early must be a JSON object` | `Early` in `config.json` is not an object. | Make `Early` a JSON object. See [Configuration](configuration.md#early-patches). |
| `[Refixes] <path>: Invalid Refixes config: Early.<key> must be 0 (no limit) or a positive integer not greater than <maximum>` | `Early.OutgoingQueueMaxBytes` or `Early.OutgoingQueueMaxPackets` is invalid. | Set a whole number in the allowed range. See [Outgoing packet queues](configuration.md#outgoing-packet-queues). |
| `[Refixes] <switch> requires <dependency> to be enabled in mods/IroriPowered_Refixes/Refixes.json` | An enabled patch needs another patch that is disabled. | Enable `<dependency>`, or disable `<switch>`. |
| `[Refixes] Refixes requires Fabric Mixin 0.17.5 or newer. Update Hyinit and remove conflicting Mixin JARs.` | Hyinit provides Fabric Mixin older than 0.17.5, or its version cannot be read. | Update Hyinit. Remove other Mixin JARs. See [Installation](installing.md#installation). |
| `[Refixes] Refixes requires Fabric Mixin 0.17.5 or newer (detected compatibility <number>). Update Hyinit and remove older conflicting Mixin runtime JARs.` | Hyinit provides Fabric Mixin older than 0.17.5. | Same as above. |
| `Shared-instance creation requires Runtime.Compatibility.ExistingSharedInstances` (no prefix) | `Mixins.Features.SharedInstances` is enabled while `Runtime.Compatibility.ExistingSharedInstances` is `false`. The runtime plugin stops startup. | Set `Runtime.Compatibility.ExistingSharedInstances` to `true`, or disable `Mixins.Features.SharedInstances`. |
| `Shared early classes do not match. Update Hyinit and Refixes together.` (no prefix) | The shared instance storage patches are selected, but the server classes the runtime plugin sees do not carry them. The runtime plugin stops startup. | Update Hyinit and Refixes together. Remove older Refixes JARs from `earlyplugins` and `mods`. |

On an unsupported server version without the override, the early checks stop only at an invalid `Refixes.json`. The other early checks are skipped because no patch is applied. An invalid `config.json` still stops startup when the runtime plugin loads it.

## Startup warnings

The server keeps starting after these messages.

| Message | Cause | Fix |
|---------|-------|-----|
| `[Refixes] Early mixins disabled for Hytale <version>. Refixes supports Hytale <supported version>. Update Refixes for this server version.` | Refixes does not accept the server version, or cannot identify it. All early patches are disabled. | See [Compatibility](installing.md#compatibility). |
| `[Refixes] Version gate overridden for Hytale <version>. Applying configured mixins.` | `AllowUnsupportedServerVersion` is `true` on a version Refixes was not built for. | Unsupported. See [Server version override](installing.md#server-version-override). |
| `[Refixes] Refixes.json: Replaced invalid value <path>=<value> with the default` | A switch in `Refixes.json` is not `true` or `false`. The switch uses its default. | Set the switch to `true` or `false`. |
| `[Refixes] Refixes.json: Removed unknown setting <path>=<value>` | A key under `Mixins` is not a switch of this Refixes version, for example a misspelled name. It is removed from the file. | Check the name against [Patches](patches.md) and set the correct switch. |
| `[Refixes] WARNING: could not update <path> (<error>). The selected patches are applied; the file keeps its previous content.` | Refixes could not write `Refixes.json` or `config.json`. | Make `mods/IroriPowered_Refixes/` writable. |
| `[Refixes-Early] Refixes Main Plugin is not installed or enabled! Some runtime fixes will not be applied.` | The runtime Refixes plugin is not enabled. Logged by `Mixins.Diagnostics.HytaleServer`. | Install Refixes as described in [Installation](installing.md#installation) and make sure the plugin is enabled. |
| `[Refixes] <feature> is inactive without its early mixins` | A runtime feature is enabled in `config.json`, but a patch it needs is off. | Enable the switch for `<feature>`: `Per-player hot radius` and `Idle player handler` need `Mixins.Compatibility.PlayerRadius`, `AI tick throttler` needs `Mixins.Compatibility.AiTickThrottler`, `Idle world pause` needs `Mixins.Compatibility.IdleWorldPause` (see [Runtime support hooks](patches.md#runtime-support-hooks)), and `Shared instance storage` needs `Mixins.Features.SharedInstanceStorage` (see [Features](patches.md#features)). If the `Early mixins disabled` warning above is also logged, follow that row instead. |
| `[Refixes] Failed to apply fix: <feature>` | A runtime feature threw an error while starting. The stack trace follows this line. The feature stays off until the next start. | Fix the error named in the stack trace and restart, or turn the feature off in `config.json` to start without it. |
| `[Refixes] Shared instance reset is inactive without Mixins.Features.SharedInstances` | `Runtime.Features.SharedInstances.ResetOnEmpty` is `true`, but `Mixins.Features.SharedInstances` is off. | Enable `Mixins.Features.SharedInstances`, or set `ResetOnEmpty` to `false`. |
| `[Refixes] World auto-restart is inactive without its early mixin. Watchdog monitoring remains active.` | `Runtime.Recovery.Watchdog.AutoRestartWorlds` is `true`, but its support patch is off. | Enable the support patch named in [Watchdog Recovery](configuration.md#watchdog-recovery). |
| `[Refixes] Memory view-radius guard is inactive without Mixins.Compatibility.ServerViewRadius` | `Runtime.Tuning.PerPlayerHotRadius.MemoryGuardEnabled` is `true`, but `Mixins.Compatibility.ServerViewRadius` is off. | Enable `Mixins.Compatibility.ServerViewRadius`. |
| `[Refixes] Failed to pass config values to the early mixins, they will use their default values.` | The values under `Early` and `Runtime.Features.SharedInstances` could not be handed to the early patches. The patches use their defaults until the next start. | Check those values in `config.json` against [Configuration](configuration.md), then restart. |
| `[Refixes] Failed to load chunk loaders from <path>` | A saved chunk loader file could not be read. The loaders in it are not loaded. | Fix or remove the named file, then restart. Add removed loaders again with `/chunkloader add`. |

## Runtime messages

| Message | Cause | Fix |
|---------|-------|-----|
| `[Refixes] ========== WATCHDOG ALERT ==========` followed by `[Refixes] Reason: <reason>` | The watchdog found a crashed or unresponsive world. | Read the reason and the thread dump that follows. See [Watchdog Diagnostics](configuration.md#watchdog-diagnostics). |
| `[Refixes] Shutting down the server after watchdog alert` | The default world crashed or stopped responding while `ShutdownOnDefaultWorldCrash` is `true`. | Find the cause in the alert above it. See [Watchdog Recovery](configuration.md#watchdog-recovery). |
| `[Refixes] World <world> did not respond for <seconds> seconds. Auto-restart only restarts crashed worlds, not hung ones.` | A world in the auto-restart list stopped responding. It is not restarted. | Restart the server. The thread dump that follows shows where the world is stuck. |
| `[Refixes] Aborting auto-restart of '<world>': save or shutdown did not safely complete within <milliseconds>ms` | Before a restart, the world did not save or shut down within `RestartSaveTimeoutMs`. The watchdog stops restarting that world until the server restarts. | Raise `RestartSaveTimeoutMs` if saves are slow, then restart the server. |
| `[Refixes] Giving up on auto-restarting world '<world>' after <count> failures. Resolve the underlying issue and restart the server.` | The world failed to load 5 times in a row after crashing. | Check the earlier `Failed to load world` errors, fix the cause and restart the server. |
| `[Refixes] Shutdown cannot proceed. Forcing exit.` | Shutdown took longer than `ShutdownTimeoutMs` while `HaltOnShutdownTimeout` is `true`. The process exits with code 1 without waiting for remaining saves. | Find what blocked shutdown in the log before this line. Set `HaltOnShutdownTimeout` to `false` or raise `ShutdownTimeoutMs` to let saves finish. |
| `[Refixes-Early] Outgoing packet queue <limit> limit exceeded: pendingBytes=<n>, pendingPackets=<n>, requestedBytes=<n>, requestedPackets=<n>, maxBytes=<n>, maxPackets=<n> for <address>` | On Netty QUIC, a write would have exceeded the byte limit, the packet limit or both. The player's connection is closed. A packet whose size cannot be computed is logged as `Invalid outgoing packet size for <packet class>` instead. | Raise `Early.OutgoingQueueMaxBytes` or `Early.OutgoingQueueMaxPackets`, or set one to `0`. See [Outgoing packet queues](configuration.md#outgoing-packet-queues). |
| `Outgoing packet queue limit exceeded for <connection id>` | Same on Quiche, without counts. Logged through the server's Quiche connection logger. | Same as above. |
| `[Refixes-Early] BlockSection#deserialize(): Quarantine failed. Corrupt section was not reset` | `Mixins.Recovery.CorruptSectionReset` could not save the unreadable section to the quarantine folder, so the section was not reset and still fails to load. | Make `mods/IroriPowered_Refixes/quarantine/block-sections/` writable. |
| `[Refixes] Failed to save chunk loaders for world <world>` | The chunk loader file for that world could not be written, so its latest loader changes are not on disk. | Make the Refixes data folder writable. |
| `[Refixes] Failed to save max view radius <radius>; it cannot be restored after an unclean shutdown` | Refixes could not write `mods/IroriPowered_Refixes/viewradius-restore`. | Make `mods/IroriPowered_Refixes/` writable. |
| `[Refixes] Failed to delete the saved max view radius; it is applied again at the next start` | Refixes could not delete `mods/IroriPowered_Refixes/viewradius-restore`. Its value replaces the server `MaxViewRadius` at the next start. | Delete the file by hand before the next start, or set `MaxViewRadius` again afterwards. |
