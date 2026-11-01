package dev.pulse;

public enum Category {
    VISUAL("Visual"), REPLAY("Replay"), MARKS("Marks"), BOOST("Boost");
    public final String label;
    Category(String label) { this.label = label; }
}
