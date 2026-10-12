package com.magicbanana666.hats.common;

import com.magicbanana666.hats.HatsReinvented;
import com.magicbanana666.hats.common.hat.HatPart;
import com.magicbanana666.hats.common.hat.PlayerHats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class HatsAttachments
{
    public static final DeferredRegister<AttachmentType<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, HatsReinvented.MOD_ID);

    /** The hat an entity is wearing. count -1 means "no hat decided yet". Synced to everyone who can see the entity. */
    public static final Supplier<AttachmentType<HatPart>> HAT = REGISTER.register("hat", () -> AttachmentType.<HatPart>builder(() -> new HatPart())
            .serialize(new IAttachmentSerializer<>()
            {
                @Override
                public HatPart read(IAttachmentHolder holder, ValueInput input)
                {
                    return input.read("data", CompoundTag.CODEC).map(HatPart::fromTag).orElseGet(HatPart::new);
                }

                @Override
                public boolean write(HatPart attachment, ValueOutput output)
                {
                    output.store("data", CompoundTag.CODEC, attachment.write(new CompoundTag()));
                    return true;
                }
            })
            .copyOnDeath()
            .sync(new AttachmentSyncHandler<>()
            {
                @Override
                public void write(RegistryFriendlyByteBuf buf, HatPart attachment, boolean initialSync)
                {
                    buf.writeNbt(attachment.write(new CompoundTag()));
                }

                @Override
                public HatPart read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable HatPart previousValue)
                {
                    HatPart part = previousValue != null ? previousValue : new HatPart();
                    CompoundTag tag = buf.readNbt();
                    part.read(tag != null ? tag : new CompoundTag());
                    return part;
                }
            })
            .build());

    /** The hats a player owns. Saved, kept on death, only synced to that player. */
    public static final Supplier<AttachmentType<PlayerHats>> PLAYER_HATS = REGISTER.register("player_hats", () -> AttachmentType.<PlayerHats>builder(() -> new PlayerHats())
            .serialize(new IAttachmentSerializer<>()
            {
                @Override
                public PlayerHats read(IAttachmentHolder holder, ValueInput input)
                {
                    PlayerHats hats = new PlayerHats();
                    input.read("data", CompoundTag.CODEC).ifPresent(hats::read);
                    return hats;
                }

                @Override
                public boolean write(PlayerHats attachment, ValueOutput output)
                {
                    output.store("data", CompoundTag.CODEC, attachment.write(new CompoundTag()));
                    return true;
                }
            })
            .copyOnDeath()
            .sync(new AttachmentSyncHandler<>()
            {
                @Override
                public boolean sendToPlayer(IAttachmentHolder holder, ServerPlayer to)
                {
                    return holder == to;
                }

                @Override
                public void write(RegistryFriendlyByteBuf buf, PlayerHats attachment, boolean initialSync)
                {
                    buf.writeNbt(attachment.write(new CompoundTag()));
                }

                @Override
                public PlayerHats read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable PlayerHats previousValue)
                {
                    PlayerHats hats = previousValue != null ? previousValue : new PlayerHats();
                    CompoundTag tag = buf.readNbt();
                    hats.read(tag != null ? tag : new CompoundTag());
                    return hats;
                }
            })
            .build());

    private HatsAttachments() {}
}
