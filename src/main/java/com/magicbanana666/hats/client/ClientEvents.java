package com.magicbanana666.hats.client;

import com.magicbanana666.hats.HatsReinvented;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = HatsReinvented.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents
{
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        while(HatsClient.KEY_HATS.consumeClick())
        {
            if(mc.player != null && mc.gui.screen() == null)
            {
                mc.gui.setScreen(new HatsScreen(mc.player));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event)
    {
        HatLayer.renderCount = 0;
    }
}
