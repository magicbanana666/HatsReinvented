package com.magicbanana666.hats.client.mixin;

import com.magicbanana666.hats.client.HatsClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the hat to show into the render state of every living entity (players and mannequins call this through super).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin
{
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void hats$extractHat(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci)
    {
        HatsClient.extractHat(entity, state);
    }
}
