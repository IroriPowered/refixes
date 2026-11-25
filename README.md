# Refixes

Server fixes and optional optimizations for Hytale 0.7.7 and 0.8.0-pre.6, based on Hyfixes and Hyzen Kernel. The same `refixes-X.X.X.jar` works on both versions. On newer versions of the same minor version Refixes applies only the early patches whose Hytale code is unchanged and lists the skipped ones at startup. On older versions it applies none, see [Compatibility](https://wiki.hytalemodding.dev/mod/refixes/installing-refixes#compatibility). Requires [Hyinit](https://github.com/IroriPowered/hyinit/releases/tag/0.5.0) 0.5.0 or newer. Install the single `refixes-X.X.X.jar` in `earlyplugins`.

[Documentation](https://wiki.hytalemodding.dev/mod/refixes) · [Releases](https://github.com/IroriPowered/refixes/releases) · [Downloads](https://www.curseforge.com/hytale/mods/refixes) · [Support Discord](https://discord.gg/y5kTgtQtgX)

## Building

`./gradlew singleJar -PhyinitJar=<path to Hyinit-0.5.0.jar>` writes `build/bundle/refixes-X.X.X.jar`. The build needs the Hyinit JAR to record the fingerprints. `bash scripts/fetch-hyinit.sh Hyinit.jar` downloads the release pinned in `gradle.properties` and checks its sha256.

[scripts/README.md](scripts/README.md) describes the porting worksheet and the live checks of `scripts/verify.py`.
