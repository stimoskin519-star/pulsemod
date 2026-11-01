package dev.pulse;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** HUD drawing. Strings are cached and rebuilt only when values change. */
public final class Hud {
    static final int ACCENT = 0xFFFFC928, BG = 0x99301C0C, WHITE = 0xFFFFFFFF;
    private static int lastFps = -1, lx = Integer.MIN_VALUE, ly, lz, lastSec = -1, lastState = -1;
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
            int state = (cur / 20) * 4 + (Replay.paused ? 1 : 0) + (Replay.third ? 2 : 0);
            if (state != lastState) {
                lastState = state;
                timeStr = (Replay.paused ? "PAUSED " : "PLAY ") + (Replay.third ? "3P " : "1P ")
                        + (cur / 20) + "s / " + (total / 20) + "s  x" + Replay.speed;
            }
            int w = 190;
            c.fill(cx - w / 2, 6, cx + w / 2, 26, BG);
            c.drawTextWithShadow(tr, timeStr, cx - w / 2 + 6, 9, WHITE);
            c.fill(cx - w / 2 + 6, 20, cx + w / 2 - 6, 22, 0xFF5A3A1C);
            c.fill(cx - w / 2 + 6, 20, cx - w / 2 + 6 + (int) ((w - 12) * (cur / (float) total)), 22, ACCENT);
        }

        renderMarks(c, tr, mc, cx);
    }

    private static void renderMarks(DrawContext c, TextRenderer tr, MinecraftClient mc, int cx) {
        if (!ModuleManager.COMPASS.enabled || Marks.ALL.isEmpty()) return;
        String dim = Marks.dim(mc);
        Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        float camYaw = cam.getYaw();
        double px = cam.getX(), py = cam.getY(), pz = cam.getZ();
        int w = c.getScaledWindowWidth();
        int listY = 4, shown = 0;
        boolean bar = false;

        for (int i = 0; i < Marks.ALL.size(); i++) {
            Marks.Mark m = Marks.ALL.get(i);
            if (!m.dim.equals(dim)) continue;
            int col = Marks.COLORS[i % Marks.COLORS.length];
            double dx = m.x - px, dy = m.y - py, dz = m.z - pz;

            float rel = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - camYaw);
            if (Math.abs(rel) < 70f) {
                if (!bar) { bar = true; c.fill(cx - 100, 30, cx + 100, 40, BG); }
                int sx = cx + (int) (rel / 70f * 96f);
                c.fill(sx - 3, 31, sx + 4, 39, 0xFF301C0C);
                c.fill(sx - 2, 32, sx + 3, 38, col);
            }
            if (shown < 5) {
                int dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist != m.lastDist) { m.lastDist = dist; m.label = m.name + "  " + dist + "m"; }
                int tw = tr.getWidth(m.label) + 10;
                c.fill(w - tw - 4, listY, w - 4, listY + 13, BG);
                c.fill(w - 6, listY, w - 4, listY + 13, col);
                c.drawTextWithShadow(tr, m.label, w - tw, listY + 3, WHITE);
                listY += 16;
                shown++;
            }
        }
    }

    private Hud() {}
}
