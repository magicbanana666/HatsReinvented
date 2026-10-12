package com.magicbanana666.hats.common.hat;

import net.minecraft.ChatFormatting;

public enum Rarity
{
    COMMON(ChatFormatting.WHITE),
    UNCOMMON(ChatFormatting.GREEN),
    RARE(ChatFormatting.BLUE),
    EPIC(ChatFormatting.LIGHT_PURPLE),
    LEGENDARY(ChatFormatting.GOLD);

    private final ChatFormatting colour;

    Rarity(ChatFormatting colour)
    {
        this.colour = colour;
    }

    public ChatFormatting getColour()
    {
        return colour;
    }
}
