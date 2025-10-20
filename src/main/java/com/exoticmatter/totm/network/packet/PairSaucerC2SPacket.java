package com.exoticmatter.totm.network.packet;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PairSaucerC2SPacket(int leaderId, int targetId) {
    public static void encode(PairSaucerC2SPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.leaderId());
        buf.writeInt(pkt.targetId());
    }

    public static PairSaucerC2SPacket decode(FriendlyByteBuf buf) {
        int l = buf.readInt();
        int t = buf.readInt();
        return new PairSaucerC2SPacket(l, t);
    }

    public static void handle(PairSaucerC2SPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var sp = ctx.getSender();
            if (sp == null) return;
            if (!(sp.level() instanceof ServerLevel sl)) return;
            var leaderEnt = sl.getEntity(pkt.leaderId());
            var targetEnt = sl.getEntity(pkt.targetId());
            if (!(leaderEnt instanceof FlyingSaucerEntity leader)) return;
            if (!(targetEnt instanceof FlyingSaucerEntity target)) return;
            // Validate sender is leader's pilot
            if (getPilotId(leader) != sp.getId()) return;
            if (leader == target) return;
            // Only pair unridden saucers
            if (target.getControllingPassenger() != null) return;

            // Determine next free slot (0..2)
            boolean[] used = new boolean[3];
            var nearby = sl.getEntitiesOfClass(FlyingSaucerEntity.class, leader.getBoundingBox().inflate(64,64,64),
                    s -> s != leader && s.getFormationLeaderId() == leader.getId());
            for (var f : nearby) {
                int fs = f.getFormationSlot();
                if (fs >= 0 && fs < used.length) used[fs] = true;
            }
            int slot = -1;
            for (int i = 0; i < used.length; i++) if (!used[i]) { slot = i; break; }
            if (slot == -1) return; // already at capacity

            // Give a generous initial expiry; leader refresh keeps it alive afterward
            target.setFormationLeaderSlot(leader.getId(), slot, sl.getGameTime() + 100);

            // Blink twice to confirm pairing
            target.requestConfirmBlink(2);
        });
        ctx.setPacketHandled(true);
    }

    private static int getPilotId(FlyingSaucerEntity saucer) {
        try {
            var f = FlyingSaucerEntity.class.getDeclaredField("pilotId");
            f.setAccessible(true);
            return (int) f.get(saucer);
        } catch (Exception ex) {
            return -1;
        }
    }
}
