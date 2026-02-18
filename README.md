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
### Refixes Early Plugin
Refixes Early Plugin mainly contains high-severity fixes that use Mixin to patch the server behavior directly.
You need to boot the server via [Hyinit](https://github.com/IroriPowered/hyinit) to apply fixes included in the early plugin.

To install:
1. Set up Hyinit to launch your server
2. Place `refixes-early-X.X.X.jar` inside the `earlyplugins` folder

### Refixes Main Plugin
Refixes Main Plugin mainly contains optimizations and light fixes through monitoring system and issue mitigations.
You can tweak some behaviors using the mod's config file at `mods/IroriPowered_Refixes/Refixes.json`.

To install:
1. Place `refixes-plugin-X.X.X.jar` inside the `mods` folder.
