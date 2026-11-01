package dev.pulse;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Cartoon-style menu: wooden panel, chunky buttons, bouncy animations. */
public class PulseScreen extends Screen {
    private static final int W = 420, H = 272, ROW = 30, STEP = 36;
    private static final int WOOD = 0xFF9C6A3A, WOOD_DARK = 0xFF4A2C14, PLANK = 0xFFE2B06A, CREAM = 0xFFFFE7B0,
            CREAM_HOV = 0xFFFFF3D0, RED = 0xFFE53935, YELLOW = 0xFFFFC928, GREEN = 0xFF4CAF50, BLUE = 0xFF3F8AE0,
            INK = 0xFF3A2410, SOFT = 0xFF8A6A40, WHITE = 0xFFFFFFFF;
    private static final int T_TAB = 0, T_MOD = 1, T_PLAY1 = 2, T_PLAY3 = 3, T_EXPORT = 4, T_ADD = 5, T_DEL = 6, T_STOP = 7;

    private final int[] hx = new int[96], hy = new int[96], hw = new int[96], hh = new int[96], ht = new int[96], hi = new int[96];
    private int hc;

    private Category sel = Category.VISUAL;
    private float open, swap = 1f, tabPop;
    private long last = System.nanoTime();

    public PulseScreen() {
        super(Text.literal("Pulse"));
        ReplayStore.refresh();
    }

    @Override public boolean shouldPause() { return false; }

    // ---------- helpers ----------
    private static float back(float t) {
        t = Math.min(1f, Math.max(0f, t));
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int col(float a, int rgb) {
        return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private void hit(int x, int y, int w, int h, int type, int idx) {
        if (hc >= hx.length) return;
        hx[hc] = x; hy[hc] = y; hw[hc] = w; hh[hc] = h; ht[hc] = type; hi[hc] = idx;
        hc++;
    }

    private void box(DrawContext c, int x, int y, int w, int h, int fill) {
        c.fill(x - 2, y - 2, x + w + 2, y + h + 2, WOOD_DARK);
        c.fill(x, y, x + w, y + h, fill);
    }

    private void btn(DrawContext c, int x, int y, int w, int h, int fill, String label, int tc, float pop) {
        int p = (int) (pop * 3);
        x -= p; y -= p; w += 2 * p; h += 2 * p;
        c.fill(x - 2, y - 2, x + w + 2, y + h + 2, WOOD_DARK);
        c.fill(x, y, x + w, y + h, fill);
        c.fill(x + 2, y + 2, x + w - 2, y + 4, 0x55FFFFFF);
        int tw = textRenderer.getWidth(label);
        c.drawText(textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2 + 1, tc, false);
    }

    private void moduleRow(DrawContext c, Module m, int idx, int cx, int cw, int ry, float k, int mx, int my) {
        boolean hov = mx >= cx && mx <= cx + cw && my >= ry && my <= ry + ROW;
        m.anim += ((m.enabled ? 1f : 0f) - m.anim) * k;
        m.pop += (0f - m.pop) * k;
        box(c, cx, ry, cw, ROW, hov ? CREAM_HOV : CREAM);
        c.drawText(textRenderer, m.name, cx + 10, ry + 6, INK, false);
        c.drawText(textRenderer, m.desc, cx + 10, ry + 18, SOFT, false);
        btn(c, cx + cw - 54, ry + 6, 44, 18, mix(RED, GREEN, m.anim), m.enabled ? "ON" : "OFF", WHITE, m.pop);
        hit(cx, ry, cw, ROW, T_MOD, idx);
    }

    // ---------- render ----------
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        float ticks = Math.min(2f, (now - last) / 50_000_000f);
        last = now;
        float k = 1f - (float) Math.exp(-ticks * 0.7f);
        open += (1f - open) * k * 0.8f;
        swap += (1f - swap) * k;
        tabPop += (0f - tabPop) * k;
        hc = 0;

        // sky + grass
        c.fillGradient(0, 0, width, height, col(open * 0.55f, 0x4FB4F0), col(open * 0.55f, 0xBDE8FF));
        c.fill(0, height - 22, width, height, col(open, 0x5DBB3F));
        c.fill(0, height - 22, width, height - 18, col(open, 0x3E8E2A));

        int x = (width - W) / 2;
        int y0 = (height - H) / 2;
        int y = y0 + (int) ((1f - back(open)) * -(y0 + H + 10));

        // panel
        box(c, x, y, W, H, WOOD);
        box(c, x + 10, y + 64, W - 20, H - 64 - 10, PLANK);

        // title
        c.drawText(textRenderer, "PULSE", x + 18, y + 16, WOOD_DARK, false);
        c.drawText(textRenderer, "PULSE", x + 17, y + 15, YELLOW, false);
        c.drawText(textRenderer, "PULSE", x + 18, y + 15, YELLOW, false);
        if (Replay.playing) {
            btn(c, x + W - 86, y + 10, 70, 20, RED, "STOP", WHITE, 0);
            hit(x + W - 86, y + 10, 70, 20, T_STOP, 0);
        }

        // tabs
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            boolean s = cats[i] == sel;
            btn(c, x + 14 + i * 96, y + 34, 90, 22, s ? RED : PLANK, cats[i].label, s ? WHITE : INK, s ? tabPop : 0f);
            hit(x + 14 + i * 96, y + 34, 90, 22, T_TAB, i);
        }

        int cx = x + 16, cw = W - 32;
        int top = y + 72 + (int) ((1f - swap) * 18);

        switch (sel) {
            case VISUAL -> {
                List<Module> l = ModuleManager.of(Category.VISUAL);
                for (int i = 0; i < l.size(); i++) moduleRow(c, l.get(i), i, cx, cw, top + i * STEP, k, mx, my);
            }
            case BOOST -> {
                moduleRow(c, ModuleManager.BOOST, 0, cx, cw, top, k, mx, my);
                c.drawText(textRenderer, "Sets view 8, simulation 6, minimal particles,", cx + 4, top + STEP + 4, INK, false);
                c.drawText(textRenderer, "no shadows / smooth light. Originals come back on OFF.", cx + 4, top + STEP + 16, INK, false);
                c.drawText(textRenderer, "For the biggest FPS gain also install Sodium + Lithium.", cx + 4, top + STEP + 34, SOFT, false);
            }
            case REPLAY -> renderReplay(c, cx, cw, top, k, mx, my);
            case MARKS -> renderMarks(c, cx, cw, top);
        }

        String foot = switch (sel) {
            case REPLAY -> "R rec | P play | O pause | V 1st/3rd | , . speed";
            case MARKS -> "B = place a mark. Marks show on the compass at the top.";
            default -> "";
        };
        c.drawText(textRenderer, foot, x + 18, y + H - 20, SOFT, false);
    }

