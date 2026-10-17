package com.magicbanana666.hats.common.network;

import com.magicbanana666.hats.HatsReinvented;
import com.magicbanana666.hats.common.hat.HatHandler;
import com.magicbanana666.hats.common.hat.HatPart;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

/**
 * Client -> server: the hat the player picked in the menu, plus their colour / accessory choices.
 * changeWorn is false when the player didn't pick another hat (or take theirs off), so the worn hat is left alone.
 */
public record SetHatPayload(boolean changeWorn, Optional<HatPart> worn, List<HatPart> customised) implements CustomPacketPayload
{
    public static final Type<SetHatPayload> TYPE = new Type<>(HatsReinvented.id("set_hat"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetHatPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SetHatPayload::changeWorn,
            ByteBufCodecs.optional(HatPart.STREAM_CODEC), SetHatPayload::worn,
            HatPart.STREAM_CODEC.apply(ByteBufCodecs.list(512)), SetHatPayload::customised,
            SetHatPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(SetHatPayload payload, IPayloadContext context)
    {
        if(context.player() instanceof ServerPlayer player)
        {
            HatHandler.setPlayerHat(player, payload.changeWorn, payload.worn.orElse(null), payload.customised);
        }
    }
}
