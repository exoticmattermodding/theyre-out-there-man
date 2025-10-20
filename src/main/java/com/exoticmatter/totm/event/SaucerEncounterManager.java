package com.exoticmatter.totm.event;

import com.exoticmatter.totm.registry.ModEntities;
import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Mod.EventBusSubscriber(modid = "totm")
public class SaucerEncounterManager {

    private static class DayPlan {
        public long lastDay = Long.MIN_VALUE;
        public boolean planned = false;
        public int plannedTickInDay = -1; // 0..23999
    }

    private static final Map<ResourceKey<Level>, DayPlan> PLANS = new HashMap<>();
    private static final Random RNG = new Random();

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel sl)) return;

        ResourceKey<Level> dim = sl.dimension();
        DayPlan plan = PLANS.computeIfAbsent(dim, k -> new DayPlan());

        long time = sl.getDayTime(); // total ticks
        long day = time / 24000L;
        int tickInDay = (int)(time % 24000L);

        if (day != plan.lastDay) {
            plan.lastDay = day;
            plan.planned = false;
            plan.plannedTickInDay = -1;
            // Daily plan: base 50%, increased chance with thunder/dusk
            float chance = 0.5f;
            if (sl.isThundering()) chance = 0.9f;
            // If dusk is coming, slightly bias to plan
            if (tickInDay >= 11800 && tickInDay <= 14000) chance = Math.max(chance, 0.75f);
            if (RNG.nextFloat() < chance) {
                plan.planned = true;
                // Choose a random time in the day (avoid the very start to ensure players are loaded)
                // If thundering, bias to spawn soon
                if (sl.isThundering()) {
                    plan.plannedTickInDay = Math.min(23900, tickInDay + 60 + RNG.nextInt(200));
                } else if (tickInDay >= 11800 && tickInDay <= 14000) {
                    plan.plannedTickInDay = Math.min(23900, tickInDay + 80 + RNG.nextInt(200));
                } else {
                    plan.plannedTickInDay = 2000 + RNG.nextInt(20000);
                }
            }
        }

        // While not planned: opportunistic spawn during thunder or dusk windows
        if (!plan.planned) {
            boolean duskWindow = tickInDay >= 11800 && tickInDay <= 14000;
            if (sl.isThundering() && RNG.nextFloat() < 0.05f) {
                plan.planned = true;
                plan.plannedTickInDay = Math.min(23900, tickInDay + 40 + RNG.nextInt(120));
            } else if (duskWindow && RNG.nextFloat() < 0.02f) {
                plan.planned = true;
                plan.plannedTickInDay = Math.min(23900, tickInDay + 60 + RNG.nextInt(200));
            }
        }

        if (!plan.planned) return;

        // Within a small window around the planned time, try to spawn one encounter
        if (Math.abs(tickInDay - plan.plannedTickInDay) <= 5) {
            plan.planned = false; // consume plan
            trySpawnEncounter(sl);
        }
    }

    private static void trySpawnEncounter(ServerLevel sl) {
        if (sl.players().isEmpty()) return;
        // Pick a random player as anchor
        Player p = sl.players().get(RNG.nextInt(sl.players().size()));
        double dist = 96.0 + RNG.nextDouble() * 64.0; // 96..160 blocks away
        double ang = RNG.nextDouble() * Math.PI * 2.0;
        double sx = p.getX() + Math.cos(ang) * dist;
        double sz = p.getZ() + Math.sin(ang) * dist;
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sx), Mth.floor(sz));
        double sy = Math.max(gy + 20.0, p.getY() + 10.0);

        var type = ModEntities.FLYING_SAUCER.get();
        FlyingSaucerEntity saucer = type.create(sl);
        if (saucer == null) return;
        saucer.moveTo(sx, sy, sz, (float)(RNG.nextDouble() * 360.0), 0.0f);

        // Mark as encounter; store an anchor around the chosen player
        var tag = saucer.getPersistentData();
        tag.putBoolean("totm_encounter", true);
        tag.putDouble("totm_enc_anchor_x", p.getX());
        tag.putDouble("totm_enc_anchor_z", p.getZ());
        tag.putInt("totm_enc_stage", 0);   // 0 patrol, 1 land/deploy, 2 await, 3 depart
        tag.putInt("totm_enc_timer", 20 * (25 + RNG.nextInt(15))); // 25-40s patrol before landing
        tag.putInt("totm_enc_mode", RNG.nextInt(3)); // 0: cross-view patrol, 1: loiter, 2: hover+ascend
        if (sl.isThundering()) {
            tag.putBoolean("totm_crash_pending", true);
        }

        sl.addFreshEntity(saucer);
        // Default visuals: no glow, beam off
        saucer.setGlowEnabled(false);
        saucer.setAbductionActive(false);
    }
}
