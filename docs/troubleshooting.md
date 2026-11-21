# Troubleshooting

Refixes log lines start with `[Refixes]`, early patch lines with `[Refixes-Early]`. Messages without either prefix say so in the Cause column. `<...>` marks a part that changes, such as a path, world name or number.

## Startup errors

These errors stop startup.

| Message | Cause | Fix |
|---------|-------|-----|
| `[Refixes] Malformed Refixes config at <path>: <details>` | `Refixes.json` or `config.json` is not valid JSON or its root is not an object. | Correct the file or restore a backup. |
| `[Refixes] <path>: Invalid Refixes config: Early must be a JSON object` | `Early` in `config.json` is not an object. | Make `Early` an object. See [Configuration](/mod/refixes/configuration#early-patches). |
| `[Refixes] <path>: Invalid Refixes config: Early.<key> must be 0 (<meaning>) or a positive integer not greater than <maximum>` | One of the four `Early.OutgoingQueue` values is invalid. | Set a whole number from `0`. See [Outgoing packet queues](/mod/refixes/configuration#outgoing-packet-queues). |
| `[Refixes] <switch> requires <dependency> to be enabled in mods/IroriPowered_Refixes/Refixes.json` | An enabled patch needs a disabled one. | Enable `<dependency>` or disable `<switch>`. |
| `[Refixes] Refixes requires Fabric Mixin 0.17.5 or newer` | Hyinit's Fabric Mixin is older than 0.17.5 or its version cannot be read. | Update Hyinit and remove other Mixin JARs. See [Installation](/mod/refixes/installing-refixes#installation). |
| `Shared-instance creation requires Runtime.Compatibility.ExistingSharedInstances` | Logged without a Refixes prefix. `Mixins.Features.SharedInstances` is on while `Runtime.Compatibility.ExistingSharedInstances` is `false`. | Set `ExistingSharedInstances` to `true` or turn off `SharedInstances`. |
| `Shared early classes do not match. Update Hyinit and Refixes together.` | Logged without a Refixes prefix. The shared instance storage patches are selected but missing from the server classes the plugin sees. | Update Hyinit and Refixes together. Remove older Refixes JARs from `earlyplugins` and `mods`. |

When no early patches apply, only an invalid `Refixes.json` stops the early checks. An invalid `config.json` still stops startup when the plugin loads.

## Startup warnings

The server keeps starting after these messages.

| Message | Cause | Fix |
|---------|-------|-----|
| `[Refixes] Early mixins disabled for Hytale <version>. Refixes supports Hytale <supported version>. Update Refixes for this server version.` | The server version is older, newer than `OtherServerVersions` covers, unreadable, or the code comparison failed. The line before names the reason. All early patches are off. | See [Compatibility](/mod/refixes/installing-refixes#compatibility). |
| `[Refixes] Hytale <version> is newer than <supported version>, the version this Refixes build was made for. Applied <n> patches whose Hytale code is unchanged, skipped <m>.` | Only switches whose Hytale code is unchanged are applied. Each skipped switch is listed with its reason. | Update Refixes for this Hytale version. See [Compatibility](/mod/refixes/installing-refixes#compatibility). |
| `[Refixes] Mixins.Features.SharedInstanceStorage is not applied on this start, so Hytale would delete removed instance-shared- worlds.` | `Runtime.Compatibility.ExistingSharedInstances` is on, but the switch is off or the server version differs from the build. Refixes only keeps the folders of removed shared worlds. | Run the Hytale version Refixes was built for, or update Refixes. |
| `[Refixes] Mixins.Features.SharedInstanceStorage is not applied on this start and Runtime.Features.SharedInstances.ResetOnEmpty is on` | As above, with `ResetOnEmpty` on, so Hytale's instance removal deletes shared worlds by its own rules. | As above, or turn `ResetOnEmpty` off to keep the folders. |
| `[Refixes] Version gate overridden for Hytale <version> by OtherServerVersions=AllPatches. Applying configured mixins.` | `OtherServerVersions` is `"AllPatches"` on a version other than the built-for one. | Unsupported. See [Other server versions](/mod/refixes/installing-refixes#other-server-versions). |
| `[Refixes] Refixes.json: Replaced invalid value <path>=<value> with the default` | A switch is not `true` or `false`. It uses its default. | Set `true` or `false`. |
| `[Refixes] Refixes.json: Removed unknown setting <path>=<value>` | A key under `Mixins` is not a switch of this version, for example a typo. It is removed. | Check the name in [Patches](/mod/refixes/patches). |
| `[Refixes] WARNING: could not update <path> (<error>). The selected patches are applied; the file keeps its previous content.` | `Refixes.json` or `config.json` could not be written. | Make `mods/IroriPowered_Refixes/` writable. |
| `[Refixes-Early] Refixes Main Plugin is not installed or enabled! Some runtime fixes will not be applied.` | The runtime plugin is not enabled. Logged by `Mixins.Diagnostics.HytaleServer`. | Install and enable it, see [Installation](/mod/refixes/installing-refixes#installation). |
| `[Refixes] <feature> is inactive without its early mixins` | A runtime feature is on, but a patch it needs is off. | Turn on the switch the feature needs, see [Runtime support hooks](/mod/refixes/patches#runtime-support-hooks) and [Features](/mod/refixes/patches#features). If `Early mixins disabled` is also logged, follow that row. |
| `[Refixes] Failed to apply fix: <feature>` | A runtime feature failed to start. The stack trace follows. It stays off until the next start. | Fix the error and restart, or turn the feature off. |
| `[Refixes] Shared instance reset is inactive without Mixins.Features.SharedInstances` | `Runtime.Features.SharedInstances.ResetOnEmpty` is `true` but the switch is off. | Turn on `Mixins.Features.SharedInstances` or set `ResetOnEmpty` to `false`. |
| `[Refixes] World auto-restart is inactive without its early mixin. Watchdog monitoring remains active.` | `Runtime.Recovery.Watchdog.AutoRestartWorlds` is `true` but its support patch is off. | Turn on a patch named in [Watchdog Recovery](/mod/refixes/configuration#watchdog-recovery). |
| `[Refixes] Memory view-radius guard is inactive without Mixins.Compatibility.ServerViewRadius` | `Runtime.Tuning.PerPlayerHotRadius.MemoryGuardEnabled` is `true` but the switch is off. | Turn on `Mixins.Compatibility.ServerViewRadius`. |
| `[Refixes] Failed to pass config values to the early mixins, they will use their default values.` | Values under `Early` and `Runtime.Features.SharedInstances` could not reach the early patches. They use defaults until the next start. | Check those values against [Configuration](/mod/refixes/configuration), then restart. |
| `[Refixes] Failed to load chunk loaders from <path>` | A saved chunk loader file could not be read. Its loaders are not loaded. | Fix or remove the file and restart. Re-add loaders with `/chunkloader add`. |

## Runtime messages

| Message | Cause | Fix |
|---------|-------|-----|
| `[Refixes] ========== WATCHDOG ALERT ==========` followed by `[Refixes] Reason: <reason>` | The default world crashed or stopped responding. | Read the reason and thread dump. See [Watchdog Diagnostics](/mod/refixes/configuration#watchdog-diagnostics). |
| `[Refixes] Shutting down the server after watchdog alert` | The default world crashed or hung while `ShutdownOnDefaultWorldCrash` is `true`. | Find the cause in the alert above. See [Watchdog Recovery](/mod/refixes/configuration#watchdog-recovery). |
| `[Refixes] World <world> did not respond for <seconds> seconds. Auto-restart only restarts crashed worlds, not hung ones.` | A world in the auto-restart list hung. It is not restarted. | Restart the server. The thread dump shows where it is stuck. |
| `[Refixes] Aborting auto-restart of '<world>': save or shutdown did not safely complete within <milliseconds>ms` | The world did not save or stop within `RestartSaveTimeoutMs`. It is not restarted until the server restarts. | Raise `RestartSaveTimeoutMs` if saves are slow, then restart the server. |
| `[Refixes] Giving up on auto-restarting world '<world>' after <count> failures. Resolve the underlying issue and restart the server.` | The world failed to load 5 times in a row. | Fix the cause in the earlier `Failed to load world` errors and restart. |
| `[Refixes] Shutdown cannot proceed. Forcing exit.` | Shutdown exceeded `ShutdownTimeoutMs` with `HaltOnShutdownTimeout` on. The process exits with code 1 without waiting for saves. | Find what blocked shutdown earlier in the log. Turn `HaltOnShutdownTimeout` off or raise `ShutdownTimeoutMs`. |
| `[Refixes-Early] Outgoing packet queue <limit> limit exceeded: pendingBytes=<n>, pendingPackets=<n>, requestedBytes=<n>, requestedPackets=<n>, maxBytes=<n>, maxPackets=<n> for <address>` | On Netty QUIC, a write would exceed a queue limit and the player is disconnected. The pending counts include exempt asset broadcast data. A packet of unknown size logs `Invalid outgoing packet size for <packet class>` instead. | Raise `Early.OutgoingQueueMaxBytes` or `Early.OutgoingQueueMaxPackets`, or set one to `0`. If it happens on asset reloads, keep `Early.OutgoingQueueBroadcastWaitMs` above `0`. See [Outgoing packet queues](/mod/refixes/configuration#outgoing-packet-queues). |
| `Outgoing packet queue limit exceeded for <connection id>` | Logged without a Refixes prefix. Same on Quiche, without counts. | Same as above. |
| `[Refixes-Early] BlockSection#deserialize(): Quarantine failed. Corrupt section was not reset` | `Mixins.Recovery.CorruptSectionReset` could not write the quarantine file, so the section still fails to load. | Make `mods/IroriPowered_Refixes/quarantine/block-sections/` writable. |
| `[Refixes] Failed to save chunk loaders for world <world>` | The world's chunk loader file could not be written. | Make the Refixes data folder writable. |
| `[Refixes] Failed to save max view radius <radius>, so it cannot be restored after an unclean shutdown` | `mods/IroriPowered_Refixes/viewradius-restore` could not be written. | Make `mods/IroriPowered_Refixes/` writable. |
| `[Refixes] Failed to delete the saved max view radius, so it is applied again at the next start` | `mods/IroriPowered_Refixes/viewradius-restore` could not be deleted and will replace `MaxViewRadius` at the next start. | Delete the file before the next start, or set `MaxViewRadius` again afterwards. |
| `[Refixes] Gauge <name> failed, its refixes.Metric events are skipped while it fails` | A gauge threw an exception during a JFR recording. Logged once per gauge. Other metrics are still written. | Report it with the stack trace. |
| `[Refixes] Early patch counters failed, their refixes.Metric events are skipped while they fail` | The early patch counters threw an exception during a JFR recording. Logged once. | Report it with the stack trace. |
| `[Refixes] This Java runtime has no JDK Flight Recorder module (jdk.jfr), so Refixes records no JFR events` | `Runtime.Diagnostics.JfrEvents` is `true` but the Java runtime lacks `jdk.jfr`. Refixes works but writes no [Flight Recorder Events](/mod/refixes/configuration#flight-recorder-events). | Use a JDK or JRE with `jdk.jfr`, or set `JfrEvents` to `false`. |
