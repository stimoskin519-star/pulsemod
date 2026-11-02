package com.rmenu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Recorder {
    /** Один фоновый поток для всего дискового ввода-вывода. */
    public static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rmenu-io");
        t.setDaemon(true);
        return t;
    });
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static Recording current;
    private static boolean lastSwing;

    private Recorder() {}

    public static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("replay_recordings");
    }

    public static Path exportsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("replay_exports");
    }

    public static boolean isRecording() { return current != null; }

    public static int frames() {
        Recording r = current;
        return r == null ? 0 : r.size;
    }

    public static int seconds() { return frames() / 20; }

    public static void toggle() {
        if (current == null) {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.world == null || c.player == null || ReplayPlayer.active()) return;
            current = new Recording(2048);
            lastSwing = false;
        } else {
            stop();
        }
    }

    public static void tick(MinecraftClient c) {
        Recording r = current;
        if (r == null) return;
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null) { stop(); return; }
        int fl = 0;
        if (p.isSneaking()) fl |= Recording.F_SNEAK;
        if (p.isSprinting()) fl |= Recording.F_SPRINT;
        boolean sw = p.handSwinging;
        if (sw && !lastSwing) fl |= Recording.F_SWING;
        lastSwing = sw;
        r.add(p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(),
                (float) (p.getEyeY() - p.getY()), p.getBodyYaw(), fl,
                Registries.ITEM.getRawId(p.getMainHandStack().getItem()));
        if (r.full()) stop();
    }

    /** Ставит метку на текущем кадре записи (если идёт запись). */
    public static void markNow(String name) {
        Recording r = current;
        if (r != null && r.size > 0) r.addMarker(r.size - 1, name);
    }

    public static void stop() {
        Recording r = current;
        current = null;
        if (r == null || r.size < 2) return;
        String name = FMT.format(LocalDateTime.now()) + ".rmrp";
        Path path = dir().resolve(name);
        POOL.execute(() -> {
            String msg;
            try {
                r.write(path);
                msg = "Запись сохранена: " + name;
            } catch (IOException e) {
                RMenuClient.LOG.error("Ошибка сохранения записи", e);
                msg = "Не удалось сохранить запись";
            }
            ReplayPlayer.refreshFiles();
            toast(msg);
        });
    }

    static void toast(String msg) {
        MinecraftClient c = MinecraftClient.getInstance();
        c.execute(() -> {
            if (c.player != null) c.player.sendMessage(Text.literal(msg), true);
        });
    }
}
