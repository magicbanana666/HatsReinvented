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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Draws hats: bakes the Tabula projects into models (per set of hidden parts) and uploads their textures
 * (per hue / saturation / brightness change). Colour changes are rounded to steps and only the most recently used
 * textures are kept, so dragging the colour sliders doesn't upload a texture for every value.
 */
public final class HatRenderer
{
    /** Hue / saturation / brightness are rounded to 1 / COLOUR_STEPS. */
    private static final int COLOUR_STEPS = 64;
    private static final int MAX_TEXTURES = 256;

    private static final Map<HatInfo, Cache> CACHES = new HashMap<>();
    /** All uploaded textures, least recently used first; the eldest is released when there are too many. */
    private static final LinkedHashMap<TextureKey, Optional<Identifier>> TEXTURES = new LinkedHashMap<>(16, 0.75F, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TextureKey, Optional<Identifier>> eldest)
        {
            if(size() > MAX_TEXTURES)
            {
                eldest.getValue().ifPresent(HatRenderer::releaseTexture);
                return true;
            }
            return false;
        }
    };
    /** The hats the caches were made for; reloading the hats replaces the map, then the caches are dropped. */
    @Nullable
    private static Map<String, HatInfo> cachedHats;
    private static int nextTextureId;

    private HatRenderer() {}

    private static final class Cache
    {
        final HatInfo info;
        final Map<Set<String>, BakedTabulaModel> models = new HashMap<>();

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
            TextureKey key = new TextureKey(info, quantise(hsbiser[0]), quantise(hsbiser[1]), quantise(hsbiser[2]));
            Optional<Identifier> existing = TEXTURES.get(key);
            if(existing != null)
            {
                return existing.orElse(null);
            }
            Identifier id = null;
            byte[] bytes = info.project.textureBytes;
            if(bytes != null)
            {
                try
                {
                    NativeImage image = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(bytes));
                    if(key.hue() != 0 || key.saturation() != 0 || key.brightness() != 0)
                    {
                        applyHsb(image, new float[] {key.hue() / (float)COLOUR_STEPS, key.saturation() / (float)COLOUR_STEPS, key.brightness() / (float)COLOUR_STEPS});
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
            TEXTURES.put(key, Optional.ofNullable(id));
            return id;
        }
    }

    /** A texture of a hat with its (rounded) hue / saturation / brightness change. */
    private record TextureKey(HatInfo info, int hue, int saturation, int brightness) {}

    private static int quantise(float value)
    {
        return Float.isFinite(value) ? Math.round(Math.clamp(value, 0F, 1F) * COLOUR_STEPS) : 0;
    }

    private static void releaseTexture(Identifier id)
    {
        Minecraft.getInstance().getTextureManager().release(id);
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

    /** Frees all textures and models (on a resource reload, or after the hats were reloaded). */
    public static void clear()
    {
        for(Optional<Identifier> id : TEXTURES.values())
        {
            id.ifPresent(HatRenderer::releaseTexture);
        }
        TEXTURES.clear();
        CACHES.clear();
    }

    /** Renders a hat with its showing accessories. The pose is the hat origin (top centre of a player sized head). */
    public static boolean render(HatPart part, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outlineColor)
    {
        Map<String, HatInfo> hats = HatResources.HATS;
        if(hats != cachedHats)
        {
            //the hats were reloaded, the old ones won't be drawn again
            clear();
            cachedHats = hats;
        }
        HatInfo info = hats.get(part.name);
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
