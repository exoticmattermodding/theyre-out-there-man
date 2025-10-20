package com.exoticmatter.totm.network.packet;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PilotStopC2SPacket(int saucerId) {
    public static void encode(PilotStopC2SPacket pkt, FriendlyByteBuf buf) { buf.writeVarInt(pkt.saucerId); }
    public static PilotStopC2SPacket decode(FriendlyByteBuf buf) { return new PilotStopC2SPacket(buf.readVarInt()); }
    public static void handle(PilotStopC2SPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = ctx.getSender();
            if (sp == null) return;
            Entity e = sp.level().getEntity(pkt.saucerId());
            if (e instanceof FlyingSaucerEntity saucer) {
                try {
                    var f = FlyingSaucerEntity.class.getDeclaredMethod("stopPiloting");
                    f.setAccessible(true);
                    f.invoke(saucer);
                } catch (Exception ignored) {}
            }
        });
        ctx.setPacketHandled(true);
    }
}

