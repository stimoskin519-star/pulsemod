package com.rmenu;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Settings {
    public static boolean showCoords = false;
    public static boolean showFps = false;
    public static boolean showRecIndicator = true;
    public static boolean showMarks = true;
    /** Расстояние камеры от «призрака» в режиме 3-го лица (1..12). */
    public static int thirdDistance = 4;
    public static boolean autoFps = false;
    public static int targetFps = 90;
    public static int autoMaxView = 12;

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("rmenu.properties");

    private Settings() {}

    private static int clamp(String s, int def, int lo, int hi) {
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(s))); } catch (NumberFormatException e) { return def; }
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (InputStream in = Files.newInputStream(FILE)) {
            Properties p = new Properties();
            p.load(in);
            showCoords = Boolean.parseBoolean(p.getProperty("showCoords", "false"));
            showFps = Boolean.parseBoolean(p.getProperty("showFps", "false"));
            showRecIndicator = Boolean.parseBoolean(p.getProperty("showRecIndicator", "true"));
            showMarks = Boolean.parseBoolean(p.getProperty("showMarks", "true"));
            thirdDistance = clamp(p.getProperty("thirdDistance", "4"), 4, 1, 12);
            autoFps = Boolean.parseBoolean(p.getProperty("autoFps", "false"));
            targetFps = clamp(p.getProperty("targetFps", "90"), 90, 30, 240);
            autoMaxView = clamp(p.getProperty("autoMaxView", "12"), 12, 4, 32);
        } catch (IOException e) {
            RMenuClient.LOG.warn("Не удалось прочитать настройки", e);
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("showCoords", Boolean.toString(showCoords));
        p.setProperty("showFps", Boolean.toString(showFps));
        p.setProperty("showRecIndicator", Boolean.toString(showRecIndicator));
        p.setProperty("showMarks", Boolean.toString(showMarks));
        p.setProperty("thirdDistance", Integer.toString(thirdDistance));
        p.setProperty("autoFps", Boolean.toString(autoFps));
        p.setProperty("targetFps", Integer.toString(targetFps));
        p.setProperty("autoMaxView", Integer.toString(autoMaxView));
        Recorder.POOL.execute(() -> {
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "RMenu");
            } catch (IOException e) {
                RMenuClient.LOG.warn("Не удалось сохранить настройки", e);
            }
        });
    }
}
