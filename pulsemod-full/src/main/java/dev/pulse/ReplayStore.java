package dev.pulse;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/** The newest saved replays (only headers are read). */
public final class ReplayStore {
    public record Entry(Path path, String label) {}
    public static final List<Entry> ENTRIES = new ArrayList<>();

    public static void refresh() {
        ENTRIES.clear();
        Path d = Replay.dir();
        if (!Files.isDirectory(d)) return;
        try (Stream<Path> s = Files.list(d)) {
            List<Path> files = s.filter(f -> f.toString().endsWith(".pulse"))
                    .sorted(Comparator.reverseOrder()).limit(4).toList();
            for (Path p : files) {
                int n = frames(p);
                if (n > 0) ENTRIES.add(new Entry(p, label(p, n)));
            }
        } catch (IOException ignored) {}
    }

    private static int frames(Path p) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
            if (in.readInt() != Replay.MAGIC) return -1;
            return in.readInt();
        } catch (IOException e) { return -1; }
    }

    private static String label(Path p, int n) {
        String name = p.getFileName().toString();
        try {
            long ts = Long.parseLong(name.substring(7, name.length() - 6));
            return new SimpleDateFormat("dd.MM HH:mm").format(new Date(ts)) + "  " + (n / 20) + "s";
        } catch (RuntimeException e) {
            return name;
        }
    }

    private ReplayStore() {}
}
