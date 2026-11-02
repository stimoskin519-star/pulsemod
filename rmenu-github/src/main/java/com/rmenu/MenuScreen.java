package com.rmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Меню в стиле Angry Birds: небо, облака, трава, красная птица, деревянная панель, толстые
 * «мультяшные» кнопки с обводкой и пружинящие (bounce) появления. Рисуется только fill-примитивами,
 * без текстур; анимации привязаны к реальному времени.
 */
public final class MenuScreen extends Screen {
    private enum Page { HOME, RECORD, REPLAYS, MARKS, FPS, UTILS }

    private static final int PW = 420, PH = 244;
    private static final int RED = 0xFFE23B3B, BLUE = 0xFF3C8DE0, GREEN = 0xFF58B833,
            YELLOW = 0xFFF5C518, WOOD = 0xFFB9783A, PURPLE = 0xFF8E5BD0;
    private static final int INK = 0xFF2B1608, WHITE = 0xFFFFFFFF;
    private static final int SCRUB_Y = 64;

    private static final class Btn {
        final int rx, ry, w, h, color;
        final Supplier<String> label;
        final Runnable action;   // null -> подпись (если color == 0) либо неактивная кнопка
        final boolean chrome;    // привязана к панели, а не к области содержимого
        float hov;

        Btn(int rx, int ry, int w, int h, Supplier<String> label, Runnable action, int color, boolean chrome) {
            this.rx = rx; this.ry = ry; this.w = w; this.h = h;
            this.label = label; this.action = action; this.color = color; this.chrome = chrome;
        }
    }

    private static final String[] TILE_NAMES = {"ЗАПИСЬ", "РЕПЛЕИ", "МЕТКИ", "FPS", "УТИЛИТЫ"};
    private static final int[] TILE_COLORS = {RED, BLUE, YELLOW, GREEN, PURPLE};
    private static final Page[] TILE_PAGES = {Page.RECORD, Page.REPLAYS, Page.MARKS, Page.FPS, Page.UTILS};

    private final List<Btn> btns = new ArrayList<>();
    private final Btn backBtn, closeBtn;
    private final float[] tileHov = new float[5];

    private Page page = Page.HOME;
    private long lastNs = System.nanoTime();
    private final long openNs = lastNs;
    private long pageNs = lastNs;
    private int sel, markPage;
    private boolean scrubbing;

    // абсолютные координаты, пересчитываются в render и используются при кликах
    private int px, py, ix, iy, oy;

    // состояние, при изменении которого страница пересобирается
    private boolean bActive, bLoading, bRec, bPaused;
    private int bView;
    private List<ReplayPlayer.Entry> bFiles;
    private List<Marks.Mark> bMarks;

    public MenuScreen() {
        super(Text.literal("RMenu"));
        backBtn = new Btn(-10, -10, 30, 30, () -> "<", () -> go(Page.HOME), GREEN, true);
        closeBtn = new Btn(PW - 20, -10, 30, 30, () -> "X", this::close, RED, true);
    }

    @Override
    protected void init() {
        ReplayPlayer.refreshFiles();
        build();
    }

    @Override
    public boolean shouldPause() { return false; }

    private void go(Page p) {
        page = p;
        pageNs = System.nanoTime();
        build();
    }

    // ================= построение страниц =================

    private int iw() { return PW - 40; }

    private void add(int x, int y, int w, int h, Supplier<String> l, Runnable a, int color) {
        btns.add(new Btn(x, y, w, h, l, a, color, false));
    }

    private void label(int x, int y, Supplier<String> l) {
        btns.add(new Btn(x, y, 0, 0, l, null, 0, false));
    }

    private void build() {
        btns.clear();
        bActive = ReplayPlayer.active();
        bLoading = ReplayPlayer.loading;
        bRec = Recorder.isRecording();
        bFiles = ReplayPlayer.files;
        bMarks = Marks.current;
        bView = ReplayPlayer.viewMode();
        bPaused = ReplayPlayer.paused;
        switch (page) {
            case RECORD -> buildRecord();
            case REPLAYS -> buildReplays();
            case MARKS -> buildMarks();
            case FPS -> buildFps();
            case UTILS -> buildUtils();
            default -> { }
        }
    }

