package cc.irori.refixes.early.duck;

import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;

public interface LightReuseState {

    boolean refixes$lightReusable(short changeCounter);

    boolean refixes$sameLightInputs(long heights, FluidSection fluids, int fluidVersion);

    void refixes$recordLightInputs(long heights, FluidSection fluids, int fluidVersion);
}
