package com.rmenu.mixin;

import com.rmenu.ReplayPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Во время реплея настоящий игрок невидим (вместо него — «призрак» из записи). Только на клиенте. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void rmenu$hideSelf(CallbackInfoReturnable<Boolean> cir) {
        if (ReplayPlayer.active() && (Object) this == MinecraftClient.getInstance().player) {
            cir.setReturnValue(true);
        }
    }
}
