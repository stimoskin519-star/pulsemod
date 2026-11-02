package com.rmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Просмотр записи. Камера: 1-е лицо — ровно то, что видел игрок при записи; 3-е лицо — свободная
 * орбита вокруг «призрака» (мышью, как в F5). Настоящий игрок на время просмотра заморожен и невидим.
 */
public final class ReplayPlayer {
    public record Entry(Path path, String name, long kb, boolean exported) {}

    private static final int GHOST_ID = -0x52454D;
    public static final float[] SPEEDS = {0.25f, 0.5f, 1f, 2f, 4f};

    private static Recording rec;
    private static Entry currentEntry;
    private static OtherClientPlayerEntity ghost;
    private static final double[] SAMPLE = new double[7];
    private static final double[] GHOST_S = new double[7];
    private static int view; // 0 = 1-е лицо, 1 = 3-е лицо
    private static int lastFrame, lastItem = Integer.MIN_VALUE;
    private static Perspective savedPersp;
    private static float savedYaw, savedPitch;
    private static KeyBinding[] frozen;

    public static volatile boolean loading;
    public static volatile List<Entry> files = List.of();
    public static boolean paused;
    public static int speedIdx = 2;
    public static double time;

    private ReplayPlayer() {}

    // ---------- состояние ----------

    public static boolean active() { return rec != null; }
    public static int viewMode() { return view; }
    public static Entry currentEntry() { return currentEntry; }
    public static float speed() { return SPEEDS[speedIdx]; }
    public static int totalSeconds() { return rec == null ? 0 : rec.size / 20; }
    public static int currentSeconds() { return (int) (time / 20.0); }
    public static double progress() { return rec == null || rec.size < 2 ? 0 : time / (rec.size - 1); }
    public static int markerCount() { return rec == null ? 0 : rec.markFrames.size(); }

    public static double markerFraction(int i) {
        return rec == null || rec.size < 2 ? 0 : (double) rec.markFrames.get(i) / (rec.size - 1);
    }

    public static void seek(double fraction) {
        if (rec == null) return;
        time = Math.max(0, Math.min(1, fraction)) * (rec.size - 1);
        lastFrame = (int) time;
    }

    public static void jumpMarker(int dir) {
        if (rec == null || rec.markFrames.isEmpty()) return;
        int cur = (int) time, best = -1;
        for (int f : rec.markFrames) {
            if (dir > 0 && f > cur + 1 && (best < 0 || f < best)) best = f;
            if (dir < 0 && f < cur - 1 && (best < 0 || f > best)) best = f;
        }
        if (best >= 0) { time = best; lastFrame = best; }
    }

    public static void togglePause() {
        if (rec == null) return;
        if (paused && time >= rec.size - 1) { time = 0; lastFrame = 0; }
        paused = !paused;
    }

    public static void setView(int v) {
        if (rec == null) return;
        view = v == 1 ? 1 : 0;
        if (view == 1) alignRealLook();
    }

    public static void toggleView() { setView(1 - view); }

