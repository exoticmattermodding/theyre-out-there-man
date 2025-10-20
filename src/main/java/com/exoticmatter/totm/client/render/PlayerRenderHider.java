package com.exoticmatter.totm.client.render;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import com.exoticmatter.totm.client.PilotClientState;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerRenderHider {
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        boolean ridingSaucer = player.getVehicle() instanceof FlyingSaucerEntity;
        boolean localPiloting = player.level().isClientSide && player == net.minecraft.client.Minecraft.getInstance().player && PilotClientState.isPiloting();
        // Only hide local player while actively piloting and a valid saucer entity exists
        if (ridingSaucer || (localPiloting && net.minecraft.client.Minecraft.getInstance().level != null &&
                net.minecraft.client.Minecraft.getInstance().level.getEntity(PilotClientState.getPilotedSaucerId()) != null)) {
            event.setCanceled(true); // hide player model entirely while mounted
        }
    }
}
