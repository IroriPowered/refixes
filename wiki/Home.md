# Refixes

Server fixes and optional optimizations for Hytale.

## Installation

1. Launch the server through [Hyinit](https://github.com/IroriPowered/hyinit) 0.2.4 or newer. It must bundle Fabric Mixin 0.17.5. Keep Hyinit and Refixes up to date together.
2. Put `refixes-X.X.X.jar` in `earlyplugins`.
3. Remove older Refixes JARs from `earlyplugins` and `mods`. One JAR contains the runtime plugin and early patches.

Upgrading from Refixes 0.6.0 or 0.5.x? Read [Upgrading](Upgrading.md) first.

## Compatibility

| Refixes | Hytale |
| --- | --- |
| 0.6.0 | Hytale 0.7.7 |
| 0.7.0-pre.5 | Hytale 0.8.0-pre.5 and later 0.8 versions |

Install the Refixes version that matches your server's Hytale version. Other Hytale versions are not supported.

When Refixes does not accept the server version, or cannot identify it, it disables its early patches and logs a message. This does not disable Hyinit's own loader patches. On a supported version, startup stops when Hyinit provides Fabric Mixin older than 0.17.5.

A newer Hytale version can change code that a patch targets. Startup then stops with a patch error, or the patch no longer has its intended effect. Update Refixes when a build for that version is available.

Features that require Refixes patches are unavailable when those patches are disabled. Update Refixes for the server version rather than bypassing the check. The [patch reference](Patches.md#server-version-override) documents the override for testing unsupported versions.

## Settings

Both files are in `mods/IroriPowered_Refixes/`:

| File | Reference |
| --- | --- |
| `config.json` | [Configuration](Configuration.md): runtime services and patch tuning values |
| `Refixes.json` | [Patches](Patches.md): which early patches are enabled |

Restart after editing either file. The references list defaults, dependencies and risks. Settings from Refixes 0.5.x are moved automatically on a supported server version. Back up both files before upgrading. [Startup checks](Patches.md#startup-checks) lists the errors that stop startup.

[Downloads](https://www.curseforge.com/hytale/mods/refixes) · [Support Discord](https://discord.gg/y5kTgtQtgX)
