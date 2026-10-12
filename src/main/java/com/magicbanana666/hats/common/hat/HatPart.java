package com.magicbanana666.hats.common.hat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A hat (or accessory) as worn by an entity or owned by a player: its name, how many the player has, which accessories
 * are attached and how it is coloured. Ported from Hats 1.16's HatsSavedData.HatPart.
 */
public final class HatPart implements Comparable<HatPart>
{
    public static final StreamCodec<RegistryFriendlyByteBuf, HatPart> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(HatPart::fromTag, p -> p.write(new CompoundTag())).cast();

    public String name = "";
    /** -1: never randomly assigned (mobs), 0: not owned (players), >0 owned count */
    public int count = -1;
    public boolean isFavourite;
    public boolean isNew;
    public boolean isShowing;
    /** 0 0 0 0 = no change, up to 1 1 1 1 for black and invisible (inverted RGBA multiplier) */
    public float[] colouriser = new float[] {0F, 0F, 0F, 0F};
    /** 0 0 0 = no change, up to 1 1 1 (hue shift, desaturate, darken) */
    public float[] hsbiser = new float[] {0F, 0F, 0F};
    public boolean enchanted;
    public final List<HatPart> hatParts = new ArrayList<>();

    public HatPart() {}

    public HatPart(String name)
    {
        this.name = name;
        this.count = 1;
    }

    public HatPart setNew()
    {
        isNew = true;
        return this;
    }

    public void copy(HatPart part)
    {
        name = part.name;
        count = part.count;
        isFavourite = part.isFavourite;
        isShowing = part.isShowing;
        isNew = part.isNew;
        colouriser = part.colouriser.clone();
        hsbiser = part.hsbiser.clone();
        enchanted = part.enchanted;
        hatParts.clear();
        for(HatPart hatPart : part.hatParts)
        {
            hatParts.add(hatPart.createCopy());
        }
    }

    public HatPart createCopy()
    {
        HatPart part = new HatPart();
        part.copy(this);
        return part;
    }

    public HatPart createRandom(Random random)
    {
        HatPart part = createCopy();
        part.randomize(random);
        return part;
    }

    @Nullable
    public HatPart get(String partName)
    {
        if(partName.equals(name))
        {
            return this;
        }
        for(HatPart hatPart : hatParts)
        {
            HatPart found = hatPart.get(partName);
            if(found != null)
            {
                return found;
            }
        }
        return null;
    }

    public void randomize(Random random)
    {
        this.count = 1;
        this.isShowing = true;
        for(int i = hatParts.size() - 1; i >= 0; i--)
        {
            if(random.nextBoolean())
            {
                hatParts.remove(i);
            }
        }
        for(HatPart hatPart : hatParts)
        {
            hatPart.randomize(random);
        }
    }

    public boolean isAHat()
    {
        return !name.isEmpty() && count >= 0;
    }

    public boolean hasNew()
    {
        if(isNew)
        {
            return true;
        }
        for(HatPart hatPart : hatParts)
        {
            if(hatPart.hasNew())
            {
                return true;
            }
        }
        return false;
    }

    public HatPart setNoNew()
    {
        isNew = false;
        for(HatPart hatPart : hatParts)
        {
            hatPart.setNoNew();
        }
        return this;
    }

    /** Adds the counts / accessories of the other part (same hat) to this one. */
    public boolean add(HatPart part)
    {
        if(name.isEmpty() || !name.equals(part.name))
        {
            return false;
        }
        count = Math.max(0, Math.min(999_999_999, count + part.count));
        List<HatPart> remaining = new ArrayList<>(part.hatParts);
        for(HatPart hatPart : hatParts)
        {
            for(int i = remaining.size() - 1; i >= 0; i--)
            {
                if(hatPart.add(remaining.get(i)))
                {
                    remaining.remove(i);
                    break;
                }
            }
        }
        for(HatPart newPart : remaining)
        {
            if(newPart.count > 0)
            {
                newPart.setNew();
            }
            hatParts.add(newPart);
        }
        return true;
    }

