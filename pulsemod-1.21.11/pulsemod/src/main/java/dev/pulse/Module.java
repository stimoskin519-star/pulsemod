package dev.pulse;

public final class Module {
    public final String name, desc;
    public final Category cat;
    public boolean enabled;
    public float anim; // 0..1 for the switch animation
    private final Runnable onEnable, onDisable;

    public Module(String name, String desc, Category cat, boolean enabled, Runnable on, Runnable off) {
        this.name = name; this.desc = desc; this.cat = cat;
        this.enabled = enabled; this.anim = enabled ? 1f : 0f;
        this.onEnable = on; this.onDisable = off;
    }

    public void setEnabled(boolean v) {
        if (v == enabled) return;
        enabled = v;
        (v ? onEnable : onDisable).run();
    }

    public void toggle() { setEnabled(!enabled); }
}
