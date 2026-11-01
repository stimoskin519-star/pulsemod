package dev.pulse;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** HUD drawing. Strings are cached and rebuilt only when values change. */
public final class Hud {
    static final int ACCENT = 0xFF8B5CF6, BG = 0x99101018, WHITE = 0xFFFFFFFF;
    private static int lastFps = -1, lx = Integer.MIN_VALUE, ly, lz, lastSec = -1, lastFrame = -1;
    private static String fpsStr = "", posStr = "", timeStr = "";

    private static int box(DrawContext c, TextRenderer tr, String t, int y) {
        int w = tr.getWidth(t) + 10;
        c.fill(4, y, 4 + w, y + 13, BG);
        c.fill(4, y, 6, y + 13, ACCENT);
        c.drawTextWithShadow(tr, t, 10, y + 3, WHITE);
        return y + 16;
    }

    static void render(DrawContext c, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null) return;
        TextRenderer tr = mc.textRenderer;
        int y = 4;

        if (ModuleManager.WATERMARK.enabled) y = box(c, tr, "Pulse", y);
        if (ModuleManager.FPS.enabled) {
            int f = mc.getCurrentFps();
            if (f != lastFps) { lastFps = f; fpsStr = f + " FPS"; }
            y = box(c, tr, fpsStr, y);
        }
        if (ModuleManager.COORDS.enabled) {
            int x = (int) Math.floor(mc.player.getX()), yy = (int) Math.floor(mc.player.getY()), z = (int) Math.floor(mc.player.getZ());
            if (x != lx || yy != ly || z != lz) { lx = x; ly = yy; lz = z; posStr = x + " " + yy + " " + z; }
            box(c, tr, posStr, y);
        }

        int cx = c.getScaledWindowWidth() / 2;
        if (Replay.recording) {
            int sec = Replay.size() / 20;
            if (sec != lastSec) { lastSec = sec; timeStr = String.format("REC %02d:%02d", sec / 60, sec % 60); }
            int w = tr.getWidth(timeStr) + 18;
            c.fill(cx - w / 2, 6, cx + w / 2, 19, BG);
            if ((System.currentTimeMillis() / 500) % 2 == 0) c.fill(cx - w / 2 + 5, 10, cx - w / 2 + 10, 15, 0xFFFF3B3B);
            c.drawTextWithShadow(tr, timeStr, cx - w / 2 + 13, 9, WHITE);
        } else if (Replay.playing) {
            int cur = Replay.cursor(), total = Math.max(1, Replay.size());
            if (cur / 20 != lastFrame) {
                lastFrame = cur / 20;
                timeStr = (Replay.paused ? "PAUSED " : "PLAY ") + lastFrame + "s / " + (total / 20) + "s  x" + Replay.speed;
            }
            int w = 170;
            c.fill(cx - w / 2, 6, cx + w / 2, 26, BG);
            c.drawTextWithShadow(tr, timeStr, cx - w / 2 + 6, 9, WHITE);
            c.fill(cx - w / 2 + 6, 20, cx + w / 2 - 6, 22, 0xFF3A3A4A);
            c.fill(cx - w / 2 + 6, 20, cx - w / 2 + 6 + (int) ((w - 12) * (cur / (float) total)), 22, ACCENT);
        }
    }

    private Hud() {}
}
