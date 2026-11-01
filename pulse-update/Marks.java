package dev.pulse;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** World marks (waypoints), saved to config/pulse/marks.txt. */
public final class Marks {
    public static final class Mark {
        public final String name, dim;
        public final double x, y, z;
        public int lastDist = -1;
        public String label = "";
        Mark(String name, String dim, double x, double y, double z) {
            this.name = name; this.dim = dim; this.x = x; this.y = y; this.z = z;
        }
    }

    public static final List<Mark> ALL = new ArrayList<>();
    public static final int[] COLORS = {0xFFE53935, 0xFFFFC928, 0xFF4CAF50, 0xFF3F8AE0, 0xFFAB47BC, 0xFFFF8A3D};
    private static final int MAX = 100;

    private static ClientWorld lastWorld;
    private static String dim = "";
    private static int counter;

    /** Dimension id of the current world, cached until the world changes. */
    public static String dim(MinecraftClient mc) {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            dim = mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
        }
        return dim;
    }

    public static void add(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (ALL.size() >= MAX) { mc.player.sendMessage(Text.literal("[Pulse] Too many marks"), true); return; }
        String name = "Mark " + (++counter);
        ALL.add(new Mark(name, dim(mc), mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        save();
        mc.player.sendMessage(Text.literal("[Pulse] " + name + " placed"), true);
    }

    public static void remove(int idx) {
        if (idx >= 0 && idx < ALL.size()) { ALL.remove(idx); save(); }
    }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("pulse/marks.txt"); }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            for (String line : Files.readAllLines(f)) {
                String[] p = line.split(";");
                if (p.length != 5) continue;
                try {
                    ALL.add(new Mark(p[0], p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4])));
                } catch (NumberFormatException ignored) {}
            }
            counter = ALL.size();
        } catch (IOException ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            List<String> lines = new ArrayList<>(ALL.size());
            for (Mark m : ALL) lines.add(m.name + ";" + m.dim + ";" + m.x + ";" + m.y + ";" + m.z);
            Files.write(file(), lines);
        } catch (IOException ignored) {}
    }

    private Marks() {}
}
