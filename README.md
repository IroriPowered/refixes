# Refixes
A mod for Hytale that offers updated server bug fixes and optimizations in two ways;
**the runtime plugin** and **Mixin-based patches**.

This project is derived from patches in Hyfixes / Hyzen Kernel, both of which are unfortunately no longer maintained
at this time. We aim to keep important patches updated to newer Hytale releases.

> #### ⚠️ **WARNING**
> Requires the server to be booted using [Hyinit](https://github.com/IroriPowered/Hyinit).  
> This project is still in early development. Expect issues and missing fixes!

## Download
Refixes builds are available on [CurseForge](https://www.curseforge.com/hytale/mods/refixes).
Please read the installation guide to apply the mod correctly.

## Installation
Refixes ships as a single jar containing both the runtime plugin and the Mixin-based early patches.
You need to boot the server via [Hyinit](https://github.com/IroriPowered/hyinit) on Hytale 0.6.4.

To install:
1. Set up Hyinit to launch your server
2. Place `refixes-X.X.X.jar` inside the `earlyplugins` folder
3. Remove any older `refixes-early-*` / `refixes-plugin-*` jars from `earlyplugins` and `mods`

Runtime settings (`config.json`) and patch toggles (`Refixes.json`) live in `mods/IroriPowered_Refixes/`.
See the [wiki](wiki/Home.md) for the full configuration and mixin reference.
