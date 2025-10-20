package com.exoticmatter.totm.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientSoundRuntime {
    private static final java.util.Map<Integer, net.minecraft.client.resources.sounds.AbstractTickableSoundInstance> ENGINE_SOUNDS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, net.minecraft.client.resources.sounds.AbstractTickableSoundInstance> BEAM_SOUNDS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, net.minecraft.client.resources.sounds.AbstractTickableSoundInstance> DASH_SOUNDS = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;

        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof com.exoticmatter.totm.world.entity.FlyingSaucerEntity fe) {
                int id = fe.getId();
                seen.add(id);
                var eng = ENGINE_SOUNDS.get(id);
                if (eng == null) {
                    eng = new com.exoticmatter.totm.client.sound.SaucerEngineLoopSound(fe, com.exoticmatter.totm.registry.ModSounds.SAUCER_ENGINE.get());
                    ENGINE_SOUNDS.put(id, eng);
                    mc.getSoundManager().play(eng);
                }
                if (fe.isAbductionActive()) {
                    var bm = BEAM_SOUNDS.get(id);
                    if (bm == null || !mc.getSoundManager().isActive(bm)) {
                        bm = new com.exoticmatter.totm.client.sound.SaucerBeamLoopSound(fe, com.exoticmatter.totm.registry.ModSounds.SAUCER_BEAM_LOOP.get());
                        BEAM_SOUNDS.put(id, bm);
                        mc.getSoundManager().play(bm);
                    }
                }
                // Dash loop strictly during dash window; do not re-trigger while active
                if (fe.isDashing()) {
                    var ds = DASH_SOUNDS.get(id);
                    if (ds == null) {
                        ds = new com.exoticmatter.totm.client.sound.SaucerDashLoopSound(fe, com.exoticmatter.totm.registry.ModSounds.SAUCER_DASH_LOOP.get());
                        DASH_SOUNDS.put(id, ds);
                        mc.getSoundManager().play(ds);
                    }
                } else {
                    var ds = DASH_SOUNDS.remove(id);
                    if (ds != null) {
                        mc.getSoundManager().stop(ds);
                    }
                }
            }
        }
        ENGINE_SOUNDS.keySet().removeIf(k -> {
            if (!seen.contains(k)) {
                var s = ENGINE_SOUNDS.get(k);
                if (s != null) net.minecraft.client.Minecraft.getInstance().getSoundManager().stop(s);
                return true;
            }
            return false;
        });
        BEAM_SOUNDS.keySet().removeIf(k -> !seen.contains(k));
        DASH_SOUNDS.keySet().removeIf(k -> !seen.contains(k));
    }
}
