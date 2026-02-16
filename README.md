# Refixes
Work in progress patches for Hytale server.

> #### ⚠️ **WARNING**
> Requires the server to be booted using [Hyinit](https://github.com/IroriPowered/Hyinit)  
> This project is still in early development and is NOT yet ready for production use!

## Current Project Goals
- [x] Port early plugin patches from Hyfixes/HyzenKernel to Mixin
- [ ] Port main plugin patches / features if needed
- [x] Target Hytale Update 3

## Skipped Patches
- Shared Instance System
    - To make Refixes easier to update to new Hytale releases, we want to avoid making drastic logic changes.
- World Task Shutdown Guard
    - This is not much of an issue in my opinion. Task queue should properly throw when it's not available. 
- (Temp) Operation Timeout
    - Requires a config system for custom timeout duration.
