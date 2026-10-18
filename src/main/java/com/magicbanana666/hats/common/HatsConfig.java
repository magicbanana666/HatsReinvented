package com.magicbanana666.hats.common;

import com.magicbanana666.hats.HatsReinvented;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The server config is kept per world (in serverconfig, with defaults from defaultconfigs, like on NeoForge), the
 * client config in the config folder.
 */
public final class HatsConfig
{
    public static final SimpleConfig SERVER_SPEC;
    public static final SimpleConfig CLIENT_SPEC;

    public static SimpleConfig.ConfigValue<String> randSeed;
    public static SimpleConfig.DoubleValue hatChance;
    public static SimpleConfig.ConfigValue<List<? extends String>> entityOverrideChance;
    public static SimpleConfig.ConfigValue<List<? extends String>> disabledMobs;
    public static SimpleConfig.ConfigValue<List<? extends String>> bossMobs;
    public static SimpleConfig.ConfigValue<List<? extends Integer>> rarityWeight;
    public static SimpleConfig.DoubleValue bossHatChanceBonus;
    public static SimpleConfig.DoubleValue bossRarityBonus;
    public static SimpleConfig.BooleanValue mobHatTakeover;
    public static SimpleConfig.BooleanValue creativeHasAllHats;
    public static SimpleConfig.BooleanValue sendNewHatMessage;
    public static SimpleConfig.BooleanValue hatLauncherReplacesPlayerHat;

    public static SimpleConfig.BooleanValue renderHats;
    public static SimpleConfig.IntValue maxHatRenders;

    private static final String SERVER_FILE = HatsReinvented.MOD_ID + "-server.toml";
    private static final String CLIENT_FILE = HatsReinvented.MOD_ID + "-client.toml";

    static
    {
        SimpleConfig.Builder server = new SimpleConfig.Builder();
        server.push("hatRandomisation");
        randSeed = server.comment("Seed that decides the rarity of each hat. Change it to shuffle the rarities.").define("randSeed", "Hats");
        hatChance = server.comment("Chance (0 - 1) that a mob spawns with a hat.").defineInRange("hatChance", 0.1D, 0D, 1D);
        entityOverrideChance = server.comment("Hat chance per mob, in percent. Format: <entity id>;<percent>, EG minecraft:zombie;50").defineStringList("entityOverrideChance", List.of(), s -> true);
        disabledMobs = server.comment("Mobs that never get hats.").defineStringList("disabledMobs", List.of("minecraft:armor_stand"), s -> true);
        bossMobs = server.comment("Boss mobs: more likely to have (rarer) hats.").defineStringList("bossMobs", List.of("minecraft:ender_dragon", "minecraft:wither", "minecraft:elder_guardian", "minecraft:warden"), s -> true);
        rarityWeight = server.comment("Weights of the rarities: common, uncommon, rare, epic, legendary.").defineIntList("rarityWeight", List.of(21, 13, 8, 5, 3), i -> i >= 0);
        bossHatChanceBonus = server.defineInRange("bossHatChanceBonus", 0.1D, 0D, 1D);
        bossRarityBonus = server.defineInRange("bossRarityBonus", 0.2D, 0D, 1D);
        server.pop();
        server.push("gameplay");
        mobHatTakeover = server.comment("A mob that kills a mob with a hat takes the hat.").define("mobHatTakeover", true);
        creativeHasAllHats = server.comment("Players in creative mode can wear every hat.").define("creativeHasAllHats", true);
        sendNewHatMessage = server.comment("Tell players when they unlock a new hat or accessory.").define("sendNewHatMessage", true);
        hatLauncherReplacesPlayerHat = server.comment("Hitting a player with the hat launcher changes their hat.").define("hatLauncherReplacesPlayerHat", true);
        server.pop();
        SERVER_SPEC = server.build();

        SimpleConfig.Builder client = new SimpleConfig.Builder();
        renderHats = client.comment("Render hats at all.").define("renderHats", true);
        maxHatRenders = client.comment("Most mob hats rendered per frame (players always show).").defineInRange("maxHatRenders", 300, 0, 100000);
        CLIENT_SPEC = client.build();
    }

    private HatsConfig() {}

    public static <T> T server(SimpleConfig.ConfigValue<T> value, T fallback)
    {
        return SERVER_SPEC.isLoaded() ? value.get() : fallback;
    }

    public static <T> T client(SimpleConfig.ConfigValue<T> value, T fallback)
    {
        return CLIENT_SPEC.isLoaded() ? value.get() : fallback;
    }

    /** Loads (or reloads) the server config of the world that is running. */
    public static void loadServer(MinecraftServer server)
    {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(SERVER_FILE);
        if(!Files.exists(file))
        {
            Path defaults = FabricLoader.getInstance().getGameDir().resolve("defaultconfigs").resolve(SERVER_FILE);
            if(Files.exists(defaults))
            {
                try
                {
                    Files.createDirectories(file.getParent());
                    Files.copy(defaults, file);
                }
                catch(IOException e)
                {
                    HatsReinvented.LOGGER.warn("Could not copy the default server config {}: {}", defaults, e.toString());
                }
            }
        }
        SERVER_SPEC.load(file);
    }

    public static void unloadServer()
    {
        SERVER_SPEC.unload();
    }

    public static void loadClient()
    {
        CLIENT_SPEC.load(FabricLoader.getInstance().getConfigDir().resolve(CLIENT_FILE));
    }
}
