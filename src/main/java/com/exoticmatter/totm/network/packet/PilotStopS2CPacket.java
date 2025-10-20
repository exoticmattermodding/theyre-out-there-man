package com.exoticmatter.totm.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PilotStopS2CPacket() {
    public static void encode(PilotStopS2CPacket pkt, FriendlyByteBuf buf) {}
    public static PilotStopS2CPacket decode(FriendlyByteBuf buf) { return new PilotStopS2CPacket(); }
    public static void handle(PilotStopS2CPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null) mc.setCameraEntity(mc.player);
            com.exoticmatter.totm.client.PilotClientState.stopPiloting();
        });
        ctx.setPacketHandled(true);
    }
}
