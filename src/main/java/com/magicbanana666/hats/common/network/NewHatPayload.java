package com.magicbanana666.hats.common.network;

import com.magicbanana666.hats.HatsReinvented;
import com.magicbanana666.hats.common.hat.HatPart;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server -> client: the player unlocked a new hat and/or accessories (shows a toast). */
public record NewHatPayload(boolean isNewHat, HatPart hat, List<String> newAccessories) implements CustomPacketPayload
{
    public static final Type<NewHatPayload> TYPE = new Type<>(HatsReinvented.id("new_hat"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NewHatPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NewHatPayload::isNewHat,
            HatPart.STREAM_CODEC, NewHatPayload::hat,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), NewHatPayload::newAccessories,
            NewHatPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
