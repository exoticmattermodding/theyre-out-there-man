package com.exoticmatter.totm.client;

import com.exoticmatter.totm.network.ModNetwork;
import com.exoticmatter.totm.network.packet.AbductionStateC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AbductionClientInput {
    private static boolean lastDown = false;

    // Note: InputEvent.Key is not cancelable on 1.20.1; do not attempt to cancel.
    // We simply detect state in client tick below and avoid canceling to prevent crashes.

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!PilotClientState.isPiloting()) { lastDown = false; return; }

        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean abductDown = com.exoticmatter.totm.client.GlowClientOptions.TOGGLE_BEAM.isDown();
        if (abductDown != lastDown) {
            int id = PilotClientState.getPilotedSaucerId();
            if (id != -1) ModNetwork.sendToServer(new AbductionStateC2SPacket(id, abductDown));
            lastDown = abductDown;
        }
    }
}