    private boolean stateChanged() {
        return bActive != ReplayPlayer.active() || bLoading != ReplayPlayer.loading || bRec != Recorder.isRecording()
                || bFiles != ReplayPlayer.files || bMarks != Marks.current || bView != ReplayPlayer.viewMode()
                || bPaused != ReplayPlayer.paused;
    }

    private void buildRecord() {
        int iw = iw();
        add(0, 6, iw, 36, () -> Recorder.isRecording() ? "ОСТАНОВИТЬ ЗАПИСЬ" : "НАЧАТЬ ЗАПИСЬ (F8)",
                Recorder::toggle, RED);
        label(0, 54, () -> Recorder.isRecording()
                ? "Идёт запись от 1-го лица: " + Recorder.seconds() + " с  (" + Recorder.frames() + " кадров)"
                : "Запись снимает всё глазами игрока: позу камеры, шаги,");
        label(0, 66, () -> Recorder.isRecording() ? "Метки (B) попадают на шкалу реплея." : "приседание, бег, взмахи руки и предмет в руке.");
        add(0, 92, iw, 26, () -> "ПОСТАВИТЬ МЕТКУ (B)", () -> Marks.addHere(MinecraftClient.getInstance()), YELLOW);
        label(0, 132, () -> "Записи лежат в replay_recordings/. До 2 часов на запись.");
        label(0, 144, () -> "Смотреть и скачивать — на странице «РЕПЛЕИ».");
    }

    private void buildReplays() {
        int iw = iw();
        if (ReplayPlayer.active()) {
            add(0, 0, 76, 22, () -> ReplayPlayer.paused ? "ИГРАТЬ" : "ПАУЗА", ReplayPlayer::togglePause, BLUE);
            add(82, 0, 22, 22, () -> "-", () -> ReplayPlayer.speedIdx = Math.max(0, ReplayPlayer.speedIdx - 1), WOOD);
            add(108, 0, 46, 22, () -> "x" + ReplayPlayer.speed(), null, WOOD);
            add(158, 0, 22, 22, () -> "+", () -> ReplayPlayer.speedIdx = Math.min(ReplayPlayer.SPEEDS.length - 1, ReplayPlayer.speedIdx + 1), WOOD);
            add(iw - 76, 0, 76, 22, () -> "СТОП", ReplayPlayer::stop, RED);

            add(0, 28, 176, 22, () -> ReplayPlayer.viewMode() == 0 ? "ВИД: ОТ 1-ГО ЛИЦА (F7)" : "ВИД: ОТ 3-ГО ЛИЦА (F7)",
                    ReplayPlayer::toggleView, YELLOW);
            if (ReplayPlayer.viewMode() == 1) {
                add(182, 28, 22, 22, () -> "-", () -> { Settings.thirdDistance = Math.max(1, Settings.thirdDistance - 1); Settings.save(); }, WOOD);
                add(208, 28, 84, 22, () -> "ДИСТ. " + Settings.thirdDistance, null, WOOD);
                add(296, 28, 22, 22, () -> "+", () -> { Settings.thirdDistance = Math.min(12, Settings.thirdDistance + 1); Settings.save(); }, WOOD);
                label(0, 54, () -> "Мышь — вращать камеру вокруг игрока.");
            }
            add(0, 96, 130, 22, () -> "< МЕТКА", () -> ReplayPlayer.jumpMarker(-1), WOOD);
            add(136, 96, 130, 22, () -> "МЕТКА >", () -> ReplayPlayer.jumpMarker(1), WOOD);
            add(0, 126, 186, 22, () -> "СКАЧАТЬ: ОТ 1-ГО ЛИЦА", () -> ReplayPlayer.export(ReplayPlayer.currentEntry(), 0), GREEN);
            add(192, 126, 186, 22, () -> "СКАЧАТЬ: ОТ 3-ГО ЛИЦА", () -> ReplayPlayer.export(ReplayPlayer.currentEntry(), 1), GREEN);
            return;
        }
        if (ReplayPlayer.loading) {
            label(0, 20, () -> "Загрузка записи...");
            return;
        }
        List<ReplayPlayer.Entry> files = ReplayPlayer.files;
        if (files.isEmpty()) {
            label(0, 10, () -> "Пока нет записей.");
            label(0, 24, () -> "Начните запись на странице «ЗАПИСЬ» (F8).");
            add(0, 50, 190, 22, () -> "ПАПКА ЭКСПОРТА", ReplayPlayer::openExportsFolder, YELLOW);
            return;
        }
        if (sel >= files.size()) sel = 0;
        for (int i = 0; i < files.size(); i++) {
            final int idx = i;
            ReplayPlayer.Entry e = files.get(i);
            add(0, 2 + i * 26, 214, 22, () -> (e.exported() ? "[экспорт] " : "") + e.name() + "  " + e.kb() + " КБ",
                    () -> sel = idx, i == sel ? BLUE : WOOD);
        }
        ReplayPlayer.Entry e = files.get(sel);
        int x = 224, w = iw - x;
        if (!e.exported()) {
            add(x, 2, w, 22, () -> "СМОТРЕТЬ: 1-Е ЛИЦО", () -> ReplayPlayer.play(e, 0), BLUE);
            add(x, 28, w, 22, () -> "СМОТРЕТЬ: 3-Е ЛИЦО", () -> ReplayPlayer.play(e, 1), BLUE);
            add(x, 60, w, 22, () -> "СКАЧАТЬ: 1-Е ЛИЦО", () -> ReplayPlayer.export(e, 0), GREEN);
            add(x, 86, w, 22, () -> "СКАЧАТЬ: 3-Е ЛИЦО", () -> ReplayPlayer.export(e, 1), GREEN);
        } else {
            add(x, 2, w, 22, () -> "СМОТРЕТЬ", () -> ReplayPlayer.play(e, -1), BLUE);
            label(x, 30, () -> "Вид сохранён в файле.");
        }
        add(x, 124, w, 22, () -> "ПАПКА ЭКСПОРТА", ReplayPlayer::openExportsFolder, YELLOW);
    }