    /** Для 3-го лица направление взгляда берётся из мыши — стартуем «за спиной» записанного игрока. */
    private static void alignRealLook() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null || rec == null) return;
        rec.sample(time, SAMPLE);
        p.setYaw((float) SAMPLE[3]);
        p.setPitch((float) Math.max(-20, Math.min(40, SAMPLE[4] + 10)));
    }

    // ---------- файлы ----------

    public static void refreshFiles() {
        Recorder.POOL.execute(() -> {
            List<Entry> list = new ArrayList<>();
            collect(Recorder.dir(), false, list);
            collect(Recorder.exportsDir(), true, list);
            list.sort(Comparator.comparing((Entry e) -> e.name()).reversed());
            files = list.size() > 6 ? List.copyOf(list.subList(0, 6)) : List.copyOf(list);
        });
    }

    private static void collect(Path dir, boolean exported, List<Entry> out) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".rmrp")).forEach(p -> {
                try {
                    String n = p.getFileName().toString();
                    out.add(new Entry(p, n.substring(0, n.length() - 5), Files.size(p) / 1024, exported));
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            RMenuClient.LOG.warn("Не удалось прочитать список записей", e);
        }
    }

    /** Сохраняет копию записи в replay_exports с выбранным видом (0 = от 1-го лица, 1 = от 3-го). */
    public static void export(Entry e, int viewMode) {
        if (e == null) return;
        Recorder.POOL.execute(() -> {
            String msg;
            try {
                Recording r = Recording.read(e.path());
                r.viewMode = viewMode;
                String base = e.name().replaceAll("_[13]p$", "");
                Path out = Recorder.exportsDir().resolve(base + (viewMode == 0 ? "_1p" : "_3p") + ".rmrp");
                r.write(out);
                msg = "Скачано: " + out.getFileName();
            } catch (IOException ex) {
                RMenuClient.LOG.error("Ошибка экспорта", ex);
                msg = "Не удалось скачать запись";
            }
            refreshFiles();
            Recorder.toast(msg);
        });
    }

    public static void openExportsFolder() {
        Recorder.POOL.execute(() -> {
            try {
                Files.createDirectories(Recorder.exportsDir());
                Util.getOperatingSystem().open(Recorder.exportsDir().toUri());
            } catch (IOException e) {
                RMenuClient.LOG.warn("Не удалось открыть папку", e);
            }
        });
    }

    // ---------- воспроизведение ----------

    /** startView: 0/1 — выбранный вид, -1 — как сохранено в файле (экспорт). */
    public static void play(Entry e, int startView) {
        if (loading || rec != null || Recorder.isRecording()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        loading = true;
        Recorder.POOL.execute(() -> {
            Recording r = null;
            try {
                r = Recording.read(e.path());
            } catch (IOException ex) {
                RMenuClient.LOG.error("Ошибка чтения записи", ex);
            }
            Recording result = r;
            mc.execute(() -> {
                loading = false;
                if (result == null || result.size < 2) { Recorder.toast("Не удалось открыть запись"); return; }
                start(mc, result, e, startView < 0 ? result.viewMode : startView);
            });
        });
    }

    private static void start(MinecraftClient mc, Recording r, Entry e, int startView) {
        ClientWorld world = mc.world;
        ClientPlayerEntity self = mc.player;
        if (world == null || self == null) return;
        rec = r;
        currentEntry = e;
        time = 0;
        lastFrame = 0;
        lastItem = Integer.MIN_VALUE;
        paused = false;
        savedYaw = self.getYaw();
        savedPitch = self.getPitch();
        savedPersp = mc.options.getPerspective();
        view = startView == 1 ? 1 : 0;
        try {
            ghost = new OtherClientPlayerEntity(world, self.getGameProfile());
            ghost.setId(GHOST_ID);
            r.sample(0, GHOST_S);
            ghost.setPosition(GHOST_S[0], GHOST_S[1], GHOST_S[2]);
            world.addEntity(ghost);
        } catch (Throwable t) {
            RMenuClient.LOG.warn("Не удалось создать призрака — просмотр без модели", t);
            ghost = null;
        }
        if (view == 1) alignRealLook();
    }

    public static void stop() {
        if (rec == null) return;
        rec = null;
        currentEntry = null;
        paused = false;
        if (ghost != null) {
            ghost.discard();
            ghost = null;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (savedPersp != null) mc.options.setPerspective(savedPersp);
        if (mc.player != null) {
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
        }
    }

    /** START_CLIENT_TICK: во время просмотра настоящий игрок не должен двигаться, бить и использовать предметы. */
    public static void freezeInput(MinecraftClient c) {
        if (rec == null || c.currentScreen != null) return;
        if (frozen == null) {
            GameOptions o = c.options;
            frozen = new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey,
                    o.sneakKey, o.sprintKey, o.attackKey, o.useKey, o.dropKey, o.swapHandsKey, o.pickItemKey};
        }
        for (KeyBinding k : frozen) {
            k.setPressed(false);
            while (k.wasPressed()) { /* сброс накопленных нажатий */ }
        }
    }

    public static void tick(MinecraftClient c) {
        if (rec == null) return;
        if (c.world == null || c.player == null) { stop(); return; }
        if (!paused) {
            time += speed();
            if (time >= rec.size - 1) { time = rec.size - 1; paused = true; }
        }
        Perspective want = view == 0 ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK;
        if (c.options.getPerspective() != want) c.options.setPerspective(want);

        if (ghost == null) return;
        // Призрак «забегает» на тик вперёд: рендер интерполирует его так же, как камеру.
        rec.sample(paused ? time : Math.min(time + speed(), rec.size - 1), GHOST_S);
        ghost.setPosition(GHOST_S[0], GHOST_S[1], GHOST_S[2]);
        ghost.setYaw((float) GHOST_S[3]);
        ghost.setPitch((float) GHOST_S[4]);
        ghost.setHeadYaw((float) GHOST_S[3]);
        ghost.setBodyYaw((float) GHOST_S[6]);
        ghost.setInvisible(view == 0);

        int cur = Math.min((int) time, rec.size - 1);
        int fl = rec.flag(cur);
        boolean sneak = (fl & Recording.F_SNEAK) != 0;
        ghost.setSneaking(sneak);
        ghost.setPose(sneak ? EntityPose.CROUCHING : EntityPose.STANDING);
        ghost.setSprinting((fl & Recording.F_SPRINT) != 0);
        if (cur > lastFrame && cur - lastFrame <= 8) {
            for (int f = lastFrame + 1; f <= cur; f++) {
                if ((rec.flag(f) & Recording.F_SWING) != 0) { ghost.swingHand(Hand.MAIN_HAND); break; }
            }
        }
        lastFrame = cur;
        int it = rec.item(cur);
        if (it != lastItem) {
            lastItem = it;
            ghost.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Registries.ITEM.get(it)));
        }
    }

    /** Камера: [0..2] позиция, [3..4] yaw/pitch. Возвращает общий массив (не сохранять!). */
    public static double[] cameraSample() {
        MinecraftClient mc = MinecraftClient.getInstance();
        float td = mc.getRenderTickCounter().getTickProgress(false);
        double t = time;
        if (!paused) t += td * speed();
        rec.sample(t, SAMPLE);
        double ex = SAMPLE[0], ey = SAMPLE[1] + SAMPLE[5], ez = SAMPLE[2];
        if (view == 1 && mc.player != null) {
            double yaw = mc.player.getYaw(td), pit = mc.player.getPitch(td);
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pit), cp = Math.cos(pr);
            int d = Settings.thirdDistance;
            ex += Math.sin(yr) * cp * d;
            ey += Math.sin(pr) * d;
            ez -= Math.cos(yr) * cp * d;
            SAMPLE[3] = yaw;
            SAMPLE[4] = pit;
        }
        SAMPLE[0] = ex; SAMPLE[1] = ey; SAMPLE[2] = ez;
        return SAMPLE;
    }
}
