package com.magicbanana666.hats.common.hat;

import com.magicbanana666.hats.HatsReinvented;
import com.magicbanana666.hats.common.HatsAttachments;
import com.magicbanana666.hats.common.HatsConfig;
import com.magicbanana666.hats.common.network.NewHatPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Server side hat logic: rarity pools, giving mobs hats, players collecting hats. Ported from Hats 1.16's HatHandler.
 */
public final class HatHandler
{
    /** Rebuilt aside and swapped in, as it is read on the server thread while a reload (config watcher, /hats reload) runs. */
    private static volatile Map<Rarity, List<HatPool>> hatPools = new EnumMap<>(Rarity.class);

    private HatHandler() {}

    /** Hats that share a pool (EG all contributor hats) share one rarity roll. */
    public static final class HatPool
    {
        public final List<HatInfo> hats = new ArrayList<>();
        public Rarity forcedRarity;

        public HatInfo getRandomHat(Random random)
        {
            return hats.get(random.nextInt(hats.size()));
        }
    }

    // ------------------------------------------------------------------ config helpers

    public static String getRandSeed()
    {
        return HatsConfig.server(HatsConfig.randSeed, "Hats");
    }

    private static List<Double> rarityMeasure()
    {
        List<? extends Integer> weights = HatsConfig.server(HatsConfig.rarityWeight, List.of(21, 13, 8, 5, 3));
        int count = Rarity.values().length;
        double total = 0;
        double[] w = new double[count];
        for(int i = 0; i < count; i++)
        {
            w[i] = i < weights.size() ? Math.max(0, weights.get(i)) : 0;
            total += w[i];
        }
        if(total <= 0)
        {
            w[0] = 1;
            total = 1;
        }
        List<Double> measure = new ArrayList<>();
        double stack = 0;
        for(int i = 0; i < count; i++)
        {
            stack += w[i];
            measure.add(stack / total);
        }
        return measure;
    }

    public static double getRarityIndividualChance(Rarity rarity)
    {
        List<Double> measure = rarityMeasure();
        int i = rarity.ordinal();
        return measure.get(i) - (i > 0 ? measure.get(i - 1) : 0D);
    }

    public static double getBossRarityBonus()
    {
        return HatsConfig.server(HatsConfig.bossRarityBonus, 0.2D);
    }

    public static Rarity getRarityForChance(double chance)
    {
        List<Double> measure = rarityMeasure();
        Rarity[] rarities = Rarity.values();
        for(int i = 0; i < measure.size(); i++)
        {
            if(chance < measure.get(i))
            {
                return rarities[i];
            }
        }
        return Rarity.LEGENDARY;
    }

    // ------------------------------------------------------------------ pools

    public static void allocateHatPools()
    {
        allocateHatPools(HatResources.HATS);
    }

    public static synchronized void allocateHatPools(Map<String, HatInfo> hats)
    {
        EnumMap<Rarity, List<HatPool>> pools = new EnumMap<>(Rarity.class);
        Map<String, HatPool> poolsByName = new HashMap<>();
        for(Map.Entry<String, HatInfo> entry : hats.entrySet())
        {
            HatInfo info = entry.getValue();
            info.resetRarity();
            HatPool pool = poolsByName.computeIfAbsent(info.forcedPool != null ? info.forcedPool : entry.getKey(), k -> new HatPool());
            pool.hats.add(info);
            if(info.forcedRarity != null)
            {
                pool.forcedRarity = info.forcedRarity;
            }
        }
        for(Map.Entry<String, HatPool> entry : poolsByName.entrySet())
        {
            HatPool pool = entry.getValue();
            Rarity rarity = pool.forcedRarity;
            if(rarity == null)
            {
                Random random = new Random(Math.abs((getRandSeed() + entry.getKey()).hashCode()) * 154041013L);
                rarity = getRarityForChance(random.nextDouble());
            }
            for(HatInfo info : pool.hats)
            {
                info.setRarity(rarity);
            }
            pools.computeIfAbsent(rarity, k -> new ArrayList<>()).add(pool);
        }
        for(Rarity rarity : Rarity.values())
        {
            pools.computeIfAbsent(rarity, k -> new ArrayList<>());
        }
        hatPools = pools;
    }

    // ------------------------------------------------------------------ entities

    public static HatPart getHat(LivingEntity entity)
    {
        return entity.getAttachedOrCreate(HatsAttachments.HAT);
    }

    public static boolean hasBeenAllocated(LivingEntity entity)
    {
        return entity.hasAttached(HatsAttachments.HAT) && getHat(entity).count >= 0;
    }

    public static boolean isBoss(LivingEntity entity)
    {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return HatsConfig.server(HatsConfig.bossMobs, List.of()).contains(id.toString());
    }