    private void renderReplay(DrawContext c, int cx, int cw, int top, float k, int mx, int my) {
        moduleRow(c, ModuleManager.RECORD, 0, cx, cw, top, k, mx, my);
        List<ReplayStore.Entry> es = ReplayStore.ENTRIES;
        if (es.isEmpty()) {
            c.drawText(textRenderer, "No replays yet - press R to record", cx + 4, top + STEP + 10, INK, false);
            return;
        }
        for (int i = 0; i < es.size() && i < 4; i++) {
            int ry = top + (i + 1) * STEP;
            box(c, cx, ry, cw, ROW, CREAM);
            c.drawText(textRenderer, es.get(i).label(), cx + 10, ry + 11, INK, false);
            int bx = cx + cw - 46;
            btn(c, bx, ry + 6, 40, 18, BLUE, "SAVE", WHITE, 0);
            hit(bx, ry + 6, 40, 18, T_EXPORT, i);
            bx -= 34;
            btn(c, bx, ry + 6, 30, 18, YELLOW, "3P", INK, 0);
            hit(bx, ry + 6, 30, 18, T_PLAY3, i);
            bx -= 34;
            btn(c, bx, ry + 6, 30, 18, GREEN, "1P", WHITE, 0);
            hit(bx, ry + 6, 30, 18, T_PLAY1, i);
        }
    }

    private void renderMarks(DrawContext c, int cx, int cw, int top) {
        btn(c, cx, top, cw, ROW, GREEN, "+ PLACE MARK HERE  [B]", WHITE, 0);
        hit(cx, top, cw, ROW, T_ADD, 0);
        String dim = Marks.dim(client);
        int shown = 0;
        for (int i = Marks.ALL.size() - 1; i >= 0 && shown < 4; i--) {
            Marks.Mark m = Marks.ALL.get(i);
            if (!m.dim.equals(dim)) continue;
            int ry = top + (shown + 1) * STEP;
            box(c, cx, ry, cw, ROW, CREAM);
            c.fill(cx + 8, ry + 8, cx + 22, ry + 22, WOOD_DARK);
            c.fill(cx + 10, ry + 10, cx + 20, ry + 20, Marks.COLORS[i % Marks.COLORS.length]);
            double dx = m.x - client.player.getX(), dy = m.y - client.player.getY(), dz = m.z - client.player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
            c.drawText(textRenderer, m.name + "   " + dist + "m", cx + 30, ry + 11, INK, false);
            btn(c, cx + cw - 32, ry + 6, 24, 18, RED, "X", WHITE, 0);
            hit(cx + cw - 32, ry + 6, 24, 18, T_DEL, i);
            shown++;
        }
        if (shown == 0) c.drawText(textRenderer, "No marks in this world yet.", cx + 4, top + STEP + 10, INK, false);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
            for (int j = hc - 1; j >= 0; j--) {
                if (mx >= hx[j] && mx <= hx[j] + hw[j] && my >= hy[j] && my <= hy[j] + hh[j]) {
                    act(ht[j], hi[j]);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void act(int type, int idx) {
        switch (type) {
            case T_TAB -> {
                Category cat = Category.values()[idx];
                if (cat != sel) { sel = cat; swap = 0f; tabPop = 1f; }
            }
            case T_MOD -> {
                List<Module> l = ModuleManager.of(sel);
                if (idx < l.size()) { Module m = l.get(idx); m.toggle(); m.pop = 1f; }
            }
            case T_PLAY1 -> { Replay.playFile(ReplayStore.ENTRIES.get(idx).path(), false); close(); }
            case T_PLAY3 -> { Replay.playFile(ReplayStore.ENTRIES.get(idx).path(), true); close(); }
            case T_EXPORT -> Replay.export(ReplayStore.ENTRIES.get(idx).path());
            case T_ADD -> Marks.add(client);
            case T_DEL -> Marks.remove(idx);
            case T_STOP -> Replay.stopPlayback();
            default -> {}
        }
    }
}
