package com.exoticmatter.totm.network.packet;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PilotInputPacket(int saucerId, float yaw, float pitch, float strafe, float forward, boolean sneak) {
    public static void encode(PilotInputPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.saucerId);
        buf.writeFloat(pkt.yaw);
        buf.writeFloat(pkt.pitch);
        buf.writeFloat(pkt.strafe);
        buf.writeFloat(pkt.forward);
        buf.writeBoolean(pkt.sneak);
    }
    public static PilotInputPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        float strafe = buf.readFloat();
        float forward = buf.readFloat();
        boolean sneak = buf.readBoolean();
        return new PilotInputPacket(id, yaw, pitch, strafe, forward, sneak);
    }
    public static void handle(PilotInputPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
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
                        var fy = FlyingSaucerEntity.class.getDeclaredField("pilotYaw");
                        var fp = FlyingSaucerEntity.class.getDeclaredField("pilotPitch");
                        var fs = FlyingSaucerEntity.class.getDeclaredField("pilotStrafe");
                        var ff = FlyingSaucerEntity.class.getDeclaredField("pilotForward");
                        var fnk = FlyingSaucerEntity.class.getDeclaredField("pilotSneak");
                        fy.setAccessible(true); fp.setAccessible(true); fs.setAccessible(true); ff.setAccessible(true);
                        fy.setFloat(saucer, pkt.yaw());
                        fp.setFloat(saucer, pkt.pitch());
                        fs.setFloat(saucer, pkt.strafe());
                        ff.setFloat(saucer, pkt.forward());
                        fnk.setAccessible(true);
                        fnk.setBoolean(saucer, pkt.sneak());
                    }
                } catch (Exception ignored) {}
            }
        });
        ctx.setPacketHandled(true);
    }
}