    private void buildMarks() {
        int iw = iw();
        add(0, 0, 214, 24, () -> "ПОСТАВИТЬ МЕТКУ (B)", () -> Marks.addHere(MinecraftClient.getInstance()), YELLOW);
        add(220, 0, iw - 220, 24, () -> Settings.showMarks ? "В МИРЕ: ВКЛ" : "В МИРЕ: ВЫКЛ",
                () -> { Settings.showMarks = !Settings.showMarks; Settings.save(); }, Settings.showMarks ? GREEN : WOOD);
        List<Marks.Mark> list = Marks.current;
        int pages = Math.max(1, (list.size() + 4) / 5);
        if (markPage >= pages) markPage = pages - 1;
        if (list.isEmpty()) {
            label(0, 40, () -> "В этом мире меток нет.");
            label(0, 54, () -> "Метка ставится туда, куда вы смотрите.");
            return;
        }
        for (int i = 0; i < 5; i++) {
            int idx = markPage * 5 + i;
            if (idx >= list.size()) break;
            Marks.Mark m = list.get(idx);
            add(0, 32 + i * 26, iw - 50, 22, () -> m.name + "   " + distTo(m) + " м", null, m.color);
            add(iw - 44, 32 + i * 26, 44, 22, () -> "X", () -> Marks.remove(m), RED);
        }
        if (pages > 1) {
            add(0, 164, 30, 16, () -> "<", () -> { markPage = Math.max(0, markPage - 1); build(); }, WOOD);
            add(36, 164, 30, 16, () -> ">", () -> { markPage = Math.min(pages - 1, markPage + 1); build(); }, WOOD);
            label(74, 168, () -> (markPage + 1) + " / " + pages);
        }
    }

