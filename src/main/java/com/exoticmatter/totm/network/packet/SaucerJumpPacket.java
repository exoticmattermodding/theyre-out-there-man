package com.exoticmatter.totm.network.packet;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.network.chat.Component;
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
            if (entity instanceof FlyingSaucerEntity saucer) {
                boolean allowed = false;
                try {
                    var f = FlyingSaucerEntity.class.getDeclaredField("pilotId");
                    f.setAccessible(true);
                    int pid = (int)f.get(saucer);
                    allowed = (pid == player.getId());
                } catch (Exception ignored) {}

                if (allowed) {
                    // no debug chat
                    saucer.onPlayerJump(pkt.power());
                } else {
                    // no debug chat
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
