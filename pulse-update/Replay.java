package dev.pulse;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

/**
 * Replay recorded from the player's first-person view (position, look, body
 * yaw, sneak/sprint/swing). Played back with a ghost entity that you can watch
 * from its eyes (1st person) or from behind (3rd person).
 */
public final class Replay {
    static final int MAGIC = 0x50554C32;
    private static final int MAX_FRAMES = 20 * 60 * 15;
    private static final int GHOST_ID = Integer.MAX_VALUE - 7;

    private static double[] pos = new double[3 * 1200];
    private static float[] rot = new float[3 * 1200];
    private static byte[] flg = new byte[1200];
    private static int size;

    public static boolean recording, playing, paused, third;
    public static float speed = 1f;
    private static float cursor;
    private static OtherClientPlayerEntity ghost;
    private static Perspective savedView;
    private static byte prevFlags;

    public static int size() { return size; }
    public static int cursor() { return (int) cursor; }
    public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("pulse/replays"); }

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
        final float[] r = Arrays.copyOf(rot, size * 3);
        final byte[] f = Arrays.copyOf(flg, size);
        final int n = size;
        final Path file = dir().resolve("replay-" + System.currentTimeMillis() + ".pulse");
        Thread t = new Thread(() -> save(file, p, r, f, n), "Pulse-Replay-Save");
        t.setDaemon(true);
        t.start();
        msg("Replay saved: " + (n / 20) + "s");
    }

    private static void tickRecord(MinecraftClient mc) {
        if (!recording || mc.player == null) return;
        if (size >= MAX_FRAMES) { ModuleManager.RECORD.setEnabled(false); return; }
        if (size >= flg.length) {
            int cap = flg.length * 2;
            pos = Arrays.copyOf(pos, cap * 3);
            rot = Arrays.copyOf(rot, cap * 3);
            flg = Arrays.copyOf(flg, cap);
        }
        int i = size++;
        var p = mc.player;
        pos[i * 3] = p.getX();
        pos[i * 3 + 1] = p.getY();
        pos[i * 3 + 2] = p.getZ();
        rot[i * 3] = p.getYaw();
        rot[i * 3 + 1] = p.getPitch();
        rot[i * 3 + 2] = p.getBodyYaw();
        byte f = 0;
        if (p.isSneaking()) f |= 1;
        if (p.isSprinting()) f |= 2;
        if (p.getHandSwingProgress(0f) > 0.01f) f |= 4;
        flg[i] = f;
    }

    // ---------- playback ----------
    public static void playFile(Path file, boolean thirdPerson) {
        if (recording) { msg("Stop recording first"); return; }
        if (playing) stopPlayback();
        if (!load(file)) { msg("Cannot read this replay"); return; }
        startPlayback(thirdPerson);
    }

    public static void playLast(boolean thirdPerson) {
        if (recording) { msg("Stop recording first"); return; }
        if (size < 2) {
            ReplayStore.refresh();
            if (!ReplayStore.ENTRIES.isEmpty()) load(ReplayStore.ENTRIES.get(0).path());
        }
        if (playing) stopPlayback();
        startPlayback(thirdPerson);
    }

    private static void startPlayback(boolean thirdPerson) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) { msg("Join a world first"); return; }
        if (size < 2) { msg("No replay yet - press R to record"); return; }

        cursor = 0; paused = false; third = thirdPerson; prevFlags = 0;
        ghost = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
        ghost.setId(GHOST_ID);
        ghost.setPosition(pos[0], pos[1], pos[2]);
        ghost.setYaw(rot[0]);
        ghost.setPitch(rot[1]);
        mc.world.addEntity(ghost);

        savedView = mc.options.getPerspective();
        mc.options.setPerspective(thirdPerson ? Perspective.THIRD_PERSON_BACK : Perspective.FIRST_PERSON);
        mc.setCameraEntity(ghost);
        playing = true;
    }

    public static void stopPlayback() {
        if (!playing) return;
        playing = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.getCameraEntity() == ghost) mc.setCameraEntity(mc.player);
        if (savedView != null) mc.options.setPerspective(savedView);
        if (mc.world != null && ghost != null) mc.world.removeEntity(GHOST_ID, Entity.RemovalReason.DISCARDED);
        ghost = null;
    }

    private static void tickPlayback(MinecraftClient mc) {
        if (!playing) return;
        if (mc.world == null || mc.player == null || ghost == null) { reset(); return; }

        if (!paused) cursor += speed;
        int i = (int) cursor;
        if (i >= size - 1) { stopPlayback(); return; }
        if (i < 0) { i = 0; cursor = 0; }

        ghost.setPosition(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
        ghost.setYaw(rot[i * 3]);
        ghost.setPitch(rot[i * 3 + 1]);
        ghost.setHeadYaw(rot[i * 3]);
        ghost.setBodyYaw(rot[i * 3 + 2]);
        byte f = flg[i];
        ghost.setSneaking((f & 1) != 0);
        ghost.setSprinting((f & 2) != 0);
        if ((f & 4) != 0 && (prevFlags & 4) == 0) ghost.swingHand(Hand.MAIN_HAND);
        prevFlags = f;

        if (mc.getCameraEntity() != ghost) mc.setCameraEntity(ghost);
        third = !mc.options.getPerspective().isFirstPerson();
    }

    public static void toggleView() {
        if (!playing) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        third = !third;
        mc.options.setPerspective(third ? Perspective.THIRD_PERSON_BACK : Perspective.FIRST_PERSON);
    }

    public static void togglePause() { if (playing) paused = !paused; }
    public static void changeSpeed(float mul) { speed = Math.max(0.25f, Math.min(8f, speed * mul)); }

    // ---------- lifecycle ----------
    public static void tick(MinecraftClient mc) { tickRecord(mc); tickPlayback(mc); }

    public static void reset() {
        if (playing && savedView != null) MinecraftClient.getInstance().options.setPerspective(savedView);
        recording = false; playing = false; ghost = null;
        ModuleManager.RECORD.enabled = false;
    }

    // ---------- files ----------
    private static void save(Path file, double[] p, float[] r, byte[] f, int n) {
        try {
            Files.createDirectories(dir());
            try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file), 1 << 16))) {
                o.writeInt(MAGIC);
                o.writeInt(n);
                for (int i = 0; i < n; i++) {
                    o.writeDouble(p[i * 3]); o.writeDouble(p[i * 3 + 1]); o.writeDouble(p[i * 3 + 2]);
                    o.writeFloat(r[i * 3]); o.writeFloat(r[i * 3 + 1]); o.writeFloat(r[i * 3 + 2]);
                    o.writeByte(f[i]);
                }
            }
        } catch (IOException ignored) {}
    }

    private static boolean load(Path file) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
            if (in.readInt() != MAGIC) return false;
            int n = in.readInt();
            if (n < 2 || n > MAX_FRAMES) return false;
            double[] p = new double[n * 3];
            float[] r = new float[n * 3];
            byte[] f = new byte[n];
            for (int i = 0; i < n; i++) {
                p[i * 3] = in.readDouble(); p[i * 3 + 1] = in.readDouble(); p[i * 3 + 2] = in.readDouble();
                r[i * 3] = in.readFloat(); r[i * 3 + 1] = in.readFloat(); r[i * 3 + 2] = in.readFloat();
                f[i] = in.readByte();
            }
            pos = p; rot = r; flg = f; size = n;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Copies a replay file to Downloads/PulseReplays. */
    public static void export(Path src) {
        Thread t = new Thread(() -> {
            String res;
            try {
                Path out = Paths.get(System.getProperty("user.home"), "Downloads", "PulseReplays");
                Files.createDirectories(out);
                Path dst = out.resolve(src.getFileName().toString());
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                res = "Exported to " + dst;
            } catch (IOException e) {
                res = "Export failed";
            }
            final String m = res;
            MinecraftClient.getInstance().execute(() -> msg(m));
        }, "Pulse-Replay-Export");
        t.setDaemon(true);
        t.start();
    }

    private static void msg(String m) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Pulse] " + m), true);
    }

    private Replay() {}
}