    private static int distTo(Marks.Mark m) {
        var p = MinecraftClient.getInstance().player;
        if (p == null) return 0;
        double dx = m.x - p.getX(), dy = m.y - p.getY(), dz = m.z - p.getZ();
        return (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void buildFps() {
        int iw = iw();
        GameOptions o = MinecraftClient.getInstance().options;
        label(0, 2, () -> "FPS сейчас: " + MinecraftClient.getInstance().getCurrentFps()
                + "   дальность: " + o.getViewDistance().getValue());
        int w = (iw - 12) / 3;
        add(0, 16, w, 24, () -> "ЭКО", () -> Perf.preset(0), GREEN);
        add(w + 6, 16, w, 24, () -> "БАЛАНС", () -> Perf.preset(1), YELLOW);
        add((w + 6) * 2, 16, w, 24, () -> "КАЧЕСТВО", () -> Perf.preset(2), BLUE);
        add(0, 46, iw, 26, () -> "МАКС. FPS (ТУРБО)", Perf::turbo, RED);

        add(0, 80, 196, 22, () -> Settings.autoFps ? "АВТО-FPS: ВКЛ" : "АВТО-FPS: ВЫКЛ", () -> {
            Settings.autoFps = !Settings.autoFps;
            if (Settings.autoFps) Settings.autoMaxView = MinecraftClient.getInstance().options.getViewDistance().getValue();
            Settings.save();
        }, Settings.autoFps ? GREEN : WOOD);
        add(202, 80, 22, 22, () -> "-", () -> { Settings.targetFps = Math.max(30, Settings.targetFps - 10); Settings.save(); }, WOOD);
        add(228, 80, 84, 22, () -> "ЦЕЛЬ " + Settings.targetFps, null, WOOD);
        add(316, 80, 22, 22, () -> "+", () -> { Settings.targetFps = Math.min(240, Settings.targetFps + 10); Settings.save(); }, WOOD);

        add(0, 108, 186, 22, () -> o.getGraphicsMode().getValue() == GraphicsMode.FAST ? "ГРАФИКА: БЫСТРАЯ" : "ГРАФИКА: КРАСИВАЯ",
                Perf::toggleGraphics, WOOD);
        add(192, 108, 186, 22, () -> o.getAo().getValue() ? "СГЛАЖ. СВЕТА: ВКЛ" : "СГЛАЖ. СВЕТА: ВЫКЛ", Perf::toggleAo, WOOD);
        add(0, 136, 186, 22, () -> o.getEnableVsync().getValue() ? "VSYNC: ВКЛ" : "VSYNC: ВЫКЛ", Perf::toggleVsync, WOOD);
        add(192, 136, 186, 22, () -> o.getMaxFps().getValue() >= 260 ? "ЛИМИТ FPS: НЕТ" : "ЛИМИТ FPS: " + o.getMaxFps().getValue(),
                Perf::cycleMaxFps, WOOD);
    }

    private void buildUtils() {
        int iw = iw();
        add(0, 4, iw, 24, () -> "КООРДИНАТЫ: " + (Settings.showCoords ? "ВКЛ" : "ВЫКЛ"),
                () -> { Settings.showCoords = !Settings.showCoords; Settings.save(); build(); }, Settings.showCoords ? GREEN : WOOD);
        add(0, 34, iw, 24, () -> "СЧЁТЧИК FPS: " + (Settings.showFps ? "ВКЛ" : "ВЫКЛ"),
                () -> { Settings.showFps = !Settings.showFps; Settings.save(); build(); }, Settings.showFps ? GREEN : WOOD);
        add(0, 64, iw, 24, () -> "ИНДИКАТОР ЗАПИСИ: " + (Settings.showRecIndicator ? "ВКЛ" : "ВЫКЛ"),
                () -> { Settings.showRecIndicator = !Settings.showRecIndicator; Settings.save(); build(); }, Settings.showRecIndicator ? GREEN : WOOD);
        label(0, 102, () -> "Клавиши (меняются в «Управление»):");
        label(0, 116, () -> "Right Shift — меню     F8 — запись     B — метка");
        label(0, 128, () -> "F9 — пауза реплея     F7 — вид 1-е/3-е лицо");
    }

    // ================= рисование =================

    private static float clamp01(float t) { return Math.max(0f, Math.min(1f, t)); }

    private static float bounce(float t) {
        t = clamp01(t);
        if (t < 1 / 2.75f) return 7.5625f * t * t;
        if (t < 2 / 2.75f) { t -= 1.5f / 2.75f; return 7.5625f * t * t + 0.75f; }
        if (t < 2.5f / 2.75f) { t -= 2.25f / 2.75f; return 7.5625f * t * t + 0.9375f; }
        t -= 2.625f / 2.75f;
        return 7.5625f * t * t + 0.984375f;
    }

    private static int shade(int c, float f) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (f < 0) { r = (int) (r * (1 + f)); g = (int) (g * (1 + f)); b = (int) (b * (1 + f)); }
        else { r += (int) ((255 - r) * f); g += (int) ((255 - g) * f); b += (int) ((255 - b) * f); }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Прямоугольник со скруглением: центр + построчные «ступеньки» по углам. */
    private static void rrect(DrawContext g, int x, int y, int w, int h, int r, int c) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, c); return; }
        g.fill(x, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
    }

