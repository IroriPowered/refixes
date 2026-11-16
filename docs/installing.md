# Installing Refixes

## Installation

1. Launch the server through [Hyinit](https://github.com/IroriPowered/hyinit/releases/tag/0.3.0) 0.3.0 or newer. It must bundle Fabric Mixin 0.17.5 or newer. Keep Hyinit and Refixes up to date together.
2. Put `refixes-X.X.X.jar` in `earlyplugins`.
3. Remove older Refixes JARs from `earlyplugins` and `mods`. One JAR contains the runtime plugin and early patches.

Upgrading from Refixes 0.6.0 or 0.5.x? Read [Upgrading](upgrading.md) first.

## Compatibility

| Refixes | Hytale |
| --- | --- |
| 0.6.0, 0.6.1 | Hytale 0.7.7 |
| 0.7.0-pre.5 | Hytale 0.8.0-pre.5 and later 0.8 versions |

Install the Refixes version that matches your server's Hytale version. Other Hytale versions are not supported.

Refixes reads the server version from the Hytale server JAR at startup. Refixes 0.6.x applies its early patches when that version is 0.7.x. On any other version, or when the JAR does not state a version, it disables all early patches and logs a message. The server still starts. Runtime features that need patches log that they are inactive. Hyinit's own loader patches are not affected.

A newer Hytale version can change code that a patch targets. Startup then stops with a patch error, or the patch no longer has its intended effect. Update Refixes when a build for that version is available.

## Server version override

`AllowUnsupportedServerVersion` is a top-level key in `Refixes.json`. Default: `false`.

Setting it to `true` applies the configured patches on a version Refixes was not built for. This is unsupported and startup can fail. Individual patch switches and dependency checks still apply.

**This is not a normal setup step.**

## Settings files

Both files are in `mods/IroriPowered_Refixes/`. Restart the server after editing either file. Missing values use their defaults.

| File | Contents | Reference |
| --- | --- | --- |
| `config.json` | Runtime services and values used by patches | [Configuration](configuration.md) |
| `Refixes.json` | Which early patches are enabled | [Patches](patches.md) |

At startup:
- `Refixes.json` is rewritten to list every switch, unless it is already up to date. Unknown keys under `Mixins` are removed and logged as `Removed unknown setting`. A switch value that is not `true` or `false` is replaced with the default and logged as `Replaced invalid value`. This happens only when early patches are enabled for the server version. If the file cannot be written, Refixes logs a warning, keeps the file and applies the selected patches.
- `config.json` is saved by the runtime plugin on every start. Unknown keys are removed. Startup stops if the file cannot be saved, so the folder must be writable.
