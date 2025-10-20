package com.exoticmatter.totm.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ZoomTuner {
    private static final double MIN_DIST = 4.0;  // closer zoom-in
    private static final double MAX_DIST = 28.0; // reduced zoom-out

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) { clamp(); }

    @SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        clamp();
    }

    private static void clamp() {
        if (!com.exoticmatter.totm.client.PilotClientState.isPiloting()) return;
        try {
            var cls = com.exoticmatter.totm.client.PilotClientState.class;
            var f = cls.getDeclaredField("camDistance");
            f.setAccessible(true);
            double d = f.getDouble(null);
            if (d < MIN_DIST) d = MIN_DIST;
            if (d > MAX_DIST) d = MAX_DIST;
            f.setDouble(null, d);
        } catch (Throwable ignored) {}
    }
}
