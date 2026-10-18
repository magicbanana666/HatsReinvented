package com.magicbanana666.hats.common;

import com.magicbanana666.hats.common.command.HatsCommand;
import com.magicbanana666.hats.common.hat.HatHandler;
import com.magicbanana666.hats.common.hat.HatPart;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class CommonEvents
{
    private CommonEvents() {}

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> HatsCommand.register(dispatcher));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> onEntityJoin(entity));
        ServerLivingEntityEvents.AFTER_DEATH.register(CommonEvents::onLivingDeath);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            HatsConfig.loadServer(server);
            //rarities depend on the seed and weights
            HatHandler.allocateHatPools();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            HatsConfig.unloadServer();
            HatHandler.allocateHatPools();
        });
    }

    private static void onEntityJoin(Entity entity)
    {
        if(entity.level().isClientSide() || !(entity instanceof LivingEntity living) || living instanceof Player)
        {
            return;
        }
        if(HatHandler.hasBeenAllocated(living))
        {
            HatHandler.checkValidity(living);
            return;
        }
        if(HatHandler.canWearHat(living) && living.getRandom().nextDouble() < HatHandler.getHatChance(living))
        {
            HatHandler.assignHat(living);
        }
        else
        {
            HatHandler.assignNoHat(living);
        }
    }

    private static void onLivingDeath(LivingEntity victim, DamageSource source)
    {
        if(victim.level().isClientSide() || victim instanceof Player || !victim.hasAttached(HatsAttachments.HAT))
        {
            return;
        }
        HatPart hat = HatHandler.getHat(victim);
        if(!hat.isAHat() || hat.count <= 0)
        {
            return;
        }
        Entity killer = source.getEntity();
        if(killer instanceof ServerPlayer player && !(killer instanceof FakePlayer))
        {
            HatHandler.addHat(player, hat);
        }
        else if(killer instanceof LivingEntity livingKiller && !(killer instanceof Player) && livingKiller.isAlive() && HatsConfig.server(HatsConfig.mobHatTakeover, true))
        {
            if(HatHandler.canWearHat(livingKiller) && (!HatHandler.getHat(livingKiller).isAHat() || HatHandler.getHat(livingKiller).count <= 0))
            {
                HatHandler.setHat(livingKiller, hat.createCopy());
            }
        }
    }
}
