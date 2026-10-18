package com.magicbanana666.hats.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class ClientEvents
{
    private ClientEvents() {}

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(ClientEvents::onClientTick);
    }

    private static void onClientTick(Minecraft mc)
    {
        while(HatsClient.KEY_HATS.consumeClick())
        {
            if(mc.player != null && mc.screen == null)
            {
                mc.setScreen(new HatsScreen(mc.player));
            }
        }
    }

    /** Called by the GameRenderer mixin at the start of every frame. */
    public static void onRenderFrame()
    {
        HatLayer.renderCount = 0;
    }
}
