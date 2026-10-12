package com.magicbanana666.hats.common.hat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The hats a player has collected (count per hat, unlocked accessories, colours). Stored on the player.
 */
public final class PlayerHats
{
    public final List<HatPart> hatParts = new ArrayList<>();

    @Nullable
    public HatPart get(String name)
    {
        for(HatPart part : hatParts)
        {
            if(part.name.equals(name))
            {
                return part;
            }
        }
        return null;
    }

    public CompoundTag write(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for(HatPart part : hatParts)
        {
            list.add(part.write(new CompoundTag()));
        }
        tag.put("hats", list);
        return tag;
    }

    public void read(CompoundTag tag)
    {
        hatParts.clear();
        for(Tag t : tag.getListOrEmpty("hats"))
        {
            if(t instanceof CompoundTag c)
            {
                HatPart part = HatPart.fromTag(c);
                if(!part.name.isEmpty())
                {
                    hatParts.add(part);
                }
            }
        }
    }
}
