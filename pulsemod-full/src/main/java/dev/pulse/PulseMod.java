package dev.pulse;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PulseMod implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("pulsemod", "main"));

    private static KeyBinding key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, code, CATEGORY));
    }

    @Override
    public void onInitializeClient() {
        Marks.load();

        KeyBinding gui = key("key.pulse.gui", GLFW.GLFW_KEY_RIGHT_SHIFT);
        KeyBinding rec = key("key.pulse.rec", GLFW.GLFW_KEY_R);
        KeyBinding play = key("key.pulse.play", GLFW.GLFW_KEY_P);
        KeyBinding pause = key("key.pulse.pause", GLFW.GLFW_KEY_O);
        KeyBinding view = key("key.pulse.view", GLFW.GLFW_KEY_V);
        KeyBinding slower = key("key.pulse.slower", GLFW.GLFW_KEY_COMMA);
        KeyBinding faster = key("key.pulse.faster", GLFW.GLFW_KEY_PERIOD);
        KeyBinding mark = key("key.pulse.mark", GLFW.GLFW_KEY_B);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (gui.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new PulseScreen());
            while (rec.wasPressed()) ModuleManager.RECORD.toggle();
            while (play.wasPressed()) { if (Replay.playing) Replay.stopPlayback(); else Replay.playLast(false); }
            while (pause.wasPressed()) Replay.togglePause();
            while (view.wasPressed()) Replay.toggleView();
            while (slower.wasPressed()) Replay.changeSpeed(0.5f);
            while (faster.wasPressed()) Replay.changeSpeed(2f);
            while (mark.wasPressed()) Marks.add(mc);
            Replay.tick(mc);
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.of("pulsemod", "hud"), Hud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Replay.reset());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Boost.restore());
    }
}
