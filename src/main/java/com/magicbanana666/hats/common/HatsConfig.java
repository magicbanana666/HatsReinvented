package com.magicbanana666.hats.common;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class HatsConfig
{
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static ModConfigSpec.ConfigValue<String> randSeed;
    public static ModConfigSpec.DoubleValue hatChance;
    public static ModConfigSpec.ConfigValue<List<? extends String>> entityOverrideChance;
    public static ModConfigSpec.ConfigValue<List<? extends String>> disabledMobs;
    public static ModConfigSpec.ConfigValue<List<? extends String>> bossMobs;
    public static ModConfigSpec.ConfigValue<List<? extends Integer>> rarityWeight;
    public static ModConfigSpec.DoubleValue bossHatChanceBonus;
    public static ModConfigSpec.DoubleValue bossRarityBonus;
    public static ModConfigSpec.BooleanValue mobHatTakeover;
    public static ModConfigSpec.BooleanValue creativeHasAllHats;
    public static ModConfigSpec.BooleanValue sendNewHatMessage;
    public static ModConfigSpec.BooleanValue hatLauncherReplacesPlayerHat;

    public static ModConfigSpec.BooleanValue renderHats;
    public static ModConfigSpec.IntValue maxHatRenders;

    static
    {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.push("hatRandomisation");
        randSeed = server.comment("Seed that decides the rarity of each hat. Change it to shuffle the rarities.").define("randSeed", "Hats");
        hatChance = server.comment("Chance (0 - 1) that a mob spawns with a hat.").defineInRange("hatChance", 0.1D, 0D, 1D);
        entityOverrideChance = server.comment("Hat chance per mob, in percent. Format: <entity id>;<percent>, EG minecraft:zombie;50").defineListAllowEmpty("entityOverrideChance", List.of(), () -> "minecraft:zombie;50", o -> o instanceof String);
        disabledMobs = server.comment("Mobs that never get hats.").defineListAllowEmpty("disabledMobs", List.of("minecraft:armor_stand"), () -> "", o -> o instanceof String);
        bossMobs = server.comment("Boss mobs: more likely to have (rarer) hats.").defineListAllowEmpty("bossMobs", List.of("minecraft:ender_dragon", "minecraft:wither", "minecraft:elder_guardian", "minecraft:warden"), () -> "", o -> o instanceof String);
        rarityWeight = server.comment("Weights of the rarities: common, uncommon, rare, epic, legendary.").defineList("rarityWeight", List.of(21, 13, 8, 5, 3), () -> 1, o -> o instanceof Integer i && i >= 0);
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

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        renderHats = client.comment("Render hats at all.").define("renderHats", true);
        maxHatRenders = client.comment("Most mob hats rendered per frame (players always show).").defineInRange("maxHatRenders", 300, 0, 100000);
        CLIENT_SPEC = client.build();
    }

    private HatsConfig() {}

    public static <T> T server(ModConfigSpec.ConfigValue<T> value, T fallback)
    {
        return SERVER_SPEC.isLoaded() ? value.get() : fallback;
    }

    public static <T> T client(ModConfigSpec.ConfigValue<T> value, T fallback)
    {
        return CLIENT_SPEC.isLoaded() ? value.get() : fallback;
    }
}
