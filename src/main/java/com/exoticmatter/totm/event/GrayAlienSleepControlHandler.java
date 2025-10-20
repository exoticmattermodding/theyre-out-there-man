package com.exoticmatter.totm.event;

import com.exoticmatter.totm.world.entity.GrayAlienEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm")
public class GrayAlienSleepControlHandler {

    @SubscribeEvent
    public static void onPlayerWake(net.minecraftforge.event.entity.player.PlayerWakeUpEvent event) {
        var player = event.getEntity();
        var level = player.level();
        if (level.isClientSide) return;
        // If a Gray Alien is nearby, prevent waking; keep the player sleeping
        GrayAlienEntity nearby = level.getNearestEntity(
                GrayAlienEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight().range(16.0),
                null,
                player.getX(), player.getY(), player.getZ(),
                player.getBoundingBox().inflate(16.0)
        );
        if (nearby != null) {
            if (event.isCancelable()) event.setCanceled(true);
        }
    }
}

