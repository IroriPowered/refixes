# Refixes

Server fixes and optional optimizations for Hytale 0.6.4, based on Hyfixes and Hyzen Kernel.

## Installation

1. Launch the server through [Hyinit](https://www.curseforge.com/hytale/bootstrap/hyinit).
2. Put `refixes-X.X.X.jar` in `earlyplugins`.
3. Remove older Refixes copies from `earlyplugins` and `mods`. One JAR provides both the runtime plugin and early patches.

## Configuration

Both files are in `mods/IroriPowered_Refixes/`.

| File | Purpose |
| --- | --- |
| `config.json` | [Runtime settings](Configuration) |
| `Refixes.json` | [Mixin patches](Mixins) |

You only need to set values you want to change — everything else uses defaults. Restart the server after editing the files.

Experimental parallel patches are off by default. Enable them only when testing.

[Support Discord](https://discord.gg/y5kTgtQtgX)
