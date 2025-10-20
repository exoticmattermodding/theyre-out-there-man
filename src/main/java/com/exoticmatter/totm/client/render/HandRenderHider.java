package com.exoticmatter.totm.client.render;

import com.exoticmatter.totm.client.PilotClientState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HandRenderHider {
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (PilotClientState.isPiloting()) {
            event.setCanceled(true); // hide first-person hands/items while piloting the saucer
        }
    }
}

