package com.exoticmatter.totm.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AlienCowSkinS2CPacket(int entityId, boolean apply) {
    public static void encode(AlienCowSkinS2CPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.entityId());
        buf.writeBoolean(pkt.apply());
    }

    public static AlienCowSkinS2CPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        boolean on = buf.readBoolean();
        return new AlienCowSkinS2CPacket(id, on);
    }

    public static void handle(AlienCowSkinS2CPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            Entity e = level.getEntity(pkt.entityId());
            if (e instanceof Cow cow) {
                cow.getPersistentData().putBoolean("totm_alien_cow", pkt.apply());
            }
        });
        ctx.setPacketHandled(true);
    }
}

