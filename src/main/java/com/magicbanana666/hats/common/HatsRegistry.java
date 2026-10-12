package com.magicbanana666.hats.common;

import com.magicbanana666.hats.HatsReinvented;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HatsRegistry
{
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HatsReinvented.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> TUBE = SOUNDS.register("tube", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> POOF = SOUNDS.register("poof", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> BONK = SOUNDS.register("bonk", SoundEvent::createVariableRangeEvent);

    private HatsRegistry() {}

    public static void register(IEventBus modBus)
    {
        SOUNDS.register(modBus);
    }
}
