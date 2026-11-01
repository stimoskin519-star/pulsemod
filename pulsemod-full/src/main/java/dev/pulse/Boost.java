package dev.pulse;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.ParticlesMode;

/** Lowers heavy video settings and restores the originals when turned off. */
public final class Boost {
    private static boolean applied;
    private static int view, sim, blend;
    private static double ent;
    private static ParticlesMode parts;
    private static boolean shadows, ao;

    public static void apply() {
        if (applied) return;
        GameOptions o = MinecraftClient.getInstance().options;
        view = o.getViewDistance().getValue();
        sim = o.getSimulationDistance().getValue();
        ent = o.getEntityDistanceScaling().getValue();
        parts = o.getParticles().getValue();
        shadows = o.getEntityShadows().getValue();
        ao = o.getAo().getValue();
        blend = o.getBiomeBlendRadius().getValue();
        applied = true;

        o.getViewDistance().setValue(Math.min(view, 8));
        o.getSimulationDistance().setValue(Math.min(sim, 6));
        o.getEntityDistanceScaling().setValue(Math.min(ent, 0.6));
        o.getParticles().setValue(ParticlesMode.MINIMAL);
        o.getEntityShadows().setValue(false);
        o.getAo().setValue(false);
        o.getBiomeBlendRadius().setValue(Math.min(blend, 1));
    }

    public static void restore() {
        if (!applied) return;
        applied = false;
        GameOptions o = MinecraftClient.getInstance().options;
        o.getViewDistance().setValue(view);
        o.getSimulationDistance().setValue(sim);
        o.getEntityDistanceScaling().setValue(ent);
        o.getParticles().setValue(parts);
        o.getEntityShadows().setValue(shadows);
        o.getAo().setValue(ao);
        o.getBiomeBlendRadius().setValue(blend);
    }

    private Boost() {}
}