    public void hideAll()
    {
        isShowing = false;
        for(HatPart hatPart : hatParts)
        {
            hatPart.hideAll();
        }
    }

    /** Copies colours / showing state from a part with the same name (recursively for accessories). */
    public boolean copyPersonalisation(HatPart part)
    {
        if(!name.equals(part.name))
        {
            return false;
        }
        isFavourite = part.isFavourite;
        isShowing = part.isShowing;
        colouriser = part.colouriser.clone();
        hsbiser = part.hsbiser.clone();
        enchanted = part.enchanted;
        for(HatPart hatPart : hatParts)
        {
            for(HatPart other : part.hatParts)
            {
                hatPart.copyPersonalisation(other);
            }
        }
        return true;
    }

    public boolean has(String partName)
    {
        if(name.toLowerCase(Locale.ROOT).contains(partName))
        {
            return true;
        }
        for(HatPart accessory : hatParts)
        {
            if(accessory.has(partName))
            {
                return true;
            }
        }
        return false;
    }

    public int accessoriesUnlocked()
    {
        int total = 0;
        for(HatPart hatPart : hatParts)
        {
            if(hatPart.count > 0)
            {
                total++;
            }
            total += hatPart.accessoriesUnlocked();
        }
        return total;
    }

    public boolean isColourised()
    {
        for(float v : colouriser)
        {
            if(v != 0F)
            {
                return true;
            }
        }
        for(float v : hsbiser)
        {
            if(v != 0F)
            {
                return true;
            }
        }
        return enchanted;
    }

    /** A cache key for how this hat looks (name, accessories shown, colours). */
    public String appearanceKey()
    {
        StringBuilder sb = new StringBuilder(name);
        sb.append('[');
        for(float v : hsbiser)
        {
            sb.append(v).append(',');
        }
        sb.append(']');
        for(HatPart part : hatParts)
        {
            if(part.isShowing)
            {
                sb.append('{').append(part.appearanceKey()).append('}');
            }
        }
        return sb.toString();
    }

    public CompoundTag write(CompoundTag tag)
    {
        tag.putString("name", name);
        tag.putInt("count", count);
        tag.putBoolean("isFavourite", isFavourite);
        tag.putBoolean("isNew", isNew);
        tag.putBoolean("isShowing", isShowing);
        for(int i = 0; i < colouriser.length; i++)
        {
            tag.putFloat("colouriser_" + i, colouriser[i]);
        }
        for(int i = 0; i < hsbiser.length; i++)
        {
            tag.putFloat("hsbiser_" + i, hsbiser[i]);
        }
        tag.putBoolean("enchanted", enchanted);
        ListTag list = new ListTag();
        for(HatPart part : hatParts)
        {
            list.add(part.write(new CompoundTag()));
        }
        tag.put("hatParts", list);
        return tag;
    }

    public void read(CompoundTag tag)
    {
        name = tag.getStringOr("name", "");
        count = tag.getIntOr("count", -1);
        isFavourite = tag.getBooleanOr("isFavourite", false);
        isNew = tag.getBooleanOr("isNew", false);
        isShowing = tag.getBooleanOr("isShowing", false);
        for(int i = 0; i < colouriser.length; i++)
        {
            colouriser[i] = tag.getFloatOr("colouriser_" + i, 0F);
        }
        for(int i = 0; i < hsbiser.length; i++)
        {
            hsbiser[i] = tag.getFloatOr("hsbiser_" + i, 0F);
        }
        enchanted = tag.getBooleanOr("enchanted", false);
        hatParts.clear();
        for(Tag t : tag.getListOrEmpty("hatParts"))
        {
            if(t instanceof CompoundTag c)
            {
                hatParts.add(fromTag(c));
            }
        }
    }

    public static HatPart fromTag(CompoundTag tag)
    {
        HatPart part = new HatPart();
        part.read(tag);
        return part;
    }

    @Override
    public int compareTo(HatPart o)
    {
        return name.compareTo(o.name);
    }

    @Override
    public String toString()
    {
        return name + "x" + count + (hatParts.isEmpty() ? "" : hatParts.toString());
    }
}
