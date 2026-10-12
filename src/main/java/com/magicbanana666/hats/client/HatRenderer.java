package com.magicbanana666.hats.client;

import com.magicbanana666.bananautil.client.model.BakedTabulaModel;
import com.magicbanana666.hats.HatsReinvented;
import com.magicbanana666.hats.common.hat.HatInfo;
import com.magicbanana666.hats.common.hat.HatPart;
import com.magicbanana666.hats.common.hat.HatResources;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws hats: bakes the Tabula projects into models (per set of hidden parts) and uploads their textures
 * (per hue / saturation / brightness change).
 */
public final class HatRenderer
{
    private static final Map<HatInfo, Cache> CACHES = new HashMap<>();
    private static int nextTextureId;

    private HatRenderer() {}

    private static final class Cache
    {
        final HatInfo info;
        final Map<Set<String>, BakedTabulaModel> models = new HashMap<>();
        final Map<String, Identifier> textures = new HashMap<>();

        Cache(HatInfo info)
        {
            this.info = info;
        }

        BakedTabulaModel model(Set<String> hidden)
        {
            return models.computeIfAbsent(hidden, h -> BakedTabulaModel.bake(info.project, h));
        }

        @Nullable
        Identifier texture(float[] hsbiser)
        {
            String key = hsbiser[0] + "H" + hsbiser[1] + "S" + hsbiser[2] + "B";
            Identifier existing = textures.get(key);
            if(existing != null || textures.containsKey(key))
            {
                return existing;
            }
            Identifier id = null;
            byte[] bytes = info.project.textureBytes;
            if(bytes != null)
            {
                try
                {
                    NativeImage image = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(bytes));
                    if(hsbiser[0] != 0F || hsbiser[1] != 0F || hsbiser[2] != 0F)
                    {
                        applyHsb(image, hsbiser);
                    }
                    id = HatsReinvented.id("hat/" + (nextTextureId++));
                    String label = "hat " + info.name;
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> label, image));
                }
                catch(Exception e)
                {
                    HatsReinvented.LOGGER.warn("Could not load the texture of hat {}: {}", info.name, e.toString());
                }
            }
            textures.put(key, id);
            return id;
        }

        void release()
        {
            for(Identifier id : textures.values())
            {
                if(id != null)
                {
                    Minecraft.getInstance().getTextureManager().release(id);
                }
            }
            textures.clear();
            models.clear();
        }
    }

    private static void applyHsb(NativeImage image, float[] hsbiser)
    {
        for(int x = 0; x < image.getWidth(); x++)
        {
            for(int y = 0; y < image.getHeight(); y++)
            {
                int argb = image.getPixel(x, y);
                int alpha = argb >>> 24;
                if(alpha > 0)
                {
                    float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xff, argb >> 8 & 0xff, argb & 0xff, null);
                    hsb[0] += hsbiser[0];
                    hsb[1] *= 1F - hsbiser[1];
                    hsb[2] *= 1F - hsbiser[2];
                    image.setPixel(x, y, (alpha << 24) | (Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xffffff));
                }
            }
        }
    }

    private static Cache cache(HatInfo info)
    {
        return CACHES.computeIfAbsent(info, Cache::new);
    }

    /** Frees all textures (after reloading the hats). */
    public static void clear()
    {
        for(Cache cache : CACHES.values())
        {
            cache.release();
        }
        CACHES.clear();
    }

    /** Renders a hat with its showing accessories. The pose is the hat origin (top centre of a player sized head). */
    public static boolean render(HatPart part, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outlineColor)
    {
        HatInfo info = HatResources.get(part);
        if(info == null || !part.isAHat())
        {
            return false;
        }
        renderInfo(info, part, poseStack, collector, light, overlay, outlineColor);
        return true;
    }

    private static void renderInfo(HatInfo info, HatPart part, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outlineColor)
    {
        if(!part.isShowing)
        {
            return;
        }
        List<HatInfo> showingAccessories = new ArrayList<>();
        List<HatPart> showingParts = new ArrayList<>();
        Set<String> hidden = new HashSet<>();
        for(HatPart accPart : part.hatParts)
        {
            if(!accPart.isShowing)
            {
                continue;
            }
            for(HatInfo accessory : info.accessories)
            {
                if(accessory.name.equals(accPart.name))
                {
                    showingAccessories.add(accessory);
                    showingParts.add(accPart);
                    hidden.addAll(accessory.hideParent);
                }
            }
        }

        Cache cache = cache(info);
        Identifier texture = cache.texture(part.hsbiser);
        if(texture != null)
        {
            float[] c = part.colouriser;
            int color = ARGB.colorFromFloat(1F - c[3], 1F - c[0], 1F - c[1], 1F - c[2]);
            cache.model(hidden).submit(poseStack, collector, RenderTypes.entityTranslucent(texture), light, overlay, color, part.enchanted, outlineColor);
        }

        for(int i = 0; i < showingAccessories.size(); i++)
        {
            renderInfo(showingAccessories.get(i), showingParts.get(i), poseStack, collector, light, overlay, outlineColor);
        }
    }
}
