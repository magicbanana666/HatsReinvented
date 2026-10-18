package com.magicbanana666.hats.common;

import com.magicbanana666.hats.HatsReinvented;
import com.magicbanana666.hats.common.hat.HatPart;
import com.magicbanana666.hats.common.hat.PlayerHats;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class HatsAttachments
{
    private static final Codec<PlayerHats> PLAYER_HATS_CODEC = CompoundTag.CODEC.xmap(HatsAttachments::playerHatsFromTag, hats -> hats.write(new CompoundTag()));
    private static final StreamCodec<RegistryFriendlyByteBuf, PlayerHats> PLAYER_HATS_STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(HatsAttachments::playerHatsFromTag, hats -> hats.write(new CompoundTag())).cast();

    /** The hat an entity is wearing. count -1 means "no hat decided yet". Synced to everyone who can see the entity. */
    public static final AttachmentType<HatPart> HAT = AttachmentRegistry.create(HatsReinvented.id("hat"), builder -> builder
            .initializer(HatPart::new)
            .persistent(CompoundTag.CODEC.xmap(HatPart::fromTag, part -> part.write(new CompoundTag())))
            .copyOnDeath()
            .syncWith(HatPart.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** The hats a player owns. Saved, kept on death, only synced to that player. */
    public static final AttachmentType<PlayerHats> PLAYER_HATS = AttachmentRegistry.create(HatsReinvented.id("player_hats"), builder -> builder
            .initializer(PlayerHats::new)
            .persistent(PLAYER_HATS_CODEC)
            .copyOnDeath()
            .syncWith(PLAYER_HATS_STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private HatsAttachments() {}

    /** Loads the class, which registers the attachment types. */
    public static void init() {}

    private static PlayerHats playerHatsFromTag(CompoundTag tag)
    {
        PlayerHats hats = new PlayerHats();
        hats.read(tag);
        return hats;
    }

    /**
     * Sends the (changed in place) hat of the entity to the clients. Fabric only syncs when a different value is set,
     * so a copy is set.
     */
    public static void syncHat(Entity entity)
    {
        entity.setAttached(HAT, entity.getAttachedOrCreate(HAT).createCopy());
    }

    /** Sends the (changed in place) owned hats of the player to that player. */
    public static void syncPlayerHats(Player player)
    {
        player.setAttached(PLAYER_HATS, playerHatsFromTag(player.getAttachedOrCreate(PLAYER_HATS).write(new CompoundTag())));
    }
}