    public static boolean canWearHat(LivingEntity entity)
    {
        if(!(entity instanceof Mob))
        {
            return false;
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return !HatsConfig.server(HatsConfig.disabledMobs, List.of()).contains(id.toString());
    }

    public static double getHatChance(LivingEntity entity)
    {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        for(String entry : HatsConfig.server(HatsConfig.entityOverrideChance, List.<String>of()))
        {
            String[] split = entry.split(";");
            if(split.length == 2 && split[0].trim().equals(id))
            {
                try
                {
                    return Double.parseDouble(split[1].trim()) / 100D;
                }
                catch(NumberFormatException ignored) {}
            }
        }
        double chance = HatsConfig.server(HatsConfig.hatChance, 0.1D);
        if(isBoss(entity))
        {
            chance += HatsConfig.server(HatsConfig.bossHatChanceBonus, 0.1D);
        }
        return chance;
    }

    /** Gives the mob a random hat (seeded by its UUID, like the original). */
    public static void assignHat(LivingEntity entity)
    {
        HatPart hat = getHat(entity);
        Random random = new Random(Math.abs((getRandSeed() + entity.getUUID()).hashCode()) * 425480085L);
        double chance = random.nextDouble();
        boolean boss = isBoss(entity);
        if(boss)
        {
            chance += getBossRarityBonus();
        }
        Rarity rarity = getRarityForChance(chance);
        List<HatPool> pools = hatPools.get(rarity);
        if(pools == null || pools.isEmpty())
        {
            assignNoHat(entity);
            return;
        }
        HatInfo info = pools.get(random.nextInt(pools.size())).getRandomHat(random);
        HatPart part = new HatPart(info.name);
        part.isShowing = true;
        info.assignAccessoriesToPart(part, entity, boss);
        hat.copy(part);
        HatsAttachments.syncHat(entity);
    }

    public static void assignNoHat(LivingEntity entity)
    {
        HatPart hat = getHat(entity);
        hat.copy(new HatPart());
        hat.count = 0;
        HatsAttachments.syncHat(entity);
    }

    public static void setHat(LivingEntity entity, @Nullable HatPart part)
    {
        if(part == null || !part.isAHat())
        {
            assignNoHat(entity);
            return;
        }
        getHat(entity).copy(part);
        HatsAttachments.syncHat(entity);
    }

    /** Removes hats (and accessories) the server doesn't have the files for. */
    public static void checkValidity(LivingEntity entity)
    {
        HatPart hat = getHat(entity);
        if(hat.isAHat() && hat.count > 0 && HatResources.get(hat) == null)
        {
            assignNoHat(entity);
        }
    }

    // ------------------------------------------------------------------ players

    public static PlayerHats getPlayerHats(Player player)
    {
        return player.getAttachedOrCreate(HatsAttachments.PLAYER_HATS);
    }

    public static boolean useInventory(Player player)
    {
        return !(player.isCreative() && HatsConfig.server(HatsConfig.creativeHasAllHats, true));
    }

    /** The player killed a mob wearing this hat. */
    public static void addHat(ServerPlayer player, HatPart mobHat)
    {
        HatInfo info = HatResources.get(mobHat);
        if(info == null)
        {
            return;
        }
        HatPart toAdd = mobHat.createCopy();
        toAdd.count = 1;
        toAdd.hideAll();
        toAdd.isShowing = true;
        setCountOfAll(toAdd, 1);

        PlayerHats hats = getPlayerHats(player);
        HatPart owned = hats.get(toAdd.name);
        boolean isNewHat = owned == null || owned.count <= 0;
        if(owned == null)
        {
            owned = new HatPart(toAdd.name).setNew();
            owned.count = 0;
            hats.hatParts.add(owned);
        }

        List<String> newAccessories = new ArrayList<>();
        collectMissing(owned, toAdd, newAccessories);
        owned.add(toAdd);
        if(isNewHat || !newAccessories.isEmpty())
        {
            owned.setNew();
            if(HatsConfig.server(HatsConfig.sendNewHatMessage, true))
            {
                ServerPlayNetworking.send(player, new NewHatPayload(isNewHat, mobHat.createCopy(), newAccessories));
            }
        }
        HatsAttachments.syncPlayerHats(player);
    }

    private static void setCountOfAll(HatPart part, int count)
    {
        part.count = count;
        for(HatPart child : part.hatParts)
        {
            setCountOfAll(child, count);
        }
    }

    /** Names of accessories in target the source doesn't own yet. */
    private static void collectMissing(@Nullable HatPart source, HatPart target, List<String> names)
    {
        for(HatPart child : target.hatParts)
        {
            HatPart sourceChild = source == null ? null : find(source.hatParts, child.name);
            if(sourceChild == null || sourceChild.count <= 0)
            {
                names.add(child.name);
            }
            collectMissing(sourceChild, child, names);
        }
    }

    @Nullable
    private static HatPart find(List<HatPart> parts, String name)
    {
        for(HatPart part : parts)
        {
            if(part.name.equals(name))
            {
                return part;
            }
        }
        return null;
    }

    /**
     * Every hat the player can pick in the menu: all hats, with the player's counts and colours merged in
     * (count 0 = not unlocked, unless useInventory is off, then everything is available).
     */
    public static List<HatPart> getHatSource(Player player)
    {
        boolean inventory = useInventory(player);
        List<HatPart> source = HatResources.getAllHatsAsParts(inventory ? 0 : 1);
        PlayerHats hats = getPlayerHats(player);
        for(HatPart owned : hats.hatParts)
        {
            for(HatPart hat : source)
            {
                if(hat.name.equals(owned.name))
                {
                    if(inventory)
                    {
                        hat.add(owned);
                    }
                    else
                    {
                        mergeOwned(hat, owned);
                    }
                    hat.copyPersonalisation(owned);
                    break;
                }
            }
        }
        return source;
    }

    private static void mergeOwned(HatPart all, HatPart owned)
    {
        all.isNew = owned.isNew;
        for(HatPart child : all.hatParts)
        {
            HatPart ownedChild = find(owned.hatParts, child.name);
            if(ownedChild != null)
            {
                mergeOwned(child, ownedChild);
            }
        }
    }

    /** Can the player wear this exact hat (with these accessories)? */
    public static boolean canWear(Player player, HatPart wanted)
    {
        if(!useInventory(player))
        {
            return HatResources.get(wanted) != null;
        }
        HatPart owned = getPlayerHats(player).get(wanted.name);
        return owned != null && owned.count > 0 && ownsShownAccessories(owned, wanted);
    }

    private static boolean ownsShownAccessories(HatPart owned, HatPart wanted)
    {
        for(HatPart child : wanted.hatParts)
        {
            if(!child.isShowing)
            {
                continue;
            }
            HatPart ownedChild = find(owned.hatParts, child.name);
            if(ownedChild == null || ownedChild.count <= 0 || !ownsShownAccessories(ownedChild, child))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Rebuilds a hat the client sent from the loaded hat, keeping only the client's colours and show / hide choices
     * for accessories that really exist. Null if there is no such hat.
     */
    @Nullable
    private static HatPart fromClient(HatPart sent, int count)
    {
        HatInfo info = HatResources.get(sent);
        if(info == null)
        {
            return null;
        }
        HatPart part = info.getAsHatPart(count);
        part.copyPersonalisation(sent);
        clampColours(part);
        return part;
    }

    private static void clampColours(HatPart part)
    {
        for(int i = 0; i < part.colouriser.length; i++)
        {
            part.colouriser[i] = Float.isNaN(part.colouriser[i]) ? 0F : Math.clamp(part.colouriser[i], 0F, 1F);
        }
        for(int i = 0; i < part.hsbiser.length; i++)
        {
            part.hsbiser[i] = Float.isNaN(part.hsbiser[i]) ? 0F : Math.clamp(part.hsbiser[i], 0F, 1F);
        }
        for(HatPart child : part.hatParts)
        {
            clampColours(child);
        }
    }

    /**
     * Saves the colours / accessories the player picked for their hats, and (if changeWorn) puts on the chosen hat,
     * or takes the hat off when worn is null.
     */
    public static void setPlayerHat(ServerPlayer player, boolean changeWorn, @Nullable HatPart worn, List<HatPart> customised)
    {
        PlayerHats hats = getPlayerHats(player);
        for(HatPart custom : customised)
        {
            HatPart clean = fromClient(custom, 0);
            if(clean == null)
            {
                continue;
            }
            HatPart owned = hats.get(clean.name);
            if(owned != null)
            {
                owned.copyPersonalisation(clean);
                owned.setNoNew();
            }
            else if(!useInventory(player))
            {
                hats.hatParts.add(clean);
            }
        }
        HatsAttachments.syncPlayerHats(player);

        if(!changeWorn)
        {
            return;
        }
        if(worn == null || !worn.isAHat())
        {
            setHat(player, null);
            return;
        }
        HatPart wanted = fromClient(worn, 1);
        if(wanted != null && canWear(player, wanted))
        {
            wanted.isShowing = true;
            wanted.isNew = false;
            setHat(player, wanted);
        }
        else
        {
            HatsReinvented.LOGGER.debug("{} tried to wear a hat they don't own: {}", player.getGameProfile().name(), worn.name);
        }
    }

    public static Component describe(HatPart part)
    {
        HatInfo info = HatResources.get(part);
        return info != null ? info.getDisplayName() : Component.literal(part.name);
    }
}
