package com.rmenu.mixin;

import com.rmenu.ReplayPlayer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Подменяет позицию/поворот камеры во время воспроизведения реплея. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void rmenu$override(CallbackInfo ci) {
        if (!ReplayPlayer.active()) return;
        double[] s = ReplayPlayer.cameraSample();
        setRotation((float) s[3], (float) s[4]);
        setPos(s[0], s[1], s[2]);
    }
}
