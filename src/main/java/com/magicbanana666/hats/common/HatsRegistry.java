package com.magicbanana666.hats.common;

import com.magicbanana666.hats.HatsReinvented;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class HatsRegistry
{
    public static final Holder.Reference<SoundEvent> TUBE = sound("tube");
    public static final Holder.Reference<SoundEvent> POOF = sound("poof");
    public static final Holder.Reference<SoundEvent> BONK = sound("bonk");

    private HatsRegistry() {}

    /** Loads the class, which registers the sounds. */
    public static void register() {}

    private static Holder.Reference<SoundEvent> sound(String name)
    {
        Identifier id = HatsReinvented.id(name);
        return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
