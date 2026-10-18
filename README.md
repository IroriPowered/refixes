[**Support Discord Server**](https://discord.gg/y5kTgtQtgX)

# Refixes
Server fixes and optimizations for Hytale through a runtime plugin and Mixin patches.

Based on Hyfixes and Hyzen Kernel, with patches updated for newer Hytale releases.

> #### ⚠️ **WARNING**
> Requires the server to be booted using [Hyinit](https://github.com/IroriPowered/Hyinit).  
> This project is still in early development. Expect issues and missing fixes!

## Download
Refixes builds are available on [CurseForge](https://www.curseforge.com/hytale/mods/refixes).
Please read the installation guide to apply the mod correctly.

## Installation
Refixes ships as a single jar containing both the runtime plugin and the Mixin-based early patches.
Use Hytale 0.7.2 and update [Hyinit](https://github.com/IroriPowered/hyinit) and Refixes together.

To install:
1. Set up Hyinit to launch your server
2. Place `refixes-X.X.X.jar` inside the `earlyplugins` folder
3. Remove any older `refixes-early-*` / `refixes-plugin-*` jars from `earlyplugins` and `mods`

Runtime settings (`config.json`) and patch toggles (`Refixes.json`) live in `mods/IroriPowered_Refixes/`.
See the [wiki](wiki/Home.md) for the full configuration and mixin reference.
