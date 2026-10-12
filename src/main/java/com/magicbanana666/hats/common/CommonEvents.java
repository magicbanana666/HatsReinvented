package com.magicbanana666.hats.common;

import com.magicbanana666.hats.HatsReinvented;
import com.magicbanana666.hats.common.command.HatsCommand;
import com.magicbanana666.hats.common.hat.HatHandler;
import com.magicbanana666.hats.common.hat.HatPart;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = HatsReinvented.MOD_ID)
public final class CommonEvents
{
    private CommonEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        HatsCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        if(event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living) || living instanceof Player)
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

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        LivingEntity victim = event.getEntity();
        if(victim.level().isClientSide() || victim instanceof Player || !victim.hasData(HatsAttachments.HAT))
        {
            return;
        }
        HatPart hat = HatHandler.getHat(victim);
        if(!hat.isAHat() || hat.count <= 0)
        {
            return;
        }
        Entity killer = event.getSource().getEntity();
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
