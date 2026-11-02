package com.rmenu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Метки: хранятся по миру/серверу и измерению. Рисуются на HUD проекцией (без рендер-хуков). */
public final class Marks {
    public static final class Mark {
        public final String world, name;
        public final double x, y, z;
        public final int color;
        public int lastDist = -1;
        public String distStr = "";

        Mark(String world, String name, double x, double y, double z, int color) {
            this.world = world; this.name = name; this.x = x; this.y = y; this.z = z; this.color = color;
        }
    }

    public static final int[] PALETTE = {0xFFE23B3B, 0xFF3C8DE0, 0xFF58B833, 0xFFF5C518, 0xFF8E5BD0, 0xFFFF8A2B};
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("rmenu_marks.txt");
    private static final List<Mark> ALL = new ArrayList<>();
    private static Object lastWorld;
    private static String key;

    /** Метки текущего мира (неизменяемый список — заменяется целиком). */
    public static volatile List<Mark> current = List.of();

    private Marks() {}

    private static String worldKey(MinecraftClient c) {
        if (c.world == null) return null;
        String dim = c.world.getRegistryKey().getValue().toString();
        ServerInfo si = c.getCurrentServerEntry();
        String base;
        if (si != null) base = si.address;
        else if (c.getServer() != null) base = "sp:" + c.getServer().getSaveProperties().getLevelName();
        else base = "?";
        return base + "|" + dim;
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            for (String line : Files.readAllLines(FILE)) {
                String[] p = line.split("\t");
                if (p.length < 6) continue;
                try {
                    ALL.add(new Mark(p[0], p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                            Double.parseDouble(p[4]), Integer.parseInt(p[5])));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            RMenuClient.LOG.warn("Не удалось прочитать метки", e);
        }
    }

    private static void save() {
        StringBuilder sb = new StringBuilder();
        for (Mark m : ALL) {
            sb.append(m.world).append('\t').append(m.name).append('\t').append(m.x).append('\t')
                    .append(m.y).append('\t').append(m.z).append('\t').append(m.color).append('\n');
        }
        String data = sb.toString();
        Recorder.POOL.execute(() -> {
            try {
                Files.writeString(FILE, data);
            } catch (IOException e) {
                RMenuClient.LOG.warn("Не удалось сохранить метки", e);
            }
        });
    }

    private static void rebuild() {
        List<Mark> list = new ArrayList<>();
        if (key != null) for (Mark m : ALL) if (m.world.equals(key)) list.add(m);
        current = List.copyOf(list);
    }

    public static void tick(MinecraftClient c) {
        if (c.world != lastWorld) {
            lastWorld = c.world;
            key = worldKey(c);
            rebuild();
        }
    }

    /** Ставит метку туда, куда смотрит игрок (блок), иначе — под ногами. Заодно — метка в идущей записи. */
    public static void addHere(MinecraftClient c) {
        if (c.world == null || c.player == null || key == null) return;
        Vec3d pos = c.player.getPos();
        if (c.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            pos = bhr.getPos();
        }
        int n = current.size() + 1;
        String name = "Метка " + n;
        ALL.add(new Mark(key, name, pos.x, pos.y, pos.z, PALETTE[(n - 1) % PALETTE.length]));
        rebuild();
        save();
        Recorder.markNow(name);
        Recorder.toast("Поставлена: " + name);
    }

    public static void remove(Mark m) {
        ALL.remove(m);
        rebuild();
        save();
    }
}
