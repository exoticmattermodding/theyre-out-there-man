package com.exoticmatter.totm.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GlowClientOptions {

    // Key registration must be on MOD bus; dispatch through a nested class
    @Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_GLOW);
            event.register(TOGGLE_BEAM);
        }
    }

    private static final KeyMapping TOGGLE_GLOW = new KeyMapping(
            "key.totm.toggle_glow",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.totm"
    );

    public static final KeyMapping TOGGLE_BEAM = new KeyMapping(
            "key.totm.toggle_beam",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.totm"
    );

    public static boolean isEerieGlowEnabled() {
        // Deprecated: global flag removed; renderer reads per-entity state
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().player == null) return;
        if (TOGGLE_GLOW.consumeClick()) {
            if (com.exoticmatter.totm.client.PilotClientState.isPiloting()) {
                int id = com.exoticmatter.totm.client.PilotClientState.getPilotedSaucerId();
                var mc = Minecraft.getInstance();
                var e = mc.level != null ? mc.level.getEntity(id) : null;
                boolean next = true;
                if (e instanceof com.exoticmatter.totm.world.entity.FlyingSaucerEntity s) {
                    next = !s.isGlowEnabled();
                }
                com.exoticmatter.totm.network.ModNetwork.sendToServer(new com.exoticmatter.totm.network.packet.ToggleGlowC2SPacket(id));
                // Disable client overlay text for glow toggle

                // Pairing gesture: double-tap glow while looking at another saucer
                tryPairWithLookTarget(id);
            }
        }
    }

    // --- Pairing detection (double-tap) ---
    private static long lastGlowTick = -1000;
    private static int lastLookTarget = -1;
    private static int pressCount = 0;
    private static final int DOUBLE_TAP_WINDOW_TICKS = 12; // ~0.6s

    private static void tryPairWithLookTarget(int leaderId) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long now = mc.level.getGameTime();

        int target = findLookedSaucerId(64.0);
        if (now - lastGlowTick <= DOUBLE_TAP_WINDOW_TICKS && target != -1 && target == lastLookTarget) {
            pressCount++;
        } else {
            pressCount = 1;
        }
        lastGlowTick = now;
        lastLookTarget = target;

        if (pressCount >= 2 && target != -1) {
            // Send pairing request to server; target will blink twice to confirm
            com.exoticmatter.totm.network.ModNetwork.sendToServer(
                    new com.exoticmatter.totm.network.packet.PairSaucerC2SPacket(leaderId, target));
            pressCount = 0; // reset
        }
    }

    private static int findLookedSaucerId(double maxDist) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return -1;
        var eye = mc.player.getEyePosition(1.0f);
        var look = mc.player.getViewVector(1.0f).normalize();
        double bestScore = 0.0;
        int bestId = -1;
        double cosThresh = Math.cos(Math.toRadians(6.0)); // within ~6 degrees of crosshair
        for (var ent : mc.level.entitiesForRendering()) {
            if (!(ent instanceof com.exoticmatter.totm.world.entity.FlyingSaucerEntity)) continue;
            if (ent.getId() == com.exoticmatter.totm.client.PilotClientState.getPilotedSaucerId()) continue;
            var to = new net.minecraft.world.phys.Vec3(ent.getX(), ent.getY() + ent.getBbHeight() * 0.5, ent.getZ()).subtract(eye);
            double dist = to.length();
            if (dist > maxDist || dist < 0.001) continue;
            var dir = to.scale(1.0 / dist);
            double dot = dir.dot(look);
            if (dot >= cosThresh && dot > bestScore) {
                bestScore = dot;
                bestId = ent.getId();
            }
        }
        return bestId;
    }
}
