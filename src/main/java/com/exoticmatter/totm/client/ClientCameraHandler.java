package com.exoticmatter.totm.client;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientCameraHandler {

    // Capture yaw/pitch for use when computing final camera position
    private static float LAST_YAW = 0f, LAST_PITCH = 0f;

    // Record yaw/pitch; do not modify position here
    @SubscribeEvent
    public static void onComputeAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        var cam = event.getCamera();
        if (!(cam.getEntity() instanceof FlyingSaucerEntity)) return;
        LAST_YAW = event.getYaw();
        LAST_PITCH = event.getPitch();
    }

    // No position override API available in this Forge target; we avoid collision by forcing first-person

    // No additional position override; forcing first-person avoids collision artifacts
}
