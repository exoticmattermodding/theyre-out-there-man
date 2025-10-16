package com.exoticmatter.totm.network.packet;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SaucerJumpPacket(int entityId, int power) {

    public static void encode(SaucerJumpPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.entityId);
        buf.writeVarInt(pkt.power);
    }

    public static SaucerJumpPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int power = buf.readVarInt();
        return new SaucerJumpPacket(entityId, power);
    }

    public static void handle(SaucerJumpPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            Entity entity = player.level().getEntity(pkt.entityId());
            if (entity instanceof FlyingSaucerEntity saucer && saucer.getControllingPassenger() == player) {
                saucer.onPlayerJump(pkt.power());
            }
        });
        ctx.setPacketHandled(true);
    }
}