    private static void box(DrawContext g, int x, int y, int w, int h, int r, int fill, int outline) {
        rrect(g, x - 2, y - 2, w + 4, h + 4, r + 2, outline);
        rrect(g, x, y, w, h, r, fill);
    }

    /** Белый текст с тёмной обводкой — «мультяшный» вид. */
    private void otxt(DrawContext g, String s, int x, int y) {
        g.drawText(textRenderer, s, x - 1, y, INK, false);
        g.drawText(textRenderer, s, x + 1, y, INK, false);
        g.drawText(textRenderer, s, x, y - 1, INK, false);
        g.drawText(textRenderer, s, x, y + 1, INK, false);
        g.drawText(textRenderer, s, x, y, WHITE, false);
    }

    private void otxtC(DrawContext g, String s, int cx, int y) {
        otxt(g, s, cx - textRenderer.getWidth(s) / 2, y);
    }

    private void drawBtn(DrawContext g, Btn b, int ax, int ay, float hov) {
        int lift = Math.round(hov * 2);
        int y = ay - lift;
        int base = hov > 0.5f ? shade(b.color, 0.12f) : b.color;
        rrect(g, ax - 2, y - 2, b.w + 4, b.h + 5, 5, INK);                   // обводка
        rrect(g, ax, y + 2, b.w, b.h, 4, shade(b.color, -0.35f));            // «толщина» снизу
        rrect(g, ax, y, b.w, b.h, 4, base);                                  // тело
        rrect(g, ax + 2, y + 2, b.w - 4, Math.max(3, b.h / 3), 3, shade(b.color, 0.35f)); // блик
        String s = b.label.get();
        otxtC(g, s, ax + b.w / 2, y + (b.h - 8) / 2);
    }

    private void cloud(DrawContext g, int x, int y, int s) {
        rrect(g, x, y + s / 3, s * 3, s * 2 / 3, s / 3, 0xF0FFFFFF);
        rrect(g, x + s / 2, y, s * 3 / 2, s, s / 2, 0xF0FFFFFF);
        rrect(g, x + s * 3 / 2 + s / 4, y + s / 6, s, s * 5 / 6, s / 2, 0xF0FFFFFF);
    }

    private void bird(DrawContext g, int cx, int cy, int r) {
        // хвост-хохолок
        rrect(g, cx - 4, cy - r - 6, 4, 8, 2, INK);
        rrect(g, cx + 2, cy - r - 4, 4, 6, 2, INK);
        rrect(g, cx - 3, cy - r - 5, 2, 8, 1, RED);
        // тело
        box(g, cx - r, cy - r, r * 2, r * 2, r, RED, INK);
        rrect(g, cx - r + 4, cy + 2, r * 2 - 8, r - 4, (r - 4) / 2, 0xFFF4E3C2); // светлое пузико
        // глаза
        rrect(g, cx - r / 2 - 4, cy - r / 2 - 4, 10, 12, 5, INK);
        rrect(g, cx + r / 2 - 6, cy - r / 2 - 4, 10, 12, 5, INK);
        rrect(g, cx - r / 2 - 3, cy - r / 2 - 3, 8, 10, 4, WHITE);
        rrect(g, cx + r / 2 - 5, cy - r / 2 - 3, 8, 10, 4, WHITE);
        g.fill(cx - r / 2 + 1, cy - r / 2, cx - r / 2 + 4, cy - r / 2 + 4, INK);
        g.fill(cx + r / 2 - 4, cy - r / 2, cx + r / 2 - 1, cy - r / 2 + 4, INK);
        // «злые» брови
        for (int i = 0; i < 12; i++) {
            g.fill(cx - r / 2 - 6 + i, cy - r / 2 - 9 + i / 4, cx - r / 2 - 5 + i, cy - r / 2 - 6 + i / 4, INK);
            g.fill(cx + r / 2 + 6 - i, cy - r / 2 - 9 + i / 4, cx + r / 2 + 7 - i, cy - r / 2 - 6 + i / 4, INK);
        }
        // клюв
        rrect(g, cx - 5, cy - 1, 14, 9, 3, INK);
        rrect(g, cx - 4, cy, 12, 7, 3, YELLOW);
        rrect(g, cx - 4, cy + 4, 12, 3, 2, 0xFFE8A010);
    }

