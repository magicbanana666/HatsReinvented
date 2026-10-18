package com.magicbanana666.hats.client;

import com.magicbanana666.hats.HatsReinvented;
import com.magicbanana666.hats.common.HatsAttachments;
import com.magicbanana666.hats.common.HatsConfig;
import com.magicbanana666.hats.common.hat.HatHandler;
import com.magicbanana666.hats.common.hat.HatPart;
import com.magicbanana666.hats.common.network.NewHatPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

public final class HatsClient implements ClientModInitializer
{
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(HatsReinvented.id("hats"));
    public static final KeyMapping KEY_HATS = new KeyMapping("hats.key.hatsMenu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    /** While the hats menu is open, the local player shows this hat instead of the worn one. */
    @Nullable
    public static HatPart previewHat;
    @Nullable
    public static Entity previewEntity;

    /** Morphing Time's PlayerShape.getCurrentShape(Player), when it is installed (a morph shows the hat of its player). */
    @Nullable
    private static Method morphGetShape;

    @Override
    public void onInitializeClient()
    {
        HatsConfig.loadClient();
        KeyMappingHelper.registerKeyMapping(KEY_HATS);
        ClientEvents.register();
        //Fabric calls this for every living entity renderer, players and mannequins (all skins) included
        LivingEntityRenderLayerRegistrationCallback.EVENT.register(HatsClient::addLayer);
        ClientPlayNetworking.registerGlobalReceiver(NewHatPayload.TYPE, (payload, context) -> onNewHat(payload));
        //the hat textures are uploaded by us, free them when the resources (and textures) are reloaded
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(HatsReinvented.id("hat_textures"), (ResourceManagerReloadListener)resourceManager -> HatRenderer.clear());

        if(FabricLoader.getInstance().isModLoaded("morphingtime"))
        {
            try
            {
                morphGetShape = Class.forName("com.magicbanana666.morphingtime.shape.api.PlayerShape").getMethod("getCurrentShape", Player.class);
            }
            catch(ReflectiveOperationException e)
            {
                HatsReinvented.LOGGER.warn("Could not find Morphing Time's shapes, morphs won't show the hat of their player: {}", e.toString());
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayer(EntityType<? extends LivingEntity> entityType, LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderLayerRegistrationCallback.RegistrationHelper helper, EntityRendererProvider.Context context)
    {
        helper.register(new HatLayer(renderer));
    }

    /** The hat to show on an entity (a morph shows the hat of the player wearing it). */
    @Nullable
    public static HatPart hatFor(Entity entity)
    {
        if(previewEntity != null && entity == previewEntity)
        {
            return previewHat;
        }
        if(morphGetShape != null && !(entity instanceof Player) && Minecraft.getInstance().level != null)
        {
            Player player = morphPlayer(entity);
            if(player != null)
            {
                return hatFor(player);
            }
        }
        return entity.getAttached(HatsAttachments.HAT);
    }

    @Nullable
    private static Player morphPlayer(Entity entity)
    {
        try
        {
            for(Player player : Minecraft.getInstance().level.players())
            {
                if(morphGetShape.invoke(null, player) == entity)
                {
                    return player;
                }
            }
        }
        catch(ReflectiveOperationException | RuntimeException e)
        {
            HatsReinvented.LOGGER.warn("Could not get the morph of a player, morphs won't show the hat of their player: {}", e.toString());
            morphGetShape = null;
        }
        return null;
    }

    /** Called by the render state mixin for every living entity (players and mannequins included). */
    public static void extractHat(LivingEntity entity, LivingEntityRenderState state)
    {
        ((FabricRenderState)state).setData(HatLayer.HAT_KEY, hatFor(entity));
    }

    private static void onNewHat(NewHatPayload payload)
    {
        Minecraft mc = Minecraft.getInstance();
        MutableComponent title = Component.translatable("hats.toast.unlock.template" + (1 + mc.level.getRandom().nextInt(5)), Component.translatable(payload.isNewHat() ? "hats.toast.unlock.hat" : "hats.toast.unlock.accessory"));
        MutableComponent subtitle = HatHandler.describe(payload.hat()).copy();
        if(!payload.newAccessories().isEmpty())
        {
            subtitle.append(Component.literal(" + " + String.join(", ", payload.newAccessories())));
        }
        SystemToast.add(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, subtitle);
    }
}
