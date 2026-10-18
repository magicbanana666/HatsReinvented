package com.magicbanana666.hats;

import com.magicbanana666.hats.common.CommonEvents;
import com.magicbanana666.hats.common.HatsAttachments;
import com.magicbanana666.hats.common.HatsRegistry;
import com.magicbanana666.hats.common.hat.HatResources;
import com.magicbanana666.hats.common.network.NewHatPayload;
import com.magicbanana666.hats.common.network.SetHatPayload;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/**
 * Hats Reinvented: mobs spawn wearing hats, kill them to collect the hats, wear and customise them.
 */
public final class HatsReinvented implements ModInitializer
{
    public static final String MOD_ID = "hatsreinvented";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize()
    {
        HatsAttachments.init();
        HatsRegistry.register();
        registerPayloads();
        CommonEvents.register();

        HatResources.extractDefaults(false);
        HatResources.loadAllHats();
    }

    private static void registerPayloads()
    {
        PayloadTypeRegistry.serverboundPlay().register(SetHatPayload.TYPE, SetHatPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NewHatPayload.TYPE, NewHatPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetHatPayload.TYPE, (payload, context) -> SetHatPayload.handle(payload, context.player()));
    }

    public static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