    private void tileIcon(DrawContext g, int i, int cx, int cy) {
        switch (i) {
            case 0 -> {
                rrect(g, cx - 16, cy - 16, 32, 32, 16, WHITE);
                rrect(g, cx - 11, cy - 11, 22, 22, 11, RED);
                rrect(g, cx - 6, cy - 6, 12, 12, 6, WHITE);
            }
            case 1 -> {
                for (int r = 0; r < 30; r++) {
                    int w = (int) ((15 - Math.abs(r - 15)) * 26 / 15.0);
                    g.fill(cx - 9, cy - 15 + r, cx - 9 + w, cy - 14 + r, WHITE);
                }
            }
            case 2 -> {
                for (int r = -15; r <= 15; r++) {
                    int half = 15 - Math.abs(r);
                    g.fill(cx - half, cy + r, cx + half + 1, cy + r + 1, WHITE);
                }
                rrect(g, cx - 5, cy - 5, 10, 10, 5, YELLOW);
            }
            case 3 -> {
                g.fill(cx - 16, cy + 4, cx - 8, cy + 15, WHITE);
                g.fill(cx - 4, cy - 5, cx + 4, cy + 15, WHITE);
                g.fill(cx + 8, cy - 15, cx + 16, cy + 15, WHITE);
            }
            default -> {
                for (int k = 0; k < 3; k++) {
                    g.fill(cx - 16, cy - 12 + k * 12, cx + 16, cy - 8 + k * 12, WHITE);
                    rrect(g, cx - 10 + ((k * 11) % 20), cy - 15 + k * 12, 8, 10, 4, 0xFF4A2A80);
                }
            }
        }
    }

    private void badge(DrawContext g, int x, int y, String s) {
        int w = textRenderer.getWidth(s) + 8;
        rrect(g, x - w + 2, y - 2, w + 2, 14, 6, INK);
        rrect(g, x - w + 3, y - 1, w, 12, 5, WHITE);
        g.drawText(textRenderer, s, x - w + 7, y + 1, INK, false);
    }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = (now - lastNs) / 1e9f;
        lastNs = now;
        float secs = (now - openNs) / 1e9f;
        if (stateChanged()) build();

        // ---- фон: небо, солнце, облака, холмы, трава ----
        g.fillGradient(0, 0, width, height, 0xE05BC8F5, 0xE0D6F2FF);
        rrect(g, width - 96, 18, 56, 56, 28, 0xFFFFF3A0);
        rrect(g, width - 90, 24, 44, 44, 22, 0xFFFFE066);
        int span = width + 240;
        cloud(g, (int) ((secs * 14 + 40) % span) - 120, 24, 14);
        cloud(g, (int) ((secs * 9 + 260) % span) - 120, 70, 10);
        cloud(g, (int) ((secs * 20 + 520) % span) - 120, 12, 8);
        int gy = height - 34;
        rrect(g, -60, gy - 28, 220, 120, 60, 0xFF6DBE3A);
        rrect(g, width - 200, gy - 40, 300, 140, 70, 0xFF5CAE2E);
        g.fill(0, gy, width, gy + 6, 0xFF7ED04B);
        g.fill(0, gy + 6, width, gy + 10, 0xFF5FA832);
        g.fill(0, gy + 10, width, height, 0xFF8B5A2B);
        for (int x = 0; x < width; x += 24) g.fill(x + 6, gy + 16, x + 14, gy + 20, 0xFF7A4A24);

        // ---- панель (падает сверху с отскоком) ----
        float drop = 1f - bounce((now - openNs) / 700e6f);
        px = (width - PW) / 2;
        py = (height - PH) / 2 - Math.round(drop * (PH + 80));
        box(g, px, py, PW, PH, 8, 0xFFD39A5B, INK);
        g.fill(px + 4, py + 4, px + PW - 4, py + 8, 0xFFE8B97E);
        for (int yy = py + 36; yy < py + PH - 6; yy += 28) g.fill(px + 4, yy, px + PW - 4, yy + 1, 0xFFB9783A);
        rrect(g, px + 12, py + 40, PW - 24, PH - 52, 6, INK);
        rrect(g, px + 14, py + 42, PW - 28, PH - 56, 5, 0xFFF3DDB5);

