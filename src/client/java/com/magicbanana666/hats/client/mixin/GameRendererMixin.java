package com.magicbanana666.hats.client.mixin;

import com.magicbanana666.hats.client.ClientEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Start of a frame (NeoForge's RenderFrameEvent.Pre): resets the per frame hat render count.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin
{
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void hats$startFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci)
    {
        ClientEvents.onRenderFrame();
    }
}
