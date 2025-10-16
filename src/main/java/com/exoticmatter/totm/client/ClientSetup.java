package com.exoticmatter.totm.client;

import com.exoticmatter.totm.client.model.FlyingSaucerModel;
import com.exoticmatter.totm.client.render.FlyingSaucerRenderer;
import com.exoticmatter.totm.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // This line actually defines the model layer, so bakeLayer() can find it
        event.registerLayerDefinition(FlyingSaucerModel.LAYER_LOCATION, FlyingSaucerModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FLYING_SAUCER.get(), FlyingSaucerRenderer::new);
    }
}
