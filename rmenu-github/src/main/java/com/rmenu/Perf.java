package com.rmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;

/**
 * Оптимизация FPS средствами настроек + адаптивная дальность прорисовки («Авто-FPS»).
 * Мод не переписывает рендер — для максимума ставьте рядом Sodium/Lithium.
 */
public final class Perf {
    private static int ticks, sum, samples;

    private Perf() {}

    public static void preset(int p) {
        GameOptions o = MinecraftClient.getInstance().options;
        int[] view = {6, 12, 20}, sim = {5, 8, 12}, blend = {0, 1, 2};
        double[] ent = {0.5, 0.75, 1.0};
        ParticlesMode[] part = {ParticlesMode.MINIMAL, ParticlesMode.DECREASED, ParticlesMode.ALL};
        CloudRenderMode[] cloud = {CloudRenderMode.OFF, CloudRenderMode.FAST, CloudRenderMode.FANCY};
        GraphicsMode[] gfx = {GraphicsMode.FAST, GraphicsMode.FANCY, GraphicsMode.FANCY};
        o.getViewDistance().setValue(view[p]);
        o.getSimulationDistance().setValue(sim[p]);
        o.getBiomeBlendRadius().setValue(blend[p]);
        o.getEntityDistanceScaling().setValue(ent[p]);
        o.getParticles().setValue(part[p]);
        o.getCloudRenderMode().setValue(cloud[p]);
        o.getGraphicsMode().setValue(gfx[p]);
        o.getEntityShadows().setValue(p > 0);
        o.getAo().setValue(p > 0);
        Settings.autoMaxView = view[p];
        Settings.save();
        o.write();
    }

    /** Максимум FPS: «Эко» + без VSync и без лимита кадров. */
    public static void turbo() {
        preset(0);
        GameOptions o = MinecraftClient.getInstance().options;
        o.getEnableVsync().setValue(false);
        o.getMaxFps().setValue(260);
        o.write();
    }

    public static void toggleGraphics() {
        GameOptions o = MinecraftClient.getInstance().options;
        o.getGraphicsMode().setValue(o.getGraphicsMode().getValue() == GraphicsMode.FAST ? GraphicsMode.FANCY : GraphicsMode.FAST);
        o.write();
    }

    public static void toggleAo() {
        GameOptions o = MinecraftClient.getInstance().options;
        o.getAo().setValue(!o.getAo().getValue());
        o.write();
    }

    public static void toggleVsync() {
        GameOptions o = MinecraftClient.getInstance().options;
        o.getEnableVsync().setValue(!o.getEnableVsync().getValue());
        o.write();
    }

    public static void cycleMaxFps() {
        GameOptions o = MinecraftClient.getInstance().options;
        int cur = o.getMaxFps().getValue();
        o.getMaxFps().setValue(cur <= 60 ? 120 : cur <= 120 ? 260 : 60);
        o.write();
    }

    /** Раз в секунду замер FPS; раз в 3 секунды — шаг дальности прорисовки с гистерезисом (без «качелей»). */
    public static void tick(MinecraftClient c) {
        if (!Settings.autoFps || c.world == null || c.currentScreen != null || ReplayPlayer.active()) {
            ticks = 0; sum = 0; samples = 0;
            return;
        }
        if (++ticks < 20) return;
        ticks = 0;
        sum += c.getCurrentFps();
        if (++samples < 3) return;
        int avg = sum / samples;
        sum = 0; samples = 0;
        var opt = c.options.getViewDistance();
        int v = opt.getValue();
        if (avg < Settings.targetFps * 0.9 && v > 4) opt.setValue(v - 1);
        else if (avg > Settings.targetFps * 1.35 && v < Settings.autoMaxView) opt.setValue(v + 1);
    }
}
