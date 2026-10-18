package com.magicbanana666.hats.client;

import com.magicbanana666.hats.HatsReinvented;
import com.magicbanana666.hats.common.HatsAttachments;
import com.magicbanana666.hats.common.hat.HatHandler;
import com.magicbanana666.hats.common.hat.HatPart;
import com.magicbanana666.hats.common.network.NewHatPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

@Mod(value = HatsReinvented.MOD_ID, dist = Dist.CLIENT)
public final class HatsClient
{
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(HatsReinvented.id("hats"));
    public static final KeyMapping KEY_HATS = new KeyMapping("hats.key.hatsMenu", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    /** While the hats menu is open, the local player shows this hat instead of the worn one. */
    @Nullable
    public static HatPart previewHat;
    @Nullable
    public static Entity previewEntity;

    private static final String MORPH_PLAYER_TAG = "morphreinvented:player";

    public HatsClient(IEventBus modBus, ModContainer container)
    {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(HatsClient::registerKeys);
        modBus.addListener(HatsClient::addLayers);
        modBus.addListener(HatsClient::registerRenderStateModifiers);
        modBus.addListener(HatsClient::registerPayloadHandlers);
        modBus.addListener(HatsClient::addReloadListeners);
    }

    private static void addReloadListeners(AddClientReloadListenersEvent event)
    {
        //the hat textures are uploaded by us, free them when the resources (and textures) are reloaded
        event.addListener(HatsReinvented.id("hat_textures"), (ResourceManagerReloadListener)resourceManager -> HatRenderer.clear());
    }

    private static void registerKeys(RegisterKeyMappingsEvent event)
    {
        event.registerCategory(CATEGORY);
        event.register(KEY_HATS);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayers(EntityRenderersEvent.AddLayers event)
    {
        for(EntityType<?> type : event.getEntityTypes())
        {
            EntityRenderer<?, ?> renderer = event.getRenderer(type);
            if(renderer instanceof LivingEntityRenderer living)
            {
                living.addLayer(new HatLayer(living));
            }
        }
        for(PlayerModelType skin : event.getSkins())
        {
            AvatarRenderer player = event.getPlayerRenderer(skin);
            if(player != null)
            {
                player.addLayer(new HatLayer(player));
            }
            AvatarRenderer mannequin = event.getMannequinRenderer(skin);
            if(mannequin != null)
            {
                mannequin.addLayer(new HatLayer(mannequin));
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event)
    {
        event.registerEntityModifier((Class)LivingEntityRenderer.class, (entity, state) -> {
            if(state instanceof LivingEntityRenderState living)
            {
                living.setRenderData(HatLayer.HAT_KEY, hatFor((Entity)entity));
            }
        });
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier()
        {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState renderState)
            {
                renderState.setRenderData(HatLayer.HAT_KEY, hatFor(avatar));
            }
        });
    }

    /** The hat to show on an entity (a morph shows the hat of the player wearing it). */
    @Nullable
    public static HatPart hatFor(Entity entity)
    {
        if(previewEntity != null && entity == previewEntity)
        {
            return previewHat;
        }
        if(entity.getPersistentData().contains(MORPH_PLAYER_TAG) && Minecraft.getInstance().level != null)
        {
            try
            {
                Player player = Minecraft.getInstance().level.getPlayerByUUID(UUID.fromString(entity.getPersistentData().getStringOr(MORPH_PLAYER_TAG, "")));
                if(player != null)
                {
                    return hatFor(player);
                }
            }
            catch(IllegalArgumentException ignored) {}
        }
        return entity.getExistingDataOrNull(HatsAttachments.HAT);
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event)
    {
        event.register(NewHatPayload.TYPE, HatsClient::onNewHat);
    }

    private static void onNewHat(NewHatPayload payload, IPayloadContext context)
    {
        Minecraft mc = Minecraft.getInstance();
        MutableComponent title = Component.translatable("hats.toast.unlock.template" + (1 + mc.level.getRandom().nextInt(5)), Component.translatable(payload.isNewHat() ? "hats.toast.unlock.hat" : "hats.toast.unlock.accessory"));
        MutableComponent subtitle = HatHandler.describe(payload.hat()).copy();
        if(!payload.newAccessories().isEmpty())
        {
            subtitle.append(Component.literal(" + " + String.join(", ", payload.newAccessories())));
        }
        SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, subtitle);
    }
}
