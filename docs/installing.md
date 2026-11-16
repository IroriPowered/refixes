# Installing Refixes

## Installation

1. Launch the server through [Hyinit](https://github.com/IroriPowered/hyinit/releases/tag/0.3.0) 0.3.0 or newer. It must bundle Fabric Mixin 0.17.5. Keep Hyinit and Refixes up to date together.
2. Put `refixes-X.X.X.jar` in `earlyplugins`.
3. Remove older Refixes JARs from `earlyplugins` and `mods`. One JAR contains the runtime plugin and early patches.

Upgrading from Refixes 0.6.0 or 0.5.x? Read [Upgrading](upgrading.md) first.

## Compatibility

| Refixes | Hytale |
| --- | --- |
| 0.6.0 | Hytale 0.7.7 |
| 0.7.0-pre.5 | Hytale 0.8.0-pre.5 and later 0.8 versions |

Install the Refixes version that matches your server's Hytale version. Other Hytale versions are not supported.

When Refixes does not accept the server version, or cannot identify it, it disables its early patches and logs a message. This does not disable Hyinit's own loader patches. On a supported version, startup stops when Hyinit provides Fabric Mixin older than 0.17.5.

A newer Hytale version can change code that a patch targets. Startup then stops with a patch error, or the patch no longer has its intended effect. Update Refixes when a build for that version is available.

Features that require Refixes patches are unavailable when those patches are disabled. Update Refixes for the server version rather than bypassing the check. The [server version override](#server-version-override) below is for testing unsupported versions.

## Server version override

`AllowUnsupportedServerVersion` is a top-level key in `Refixes.json`. Default: `false`.

When Refixes does not accept the server version, or cannot identify it, all early patches are disabled and a message is logged. The server still starts. Runtime features that need patches report that they are inactive.

Setting `AllowUnsupportedServerVersion` to `true` applies the configured patches on a version Refixes was not built for. This is unsupported and startup can fail. Individual patch switches and dependency checks still apply.

**This is not a normal setup step.** Use a compatible Refixes release instead. See [Compatibility](#compatibility).

## Settings files

Both files are in `mods/IroriPowered_Refixes/`:

| File | Reference |
| --- | --- |
| `config.json` | [Configuration](configuration.md): runtime services and patch tuning values |
| `Refixes.json` | [Patches](patches.md): which early patches are enabled |

Restart after editing either file. The references list defaults, dependencies and risks. Settings from Refixes 0.5.x are moved automatically on a supported server version. Back up both files before upgrading. [Startup errors](troubleshooting.md#startup-errors) lists the errors that stop startup.
