[**Support Discord Server**](https://discord.gg/y5kTgtQtgX)

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
The `pre-release` branch targets **Hytale Server 0.7.0-pre.5.1** and **JDK 25**. Boot the server through [Hyinit](https://github.com/IroriPowered/hyinit).

To install:
1. Set up Hyinit to launch your server
2. Place `refixes-X.X.X.jar` inside the `earlyplugins` folder
3. Remove any older `refixes-early-*` / `refixes-plugin-*` jars from `earlyplugins` and `mods`

Runtime settings (`config.json`) and patch toggles (`Refixes.json`) live in `mods/IroriPowered_Refixes/`.
See the [wiki](wiki/Home.md) for the full configuration and mixin reference.

## Pre-release verification

Built with JDK 25 against the published 0.7.0-pre.5.1 server artifact. With Hyinit 0.2.4, a strict transformation probe verified 77 enabled Mixin/target pairs using the default patches plus `SharedInstances`. An isolated, asset-loaded offline server enabled the packaged runtime plugin and shut down cleanly with a native Void default world.

These checks do not prove connected-client gameplay or optional patch behavior. The local asset snapshot is not a checksum-certified published asset package. The obsolete blanket block-health NPE handler and `isInThread()` overwrite were removed; native thread identity is preserved.
