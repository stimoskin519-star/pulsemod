package dev.pulse;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class ModuleManager {
    private static final Runnable NOP = () -> {};
    private static final EnumMap<Category, List<Module>> BY_CAT = new EnumMap<>(Category.class);

    public static final Module WATERMARK = reg(new Module("Watermark", "Pulse label on the HUD", Category.VISUAL, true, NOP, NOP));
    public static final Module FPS = reg(new Module("FPS", "Frames per second", Category.VISUAL, true, NOP, NOP));
    public static final Module COORDS = reg(new Module("Coordinates", "XYZ position", Category.VISUAL, true, NOP, NOP));
    public static final Module COMPASS = reg(new Module("Marks HUD", "Compass + list of your marks", Category.VISUAL, true, NOP, NOP));

    public static final Module RECORD = reg(new Module("Record", "Record first-person view  [R]", Category.REPLAY, false,
            Replay::startRecording, Replay::stopRecording));

    public static final Module BOOST = reg(new Module("FPS Boost", "Lower settings for more FPS", Category.BOOST, false,
            Boost::apply, Boost::restore));

    private static Module reg(Module m) {
        BY_CAT.computeIfAbsent(m.cat, c -> new ArrayList<>()).add(m);
        return m;
    }

    public static List<Module> of(Category c) { return BY_CAT.getOrDefault(c, List.of()); }
    private ModuleManager() {}
}
