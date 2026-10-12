package com.magicbanana666.hats.common.hat;

import com.magicbanana666.bananautil.tabula.TabulaProject;
import com.magicbanana666.hats.HatsReinvented;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * A hat (or accessory) loaded from a Tabula file, with the metadata from the project notes (rarity, pool, accessory...).
 * Ported from Hats 1.16's HatInfo; rendering lives in the client package.
 */
public final class HatInfo implements Comparable<HatInfo>
{
    public final String name;
    public final TabulaProject project;
    public final List<HatInfo> accessories = new ArrayList<>();
    public final List<String> hideParent = new ArrayList<>();

    private Rarity rarity;

    public String forcedPool;
    public Rarity forcedRarity;
    public UUID contributorUUID;
    public String description;

    public String accessoryFor;
    public final List<String> accessoryLayer = new ArrayList<>();
    public String accessoryParent;

    /** Client side render cache (BakedHat), set by the client. */
    public transient Object clientCache;

    public HatInfo(String name, TabulaProject project)
    {
        this.name = name;
        this.project = project;
        this.project.name = name;
        findMeta();
    }

    private void findMeta()
    {
        for(String note : project.notes)
        {
            if(note.startsWith("hats-rarity:"))
            {
                try
                {
                    forcedRarity = Rarity.valueOf(note.substring("hats-rarity:".length()).trim().toUpperCase(Locale.ROOT));
                }
                catch(IllegalArgumentException e)
                {
                    HatsReinvented.LOGGER.warn("Unknown hat rarity in {}: {}", name, note);
                }
            }
            else if(note.startsWith("hats-pool:"))
            {
                forcedPool = note.substring("hats-pool:".length()).trim();
            }
            else if(note.startsWith("hats-contributor-uuid:"))
            {
                try
                {
                    contributorUUID = UUID.fromString(note.substring("hats-contributor-uuid:".length()).trim());
                }
                catch(IllegalArgumentException ignored) {}
            }
            else if(note.startsWith("hats-accessory:"))
            {
                accessoryFor = note.substring("hats-accessory:".length()).trim();
            }
            else if(note.startsWith("hats-accessory-layer:"))
            {
                accessoryLayer.add(note.substring("hats-accessory-layer:".length()).trim());
            }
            else if(note.startsWith("hats-accessory-parent:"))
            {
                accessoryParent = note.substring("hats-accessory-parent:".length()).trim();
            }
            else if(note.startsWith("hats-accessory-hide-parent-part:"))
            {
                hideParent.add(note.substring("hats-accessory-hide-parent-part:".length()).trim());
            }
            else if(note.startsWith("hats-description:"))
            {
                description = note.substring("hats-description:".length()).trim();
            }
        }
    }

    public Rarity getRarity()
    {
        if(rarity == null)
        {
            if(forcedRarity != null)
            {
                rarity = forcedRarity;
            }
            else
            {
                Random random = new Random(Math.abs((HatHandler.getRandSeed() + getFullName()).hashCode()) * 154041013L);
                rarity = HatHandler.getRarityForChance(random.nextDouble());
            }
        }
        return rarity;
    }

    public void setRarity(Rarity rarity)
    {
        this.rarity = rarity;
    }

    public void resetRarity()
    {
        rarity = null;
        for(HatInfo accessory : accessories)
        {
            accessory.resetRarity();
        }
    }

    public MutableComponent getDisplayName()
    {
        return Component.literal(name).withStyle(contributorUUID != null ? net.minecraft.ChatFormatting.AQUA : getRarity().getColour());
    }

    /** Takes the accessories meant for this hat (or for one of our accessories, they pick theirs up). */
    public void accessorise(List<HatInfo> newAccessories)
    {
        for(HatInfo info : new ArrayList<>(newAccessories))
        {
            if(info.accessoryParent == null || info.accessoryParent.equals(name))
            {
                accessories.add(info);
            }
        }
        newAccessories.removeAll(accessories);
        for(HatInfo accessory : accessories)
        {
            accessory.accessorise(newAccessories);
        }
    }

    @Nullable
    public HatInfo getInfoFor(String accessoryName)
    {
        if(accessoryName.equals(name))
        {
            return this;
        }
        for(HatInfo accessory : accessories)
        {
            HatInfo found = accessory.getInfoFor(accessoryName);
            if(found != null)
            {
                return found;
            }
        }
        return null;
    }

    public String getFullName()
    {
        StringBuilder sb = new StringBuilder();
        if(accessoryFor != null)
        {
            sb.append(accessoryFor).append(':');
        }
        if(accessoryParent != null)
        {
            sb.append(accessoryParent).append('|');
        }
        sb.append(name);
        return sb.toString();
    }

    /** This hat as a part with all accessories (not showing) at the given count. */
    public HatPart getAsHatPart(int count)
    {
        HatPart part = new HatPart(name);
        part.isShowing = true;
        part.count = count;
        for(HatInfo accessory : accessories)
        {
            HatPart child = accessory.getAsHatPart(count);
            child.isShowing = false;
            part.hatParts.add(child);
        }
        return part;
    }

    /** Random accessories for a mob spawning with this hat (as in Hats 1.16). */
    public void assignAccessoriesToPart(HatPart hatPart, LivingEntity ent, boolean isBoss)
    {
        List<HatInfo> spawning = new ArrayList<>();
        Map<String, List<HatInfo>> conflicts = new HashMap<>();
        for(HatInfo accessory : accessories)
        {
            double chance = HatHandler.getRarityIndividualChance(accessory.getRarity());
            Random random = new Random(Math.abs((HatHandler.getRandSeed() + ent.getUUID() + accessory.getFullName()).hashCode()) * 53579997854L);
            if(isBoss)
            {
                chance += HatHandler.getBossRarityBonus();
            }
            if(random.nextDouble() < chance)
            {
                spawning.add(accessory);
                for(String layer : accessory.accessoryLayer)
                {
                    conflicts.computeIfAbsent(layer, k -> new ArrayList<>()).add(accessory);
                }
            }
        }
        RandomSource entRandom = ent.getRandom();
        for(List<HatInfo> conflicting : conflicts.values())
        {
            while(conflicting.size() > 1)
            {
                HatInfo loser = conflicting.get(entRandom.nextInt(conflicting.size()));
                spawning.remove(loser);
                conflicting.remove(loser);
            }
        }
        for(HatInfo accessory : spawning)
        {
            HatPart acc = new HatPart(accessory.name);
            acc.isShowing = true;
            hatPart.hatParts.add(acc);
            accessory.assignAccessoriesToPart(acc, ent, isBoss);
        }
    }

    public void collectNames(List<String> names)
    {
        names.add(name);
        for(HatInfo accessory : accessories)
        {
            accessory.collectNames(names);
        }
    }

    @Override
    public int compareTo(HatInfo o)
    {
        return name.compareTo(o.name);
    }
}
