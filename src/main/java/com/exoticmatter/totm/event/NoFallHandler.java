package com.exoticmatter.totm.event;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NoFallHandler {
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        var ent = event.getEntity();
        var level = ent.level();
        if (level == null || level.isClientSide) return;

        var tag = ent.getPersistentData();
        long until = tag.getLong("totm_no_fall_until");
        if (until > 0 && level.getGameTime() <= until) {
            event.setDistance(0);
            event.setDamageMultiplier(0);
        }
    }
}
