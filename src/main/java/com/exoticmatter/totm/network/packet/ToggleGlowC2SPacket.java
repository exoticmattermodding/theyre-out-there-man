package com.exoticmatter.totm.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ToggleGlowC2SPacket(int saucerId) {
    public static void encode(ToggleGlowC2SPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.saucerId());
    }

    public static ToggleGlowC2SPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        return new ToggleGlowC2SPacket(id);
    }

    public static void handle(ToggleGlowC2SPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var sp = ctx.getSender();
            if (sp == null) return;
            var level = sp.level();
            var e = level.getEntity(pkt.saucerId());
            if (e instanceof com.exoticmatter.totm.world.entity.FlyingSaucerEntity saucer) {
                // Optional: only allow the pilot to toggle
                // Here, accept if sender is current pilot
                var pilotField = getPilotId(saucer);
                if (pilotField == sp.getId()) {
                    boolean newState = !saucer.isGlowEnabled();
                    saucer.setGlowEnabled(newState);
                    // Cascade to followers in formation: mirror leader glow state
                    if (sp.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        var box = saucer.getBoundingBox().inflate(128, 128, 128);
                        var followers = sl.getEntitiesOfClass(
                                com.exoticmatter.totm.world.entity.FlyingSaucerEntity.class,
                                box,
                                s -> s != saucer && s.getFormationLeaderId() == saucer.getId()
                        );
                        for (var f : followers) {
                            f.setGlowEnabled(newState);
                        }
                    }
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    private static int getPilotId(com.exoticmatter.totm.world.entity.FlyingSaucerEntity saucer) {
        try {
            var f = com.exoticmatter.totm.world.entity.FlyingSaucerEntity.class.getDeclaredField("pilotId");
            f.setAccessible(true);
            return (int) f.get(saucer);
        } catch (Exception ex) {
            return -1;
        }
    }
}