        // ---- лента-заголовок ----
        String title = page == Page.HOME ? "RMENU" : TILE_NAMES[page.ordinal() - 1];
        box(g, px + PW / 2 - 80, py - 14, 160, 28, 6, RED, INK);
        g.fill(px + PW / 2 - 76, py - 11, px + PW / 2 + 76, py - 8, 0x55FFFFFF);
        otxtC(g, title, px + PW / 2, py - 4);

        // ---- птица: прыгает на траве ----
        int hop = (int) (Math.abs(Math.sin(secs * 2.2)) * 7);
        bird(g, 44, gy - 20 - hop, 20);

        // ---- содержимое ----
        ix = px + 20;
        iy = py + 50;
        float pt = bounce((now - pageNs) / 450e6f);
        oy = -Math.round((1f - pt) * 36);

        if (page == Page.HOME) renderHome(g, mx, my, dt, now);
        else renderBtns(g, mx, my, dt);

        // ---- кнопки панели ----
        if (page != Page.HOME) renderChrome(g, backBtn, mx, my, dt);
        renderChrome(g, closeBtn, mx, my, dt);
    }

    private void renderChrome(DrawContext g, Btn b, int mx, int my, float dt) {
        int ax = px + b.rx, ay = py + b.ry;
        boolean hov = mx >= ax && mx < ax + b.w && my >= ay && my < ay + b.h;
        b.hov += ((hov ? 1f : 0f) - b.hov) * (1f - (float) Math.exp(-dt * 18f));
        drawBtn(g, b, ax, ay, b.hov);
    }

    private void renderHome(DrawContext g, int mx, int my, float dt, long now) {
        int tw = 70, th = 86, gap = 7;
        int total = 5 * tw + 4 * gap;
        int x0 = ix + (iw() - total) / 2;
        for (int i = 0; i < 5; i++) {
            float t = (now - pageNs - i * 70_000_000L) / 450e6f;
            if (t <= 0) continue;
            int x = x0 + i * (tw + gap);
            int y = iy + 22 - Math.round((1f - bounce(t)) * 120);
            boolean hov = t >= 1f && mx >= x && mx < x + tw && my >= y && my < y + th;
            tileHov[i] += ((hov ? 1f : 0f) - tileHov[i]) * (1f - (float) Math.exp(-dt * 18f));
            y -= Math.round(tileHov[i] * 4);
            int c = TILE_COLORS[i];
            box(g, x, y, tw, th, 7, hov ? shade(c, 0.12f) : c, INK);
            rrect(g, x + 3, y + 3, tw - 6, 14, 5, shade(c, 0.35f));
            tileIcon(g, i, x + tw / 2, y + 38);
            rrect(g, x + 4, y + th - 24, tw - 8, 20, 5, INK);
            rrect(g, x + 5, y + th - 23, tw - 10, 18, 4, shade(c, -0.25f));
            otxtC(g, TILE_NAMES[i], x + tw / 2, y + th - 18);
            if (i == 0 && Recorder.isRecording() && ((now / 400_000_000L) & 1) == 0) rrect(g, x + tw - 14, y + 6, 8, 8, 4, RED);
            if (i == 1 && !ReplayPlayer.files.isEmpty()) badge(g, x + tw - 4, y + 4, String.valueOf(ReplayPlayer.files.size()));
            if (i == 2 && !Marks.current.isEmpty()) badge(g, x + tw - 4, y + 4, String.valueOf(Marks.current.size()));
            if (i == 3) badge(g, x + tw - 4, y + 4, String.valueOf(MinecraftClient.getInstance().getCurrentFps()));
        }
        String hint = Recorder.isRecording() ? "Идёт запись... (F8 — стоп)"
                : ReplayPlayer.active() ? "Идёт просмотр реплея — страница «РЕПЛЕИ»" : "Выберите раздел";
        g.drawText(textRenderer, hint, ix + (iw() - textRenderer.getWidth(hint)) / 2, iy + 128, 0xFF6B4423, false);
        g.drawText(textRenderer, "Right Shift — открыть/закрыть   Esc — назад", ix + (iw() - textRenderer.getWidth("Right Shift — открыть/закрыть   Esc — назад")) / 2,
                iy + 146, 0xFF8A6A44, false);
    }

    private void renderBtns(DrawContext g, int mx, int my, float dt) {
        boolean ready = oy == 0;
        for (Btn b : btns) {
            int ax = ix + b.rx, ay = iy + b.ry + oy;
            if (b.color == 0) { // подпись
                g.drawText(textRenderer, b.label.get(), ax, ay, 0xFF5A3417, false);
                continue;
            }
            boolean hov = ready && b.action != null && mx >= ax && mx < ax + b.w && my >= ay && my < ay + b.h;
            b.hov += ((hov ? 1f : 0f) - b.hov) * (1f - (float) Math.exp(-dt * 18f));
            drawBtn(g, b, ax, ay, b.hov);
        }
        if (page == Page.REPLAYS && ReplayPlayer.active()) renderScrub(g);
    }

    private void renderScrub(DrawContext g) {
        int x = ix, y = iy + SCRUB_Y + oy, w = iw() - 2;
        double p = ReplayPlayer.progress();
        rrect(g, x - 2, y - 2, w + 4, 14, 6, INK);
        rrect(g, x, y, w, 10, 5, 0xFF7A4A24);
        int fw = Math.max(0, (int) (w * p));
        if (fw > 0) rrect(g, x, y, Math.max(fw, 8), 10, 5, GREEN);
        for (int i = 0, n = ReplayPlayer.markerCount(); i < n; i++) {
            int mx2 = x + (int) (w * ReplayPlayer.markerFraction(i));
            g.fill(mx2 - 1, y - 5, mx2 + 2, y + 15, INK);
            g.fill(mx2, y - 4, mx2 + 1, y + 14, YELLOW);
        }
        int kx = x + (int) (w * p);
        rrect(g, kx - 8, y - 4, 16, 18, 8, INK);
        rrect(g, kx - 6, y - 2, 12, 14, 6, RED);
        String t = mmss(ReplayPlayer.currentSeconds()) + " / " + mmss(ReplayPlayer.totalSeconds());
        g.drawText(textRenderer, t, x, y + 18, 0xFF5A3417, false);
    }

    private static String mmss(int s) {
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    // ================= ввод =================

    private boolean overScrub(double mx, double my) {
        if (!(page == Page.REPLAYS && ReplayPlayer.active())) return false;
        int y = iy + SCRUB_Y + oy;
        return mx >= ix - 8 && mx <= ix + iw() + 8 && my >= y - 8 && my <= y + 18;
    }

    private void scrubTo(double mx) {
        ReplayPlayer.seek((mx - ix) / (iw() - 2));
    }

    private static boolean inside(Btn b, int ax, int ay, double mx, double my) {
        return mx >= ax && mx < ax + b.w && my >= ay && my < ay + b.h;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x(), my = click.y();

        if (inside(closeBtn, px + closeBtn.rx, py + closeBtn.ry, mx, my)) { closeBtn.action.run(); return true; }
        if (page != Page.HOME && inside(backBtn, px + backBtn.rx, py + backBtn.ry, mx, my)) { backBtn.action.run(); return true; }
        if (oy != 0) return true;

        if (page == Page.HOME) {
            int tw = 70, th = 86, gap = 7;
            int x0 = ix + (iw() - (5 * tw + 4 * gap)) / 2;
            for (int i = 0; i < 5; i++) {
                int x = x0 + i * (tw + gap), y = iy + 22;
                if (mx >= x && mx < x + tw && my >= y && my < y + th) { go(TILE_PAGES[i]); return true; }
            }
            return true;
        }
        if (overScrub(mx, my)) { scrubbing = true; scrubTo(mx); return true; }
        for (Btn b : btns) {
            if (b.action == null || b.color == 0) continue;
            if (inside(b, ix + b.rx, iy + b.ry + oy, mx, my)) {
                b.action.run();
                build();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (scrubbing) { scrubTo(click.x()); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        scrubbing = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        if (k == GLFW.GLFW_KEY_ESCAPE && page != Page.HOME) { go(Page.HOME); return true; }
        return super.keyPressed(input);
    }
}
