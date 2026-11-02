package com.rmenu;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Запись «от первого лица»: на каждый тик сохраняется поза глаз игрока (то, что видит камера),
 * а также приседание / бег / взмах руки / предмет в руке — этого хватает, чтобы потом показать
 * сцену и от 1-го, и от 3-го лица. Всё в примитивных массивах, на диск — дельты + GZIP.
 */
public final class Recording {
    public static final int F_SNEAK = 1, F_SPRINT = 2, F_SWING = 4;

    private static final int MAGIC = 0x524D5250; // "RMRP"
    private static final int VERSION = 2;
    private static final double SCALE = 256.0;
    private static final int MAX_FRAMES = 20 * 60 * 60 * 2; // 2 часа

    public int size;
    public int viewMode; // 0 = 1-е лицо, 1 = 3-е лицо (для экспортированных файлов)
    private double[] x, y, z;
    private float[] yaw, pitch, eye, body;
    private byte[] flags;
    private int[] item;
    public final List<Integer> markFrames = new ArrayList<>();
    public final List<String> markNames = new ArrayList<>();

    public Recording(int cap) {
        x = new double[cap]; y = new double[cap]; z = new double[cap];
        yaw = new float[cap]; pitch = new float[cap]; eye = new float[cap]; body = new float[cap];
        flags = new byte[cap]; item = new int[cap];
    }

    public boolean full() {
        return size >= MAX_FRAMES;
    }

    public int flag(int i) { return flags[i]; }

    public int item(int i) { return item[i]; }

    public void addMarker(int frame, String name) {
        markFrames.add(frame);
        markNames.add(name);
    }

    public void add(double px, double py, double pz, float yw, float pt, float eyeH, float bodyYaw, int fl, int itemId) {
        if (size == x.length) {
            int n = size * 2;
            x = Arrays.copyOf(x, n); y = Arrays.copyOf(y, n); z = Arrays.copyOf(z, n);
            yaw = Arrays.copyOf(yaw, n); pitch = Arrays.copyOf(pitch, n);
            eye = Arrays.copyOf(eye, n); body = Arrays.copyOf(body, n);
            flags = Arrays.copyOf(flags, n); item = Arrays.copyOf(item, n);
        }
        int i = size++;
        x[i] = px; y[i] = py; z[i] = pz;
        yaw[i] = yw; pitch[i] = pt; eye[i] = eyeH; body[i] = bodyYaw;
        flags[i] = (byte) fl; item[i] = itemId;
    }

    /** Кадр в момент t (тики) -> o[0..6] = x,y,z,yaw,pitch,eye,body. Без аллокаций. */
    public void sample(double t, double[] o) {
        int last = size - 1;
        if (t < 0) t = 0;
        if (t > last) t = last;
        int i = (int) t;
        int j = Math.min(i + 1, last);
        double f = t - i;
        o[0] = x[i] + (x[j] - x[i]) * f;
        o[1] = y[i] + (y[j] - y[i]) * f;
        o[2] = z[i] + (z[j] - z[i]) * f;
        o[3] = yaw[i] + (yaw[j] - yaw[i]) * f;
        o[4] = pitch[i] + (pitch[j] - pitch[i]) * f;
        o[5] = eye[i] + (eye[j] - eye[i]) * f;
        o[6] = body[i] + (body[j] - body[i]) * f;
    }

    public void write(Path p) throws IOException {
        Files.createDirectories(p.getParent());
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(tmp), 1 << 16), 1 << 16))) {
            o.writeInt(MAGIC);
            o.writeByte(VERSION);
            o.writeInt(size);
            o.writeByte(viewMode);
            o.writeInt(markFrames.size());
            for (int i = 0; i < markFrames.size(); i++) {
                o.writeInt(markFrames.get(i));
                o.writeUTF(markNames.get(i));
            }
            long px = 0, py = 0, pz = 0;
            for (int i = 0; i < size; i++) {
                long fx = Math.round(x[i] * SCALE), fy = Math.round(y[i] * SCALE), fz = Math.round(z[i] * SCALE);
                long dx = fx - px, dy = fy - py, dz = fz - pz;
                boolean key = i == 0 || (int) dx != dx || (int) dy != dy || (int) dz != dz;
                o.writeByte(key ? 1 : 0);
                if (key) { o.writeLong(fx); o.writeLong(fy); o.writeLong(fz); }
                else { o.writeInt((int) dx); o.writeInt((int) dy); o.writeInt((int) dz); }
                px = fx; py = fy; pz = fz;
                o.writeFloat(yaw[i]); o.writeFloat(pitch[i]); o.writeFloat(eye[i]); o.writeFloat(body[i]);
                o.writeByte(flags[i]); o.writeInt(item[i]);
            }
        }
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Recording read(Path p) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(p), 1 << 16), 1 << 16))) {
            if (in.readInt() != MAGIC) throw new IOException("Неверный формат файла");
            int ver = in.readByte();
            if (ver != VERSION) throw new IOException("Неподдерживаемая версия: " + ver);
            int n = in.readInt();
            if (n < 0 || n > MAX_FRAMES) throw new IOException("Некорректный размер записи");
            Recording r = new Recording(Math.max(n, 2));
            r.viewMode = in.readByte() == 1 ? 1 : 0;
            int marks = in.readInt();
            if (marks < 0 || marks > 10000) throw new IOException("Некорректные метки");
            for (int i = 0; i < marks; i++) {
                int f = in.readInt();
                r.addMarker(Math.max(0, Math.min(n - 1, f)), in.readUTF());
            }
            long ax = 0, ay = 0, az = 0;
            for (int i = 0; i < n; i++) {
                if (in.readByte() == 1) { ax = in.readLong(); ay = in.readLong(); az = in.readLong(); }
                else { ax += in.readInt(); ay += in.readInt(); az += in.readInt(); }
                r.add(ax / SCALE, ay / SCALE, az / SCALE,
                        in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                        in.readByte(), in.readInt());
            }
            return r;
        }
    }
}
