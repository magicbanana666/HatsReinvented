package com.magicbanana666.hats.common.command;

import com.magicbanana666.hats.common.HatsAttachments;
import com.magicbanana666.hats.common.hat.HatHandler;
import com.magicbanana666.hats.common.hat.HatInfo;
import com.magicbanana666.hats.common.hat.HatPart;
import com.magicbanana666.hats.common.hat.HatResources;
import com.magicbanana666.hats.common.hat.PlayerHats;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;

/**
 * /hats give|set|remove|clear|list|reload
 */
public final class HatsCommand
{
    private static final DynamicCommandExceptionType UNKNOWN_HAT = new DynamicCommandExceptionType(name -> Component.translatable("commands.hats.unknown", name));
    private static final SuggestionProvider<CommandSourceStack> HAT_NAMES = (c, b) -> SharedSuggestionProvider.suggest(HatResources.HATS.keySet().stream().sorted(), b);

    private HatsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("hats")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("give")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                        .then(Commands.argument("hat", StringArgumentType.greedyString()).suggests(HAT_NAMES)
                                                .executes(c -> give(c, EntityArgument.getPlayers(c, "players"), IntegerArgumentType.getInteger(c, "count")))))))
                .then(Commands.literal("set")
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .then(Commands.argument("allAccessories", BoolArgumentType.bool())
                                        .then(Commands.argument("hat", StringArgumentType.greedyString()).suggests(HAT_NAMES)
                                                .executes(c -> set(c, EntityArgument.getEntities(c, "entities"), BoolArgumentType.getBool(c, "allAccessories")))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .executes(c -> remove(c.getSource(), EntityArgument.getEntities(c, "entities")))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> clear(c.getSource(), EntityArgument.getPlayers(c, "players")))))
                .then(Commands.literal("list")
                        .executes(c -> {
                            c.getSource().sendSuccess(() -> Component.translatable("commands.hats.list", HatResources.HATS.size()), false);
                            return HatResources.HATS.size();
                        }))
                .then(Commands.literal("reload")
                        .executes(c -> {
                            HatResources.loadAllHats();
                            c.getSource().sendSuccess(() -> Component.translatable("commands.hats.reload.success", HatResources.HATS.size()), true);
                            return 1;
                        }))
                .then(Commands.literal("reextract")
                        .executes(c -> {
                            HatResources.extractDefaults(true);
                            HatResources.loadAllHats();
                            c.getSource().sendSuccess(() -> Component.translatable("commands.hats.reextract.success", HatResources.HATS.size()), true);
                            return 1;
                        }))
        );
    }

    private static HatInfo getHat(CommandContext<CommandSourceStack> c) throws CommandSyntaxException
    {
        String name = StringArgumentType.getString(c, "hat");
        HatInfo info = HatResources.get(name);
        if(info == null)
        {
            throw UNKNOWN_HAT.create(name);
        }
        return info;
    }

    private static int give(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int count) throws CommandSyntaxException
    {
        HatInfo info = getHat(c);
        for(ServerPlayer player : players)
        {
            HatPart part = info.getAsHatPart(count);
            showAll(part);
            HatHandler.addHat(player, part);
            PlayerHats hats = HatHandler.getPlayerHats(player);
            HatPart owned = hats.get(info.name);
            if(owned != null && count > 1)
            {
                owned.count += count - 1;
                player.syncData(HatsAttachments.PLAYER_HATS);
            }
            c.getSource().sendSuccess(() -> Component.translatable("commands.hats.addCount.success", count, player.getDisplayName()), true);
        }
        return players.size();
    }

    private static void showAll(HatPart part)
    {
        part.isShowing = true;
        for(HatPart child : part.hatParts)
        {
            showAll(child);
        }
    }

    private static int set(CommandContext<CommandSourceStack> c, Collection<? extends Entity> entities, boolean allAccessories) throws CommandSyntaxException
    {
        HatInfo info = getHat(c);
        int count = 0;
        for(Entity entity : entities)
        {
            if(entity instanceof LivingEntity living)
            {
                HatPart part = info.getAsHatPart(1);
                if(allAccessories)
                {
                    showAll(part);
                }
                HatHandler.setHat(living, part);
                count++;
            }
        }
        int done = count;
        c.getSource().sendSuccess(() -> Component.translatable("commands.hats.setHat.success", done), true);
        return count;
    }

    private static int remove(CommandSourceStack source, Collection<? extends Entity> entities)
    {
        int count = 0;
        for(Entity entity : entities)
        {
            if(entity instanceof LivingEntity living)
            {
                HatHandler.setHat(living, null);
                count++;
            }
        }
        int done = count;
        source.sendSuccess(() -> Component.translatable("commands.hats.setHat.success", done), true);
        return count;
    }

    private static int clear(CommandSourceStack source, Collection<ServerPlayer> players)
    {
        for(ServerPlayer player : players)
        {
            HatHandler.getPlayerHats(player).hatParts.clear();
            player.syncData(HatsAttachments.PLAYER_HATS);
            HatHandler.setHat(player, null);
            source.sendSuccess(() -> Component.translatable("commands.hats.clear.success", player.getDisplayName()), true);
        }
        return players.size();
    }
}
