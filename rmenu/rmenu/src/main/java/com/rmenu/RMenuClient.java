package com.rmenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RMenuClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("rmenu");

    private static KeyBinding menuKey, recKey, pauseKey, viewKey, markKey;

    private static KeyBinding key(String id, int code, KeyBinding.Category cat) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, code, cat));
    }

    @Override
    public void onInitializeClient() {
        Settings.load();
        Marks.load();
        ReplayPlayer.refreshFiles();

        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("rmenu", "main"));
        menuKey = key("key.rmenu.menu", GLFW.GLFW_KEY_RIGHT_SHIFT, cat);
        recKey = key("key.rmenu.record", GLFW.GLFW_KEY_F8, cat);
        pauseKey = key("key.rmenu.pause", GLFW.GLFW_KEY_F9, cat);
        viewKey = key("key.rmenu.view", GLFW.GLFW_KEY_F7, cat);
        markKey = key("key.rmenu.mark", GLFW.GLFW_KEY_B, cat);

        ClientTickEvents.START_CLIENT_TICK.register(ReplayPlayer::freezeInput);

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (menuKey.wasPressed()) if (c.currentScreen == null) c.setScreen(new MenuScreen());
            while (recKey.wasPressed()) Recorder.toggle();
            while (pauseKey.wasPressed()) ReplayPlayer.togglePause();
            while (viewKey.wasPressed()) ReplayPlayer.toggleView();
            while (markKey.wasPressed()) if (c.currentScreen == null) Marks.addHere(c);
            Marks.tick(c);
            Recorder.tick(c);
            ReplayPlayer.tick(c);
            Perf.tick(c);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Recorder.stop();
            ReplayPlayer.stop();
        });

        HudRenderCallback.EVENT.register(RHud::render);
    }
}
