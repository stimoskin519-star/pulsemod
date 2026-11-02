package com.rmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** HUD. Строки кэшируются и пересобираются только при изменении значения. */
public final class RHud {
    private static String coords = "";
    private static int lx = Integer.MIN_VALUE, ly, lz;
    private static String fps = "";
    private static long fpsNext;
    private static String rec = "";
    private static int recSec = -1;
    private static String play = "";
    private static int playCur = -1, playTot = -1, playView = -1;
    private static float playSpeed = -1;
    private static boolean playPaused;

    private RHud() {}

    private static String mmss(int s) {
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    public static void render(DrawContext g, RenderTickCounter tc) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden) return;
        TextRenderer tr = c.textRenderer;
        int sw = c.getWindow().getScaledWidth(), sh = c.getWindow().getScaledHeight();
        int y = 4;

        if (Settings.showFps) {
            long now = System.currentTimeMillis();
            if (now >= fpsNext) { fpsNext = now + 500; fps = c.getCurrentFps() + " FPS"; }
            g.drawText(tr, fps, 4, y, 0xFFFFFFFF, true);
            y += 10;
        }
        if (Settings.showCoords) {
            int bx = c.player.getBlockX(), by = c.player.getBlockY(), bz = c.player.getBlockZ();
            if (bx != lx || by != ly || bz != lz) {
                lx = bx; ly = by; lz = bz;
                coords = "XYZ: " + bx + " " + by + " " + bz;
            }
            g.drawText(tr, coords, 4, y, 0xFFFFFFFF, true);
        }

        if (Recorder.isRecording() && Settings.showRecIndicator) {
            int s = Recorder.seconds();
            if (s != recSec) { recSec = s; rec = "REC " + mmss(s); }
            int w = tr.getWidth(rec);
            int x = sw - w - 10;
            if (((System.currentTimeMillis() / 500) & 1) == 0) g.fill(x - 8, 6, x - 2, 12, 0xFFE5484D);
            g.drawText(tr, rec, x, 5, 0xFFFFFFFF, true);
        }

        if (Settings.showMarks) drawMarks(g, c, tr, sw, sh);

        if (ReplayPlayer.active()) {
            int cur = ReplayPlayer.currentSeconds(), tot = ReplayPlayer.totalSeconds();
            float sp = ReplayPlayer.speed();
            boolean pz = ReplayPlayer.paused;
            int vw = ReplayPlayer.viewMode();
            if (cur != playCur || tot != playTot || sp != playSpeed || pz != playPaused || vw != playView) {
                playCur = cur; playTot = tot; playSpeed = sp; playPaused = pz; playView = vw;
                play = "REPLAY " + (vw == 0 ? "1-е лицо" : "3-е лицо") + "  " + mmss(cur) + " / " + mmss(tot)
                        + "  x" + sp + (pz ? "  [пауза]" : "");
            }
            int bw = 220, bx = (sw - bw) / 2, by = sh - 52;
            g.drawText(tr, play, (sw - tr.getWidth(play)) / 2, by - 12, 0xFFFFFFFF, true);
            g.fill(bx, by, bx + bw, by + 4, 0xAA000000);
            g.fill(bx, by, bx + (int) (bw * ReplayPlayer.progress()), by + 4, 0xFF4C8DFF);
            for (int i = 0, n = ReplayPlayer.markerCount(); i < n; i++) {
                int mx = bx + (int) (bw * ReplayPlayer.markerFraction(i));
                g.fill(mx, by - 2, mx + 2, by + 6, 0xFFF5C518);
            }
        }
    }

    /** Проекция точки мира на экран без матриц: камера -> базис (вперёд/вправо/вверх) -> перспектива. */
    private static void drawMarks(DrawContext g, MinecraftClient c, TextRenderer tr, int sw, int sh) {
        List<Marks.Mark> list = Marks.current;
        if (list.isEmpty()) return;
        double cx, cy, cz, yaw, pit;
        if (ReplayPlayer.active()) {
            double[] s = ReplayPlayer.cameraSample();
            cx = s[0]; cy = s[1]; cz = s[2]; yaw = s[3]; pit = s[4];
        } else {
            float td = c.getRenderTickCounter().getTickProgress(false);
            Vec3d p = c.player.getCameraPosVec(td);
            cx = p.x; cy = p.y; cz = p.z;
            yaw = c.player.getYaw(td); pit = c.player.getPitch(td);
        }
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pit);
        double cyw = Math.cos(yr), syw = Math.sin(yr), cp = Math.cos(pr), sp = Math.sin(pr);
        double fx = -syw * cp, fy = -sp, fz = cyw * cp;
        double rx = -cyw, rz = -syw;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double tanV = Math.tan(Math.toRadians(c.options.getFov().getValue()) / 2.0);
        double aspect = (double) sw / sh;

        for (int i = 0, n = list.size(); i < n; i++) {
            Marks.Mark m = list.get(i);
            double dx = m.x - cx, dy = m.y - cy, dz = m.z - cz;
            double z = dx * fx + dy * fy + dz * fz;
            if (z < 0.5) continue;
            double xr = dx * rx + dz * rz;
            double yu = dx * ux + dy * uy + dz * uz;
            int px = (int) (sw / 2.0 + (xr / z) / (tanV * aspect) * sw / 2.0);
            int py = (int) (sh / 2.0 - (yu / z) / tanV * sh / 2.0);
            if (px < -40 || px > sw + 40 || py < -20 || py > sh + 20) continue;

            for (int r = -6; r <= 6; r++) {
                int half = 6 - Math.abs(r);
                g.fill(px - half, py + r, px + half + 1, py + r + 1, 0xFF2B1608);
            }
            for (int r = -4; r <= 4; r++) {
                int half = 4 - Math.abs(r);
                g.fill(px - half, py + r, px + half + 1, py + r + 1, m.color);
            }
            g.drawText(tr, m.name, px - tr.getWidth(m.name) / 2, py - 18, 0xFFFFFFFF, true);
            int d = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d != m.lastDist) { m.lastDist = d; m.distStr = d + " м"; }
            g.drawText(tr, m.distStr, px - tr.getWidth(m.distStr) / 2, py + 9, 0xFFE8EAF0, true);
        }
    }
}
