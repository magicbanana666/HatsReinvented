package com.magicbanana666.hats;

import com.magicbanana666.hats.common.HatsAttachments;
import com.magicbanana666.hats.common.HatsConfig;
import com.magicbanana666.hats.common.HatsRegistry;
import com.magicbanana666.hats.common.hat.HatResources;
import com.magicbanana666.hats.common.network.NewHatPayload;
import com.magicbanana666.hats.common.network.SetHatPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * Hats Reinvented: mobs spawn wearing hats, kill them to collect the hats, wear and customise them.
 */
@Mod(HatsReinvented.MOD_ID)
public final class HatsReinvented
{
    public static final String MOD_ID = "hatsreinvented";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HatsReinvented(IEventBus modBus, ModContainer container)
    {
        HatsAttachments.REGISTER.register(modBus);
        HatsRegistry.register(modBus);
        modBus.addListener(HatsReinvented::registerPayloads);
        modBus.addListener(HatsReinvented::onConfigLoad);

        container.registerConfig(ModConfig.Type.SYNCED, HatsConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, HatsConfig.CLIENT_SPEC);

        HatResources.extractDefaults(false);
        HatResources.loadAllHats();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetHatPayload.TYPE, SetHatPayload.STREAM_CODEC, SetHatPayload::handle);
        registrar.playToClient(NewHatPayload.TYPE, NewHatPayload.STREAM_CODEC);
    }

    private static void onConfigLoad(ModConfigEvent event)
    {
        if(event.getConfig().getSpec() == HatsConfig.SERVER_SPEC)
        {
            //rarities depend on the seed and weights
            com.magicbanana666.hats.common.hat.HatHandler.allocateHatPools();
        }
    }

    public static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
