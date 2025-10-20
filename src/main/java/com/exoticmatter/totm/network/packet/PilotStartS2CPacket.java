package com.exoticmatter.totm.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PilotStartS2CPacket(int saucerId) {
    public static void encode(PilotStartS2CPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.saucerId);
    }
    public static PilotStartS2CPacket decode(FriendlyByteBuf buf) {
        return new PilotStartS2CPacket(buf.readVarInt());
    }
    public static void handle(PilotStartS2CPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            // Start piloting (camera remains player; client tether centers view on the saucer)
            com.exoticmatter.totm.client.PilotClientState.startPiloting(pkt.saucerId());
            // Play mount sound locally for the controlling player
            if (mc.getSoundManager() != null) {
                // Play non-positional UI sound so it follows the listener, not a world position
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        com.exoticmatter.totm.registry.ModSounds.SAUCER_MOUNT_THEREMIN.get(), 1.0f));
            }
        });
        ctx.setPacketHandled(true);
    }
}
