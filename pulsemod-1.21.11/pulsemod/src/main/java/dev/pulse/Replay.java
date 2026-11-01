package dev.pulse;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Lightweight replay: records the player's path (position + look) every tick
 * into primitive arrays (no per-tick allocations), plays it back with a ghost
 * player entity and an optional camera on that ghost.
 */
public final class Replay {
    private static final int MAX_FRAMES = 20 * 60 * 15; // 15 minutes
    private static final int GHOST_ID = Integer.MAX_VALUE - 7;

    private static double[] pos = new double[3 * 1200];
    private static float[] rot = new float[2 * 1200];
    private static int size;

    public static boolean recording, playing, paused, ghostCam = true;
    public static float speed = 1f;
    private static float cursor;
    private static OtherClientPlayerEntity ghost;

    public static int size() { return size; }
    public static int cursor() { return (int) cursor; }

    // ---------- recording ----------
    public static void startRecording() {
        if (playing) { ModuleManager.RECORD.enabled = false; msg("Stop playback first"); return; }
        size = 0;
        recording = true;
    }

    public static void stopRecording() {
        if (!recording) return;
        recording = false;
        if (size < 2) return;
        final double[] p = Arrays.copyOf(pos, size * 3);
        final float[] r = Arrays.copyOf(rot, size * 2);
        final int n = size;
        Thread t = new Thread(() -> save(p, r, n), "Pulse-Replay-Save");
        t.setDaemon(true);
        t.start();
        msg("Replay saved: " + (n / 20) + "s");
    }

    private static void tickRecord(MinecraftClient mc) {
        if (!recording || mc.player == null) return;
        if (size >= MAX_FRAMES) { ModuleManager.RECORD.setEnabled(false); return; }
        if (size * 3 >= pos.length) {
            pos = Arrays.copyOf(pos, pos.length * 2);
            rot = Arrays.copyOf(rot, rot.length * 2);
        }
        int i = size++;
        pos[i * 3] = mc.player.getX();
        pos[i * 3 + 1] = mc.player.getY();
        pos[i * 3 + 2] = mc.player.getZ();
        rot[i * 2] = mc.player.getYaw();
        rot[i * 2 + 1] = mc.player.getPitch();
    }

    // ---------- playback ----------
    public static void startPlayback() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (recording) { fail("Stop recording first"); return; }
        if (mc.world == null || mc.player == null) { fail("Join a world first"); return; }
        if (size < 2) loadLatest();
        if (size < 2) { fail("No replay recorded yet"); return; }

        cursor = 0; paused = false; playing = true;
        ghost = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
        ghost.setId(GHOST_ID);
        ghost.setPosition(pos[0], pos[1], pos[2]);
        ghost.setYaw(rot[0]);
        ghost.setPitch(rot[1]);
        mc.world.addEntity(ghost);
    }

    public static void stopPlayback() {
        if (!playing) return;
        playing = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.getCameraEntity() == ghost) mc.setCameraEntity(mc.player);
        if (mc.world != null && ghost != null) mc.world.removeEntity(GHOST_ID, Entity.RemovalReason.DISCARDED);
        ghost = null;
    }

    private static void tickPlayback(MinecraftClient mc) {
        if (!playing) return;
        if (mc.world == null || mc.player == null || ghost == null) { reset(); return; }

        if (!paused) cursor += speed;
        int i = (int) cursor;
        if (i >= size - 1) {
            ModuleManager.PLAYBACK.enabled = false;
            stopPlayback();
            return;
        }
        if (i < 0) { i = 0; cursor = 0; }

        ghost.setPosition(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
        ghost.setYaw(rot[i * 2]);
        ghost.setPitch(rot[i * 2 + 1]);
        ghost.setHeadYaw(rot[i * 2]);
        ghost.setBodyYaw(rot[i * 2]);

        if (ghostCam) { if (mc.getCameraEntity() != ghost) mc.setCameraEntity(ghost); }
        else if (mc.getCameraEntity() == ghost) mc.setCameraEntity(mc.player);
    }

    public static void togglePause() { if (playing) paused = !paused; }
    public static void changeSpeed(float mul) { speed = Math.max(0.25f, Math.min(8f, speed * mul)); }

    // ---------- lifecycle ----------
    public static void tick(MinecraftClient mc) { tickRecord(mc); tickPlayback(mc); }

    /** Called on disconnect: drop state without touching the (gone) world. */
    public static void reset() {
        recording = false; playing = false; ghost = null;
        ModuleManager.RECORD.enabled = false;
        ModuleManager.PLAYBACK.enabled = false;
    }

    // ---------- disk ----------
    private static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("pulse/replays"); }

    private static void save(double[] p, float[] r, int n) {
        try {
            Files.createDirectories(dir());
            Path f = dir().resolve("replay-" + System.currentTimeMillis() + ".pulse");
            try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(f), 1 << 16))) {
                o.writeInt(n);
                for (int i = 0; i < n; i++) {
                    o.writeDouble(p[i * 3]); o.writeDouble(p[i * 3 + 1]); o.writeDouble(p[i * 3 + 2]);
                    o.writeFloat(r[i * 2]); o.writeFloat(r[i * 2 + 1]);
                }
            }
        } catch (IOException ignored) {}
    }

    private static void loadLatest() {
        try (Stream<Path> s = Files.list(dir())) {
            Path latest = s.filter(f -> f.toString().endsWith(".pulse")).max(Path::compareTo).orElse(null);
            if (latest == null) return;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(latest), 1 << 16))) {
                int n = in.readInt();
                if (n < 2 || n > MAX_FRAMES) return;
                double[] p = new double[n * 3];
                float[] r = new float[n * 2];
                for (int i = 0; i < n; i++) {
                    p[i * 3] = in.readDouble(); p[i * 3 + 1] = in.readDouble(); p[i * 3 + 2] = in.readDouble();
                    r[i * 2] = in.readFloat(); r[i * 2 + 1] = in.readFloat();
                }
                pos = p; rot = r; size = n;
            }
        } catch (IOException ignored) {}
    }

    private static void fail(String m) { ModuleManager.PLAYBACK.enabled = false; playing = false; msg(m); }

    private static void msg(String m) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Pulse] " + m), true);
    }

    private Replay() {}
}
