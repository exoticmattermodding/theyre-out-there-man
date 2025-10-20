package com.exoticmatter.totm.network.packet;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AbductionStateC2SPacket(int saucerId, boolean active) {
    public static void encode(AbductionStateC2SPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.saucerId);
        buf.writeBoolean(pkt.active);
    }
    public static AbductionStateC2SPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        boolean a = buf.readBoolean();
        return new AbductionStateC2SPacket(id, a);
    }
    public static void handle(AbductionStateC2SPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = ctx.getSender();
            if (sp == null) return;
            Entity e = sp.level().getEntity(pkt.saucerId());
            if (e instanceof FlyingSaucerEntity saucer) {
                try {
                    var f = FlyingSaucerEntity.class.getDeclaredField("pilotId");
                    f.setAccessible(true);
                    int pid = (int)f.get(saucer);
                    if (pid == sp.getId()) {
                        saucer.setAbductionActive(pkt.active());
                    }
                } catch (Exception ignored) {}
            }
        });
        ctx.setPacketHandled(true);
    }
}

