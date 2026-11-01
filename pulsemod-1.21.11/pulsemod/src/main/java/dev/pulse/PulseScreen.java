package dev.pulse;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Animated menu: sliding tab indicator, content slide/fade on tab change, smooth switches. */
public class PulseScreen extends Screen {
    private static final int W = 380, H = 250, SIDE = 96, TAB_H = 28, TAB_STEP = 34, ROW = 32, ROW_STEP = 38;
    private static final int ACCENT = 0x8B5CF6;

    private Category sel = Category.VISUAL;
    private float tabY, open, swap = 1f;
    private long last = System.nanoTime();

    public PulseScreen() { super(Text.literal("Pulse")); }

    @Override public boolean shouldPause() { return false; }

    private static int col(float a, int rgb) { return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | rgb; }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static void pill(DrawContext c, int x, int y, int w, int h, int argb) {
        c.fill(x + 1, y, x + w - 1, y + h, argb);
        c.fill(x, y + 1, x + w, y + h - 1, argb);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        float ticks = Math.min(2f, (now - last) / 50_000_000f);
        last = now;
        float k = 1f - (float) Math.exp(-ticks * 0.7f);
        open += (1f - open) * k;
        swap += (1f - swap) * k;
        tabY += (sel.ordinal() * TAB_STEP - tabY) * k;

        int x = (width - W) / 2, y = (height - H) / 2 + (int) ((1f - open) * 12);
        c.fill(0, 0, width, height, col(open * 0.43f, 0x000000));
        pill(c, x, y, W, H, col(open * 0.95f, 0x12121A));
        c.fill(x, y, x + SIDE, y + H, col(open * 0.95f, 0x0D0D14));
        c.drawTextWithShadow(textRenderer, "PULSE", x + 14, y + 14, col(open, ACCENT));

        // tabs
        int ty0 = y + 44;
        c.fill(x + 6, ty0 + (int) tabY, x + SIDE - 6, ty0 + (int) tabY + TAB_H, col(open, 0x2A2144));
        c.fill(x + 6, ty0 + (int) tabY, x + 8, ty0 + (int) tabY + TAB_H, col(open, ACCENT));
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            boolean s = cats[i] == sel;
            c.drawTextWithShadow(textRenderer, cats[i].label, x + 18, ty0 + i * TAB_STEP + 10, col(open, s ? 0xFFFFFF : 0x8A8A9A));
        }

        // modules
        int cx = x + SIDE + 14, cw = W - SIDE - 28;
        List<Module> list = ModuleManager.of(sel);
        float a = swap * open;
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int ry = y + 16 + i * ROW_STEP + (int) ((1f - swap) * 16);
            boolean hov = mx >= cx && mx <= cx + cw && my >= ry && my <= ry + ROW;
            pill(c, cx, ry, cw, ROW, col(a, hov ? 0x242433 : 0x1B1B26));
            c.drawTextWithShadow(textRenderer, m.name, cx + 10, ry + 6, col(a, 0xFFFFFF));
            c.drawTextWithShadow(textRenderer, m.desc, cx + 10, ry + 18, col(a, 0x7A7A8C));

            m.anim += ((m.enabled ? 1f : 0f) - m.anim) * k;
            int sx = cx + cw - 36, sy = ry + 11;
            pill(c, sx, sy, 26, 10, col(a, mix(0x3A3A4A, ACCENT, m.anim)));
            c.fill(sx + 2 + (int) (m.anim * 14), sy + 1, sx + 10 + (int) (m.anim * 14), sy + 9, col(a, 0xFFFFFF));
        }

        if (sel == Category.REPLAY)
            c.drawTextWithShadow(textRenderer, "R rec | P play | O pause | V cam | , . speed", cx, y + H - 16, col(a, 0x6A6A7C));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() == 0) {
            int x = (width - W) / 2, y = (height - H) / 2;
            Category[] cats = Category.values();
            for (int i = 0; i < cats.length; i++) {
                int ty = y + 44 + i * TAB_STEP;
                if (mx >= x + 6 && mx <= x + SIDE - 6 && my >= ty && my <= ty + TAB_H) {
                    if (cats[i] != sel) { sel = cats[i]; swap = 0f; }
                    return true;
                }
            }
            int cx = x + SIDE + 14, cw = W - SIDE - 28;
            List<Module> list = ModuleManager.of(sel);
            for (int i = 0; i < list.size(); i++) {
                int ry = y + 16 + i * ROW_STEP;
                if (mx >= cx && mx <= cx + cw && my >= ry && my <= ry + ROW) {
                    list.get(i).toggle();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }
}
