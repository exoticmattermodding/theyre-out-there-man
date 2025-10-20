package com.exoticmatter.totm.client;

import com.exoticmatter.totm.client.model.FlyingSaucerModel;
import com.exoticmatter.totm.client.render.FlyingSaucerRenderer;
import com.exoticmatter.totm.client.render.CrashedSaucerRenderer;
import com.exoticmatter.totm.registry.ModSkullTypes;
import com.exoticmatter.totm.registry.ModEntities;
// import com.exoticmatter.totm.registry.ModBlocks;
// import net.minecraft.client.Minecraft;
// import net.minecraft.resources.ResourceLocation;
// import net.minecraft.world.inventory.InventoryMenu;
// import net.minecraft.world.level.block.state.BlockState;
// import net.minecraftforge.client.extensions.common.IClientBlockExtensions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // This line actually defines the model layer, so bakeLayer() can find it
        event.registerLayerDefinition(FlyingSaucerModel.LAYER_LOCATION, FlyingSaucerModel::createBodyLayer);
        // Using vanilla skeleton skull layer baked in renderers
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FLYING_SAUCER.get(), FlyingSaucerRenderer::new);
        event.registerEntityRenderer(ModEntities.GRAY_ALIEN.get(),
                ctx -> new net.minecraft.client.renderer.entity.HumanoidMobRenderer<>(
                        ctx,
                        new net.minecraft.client.model.HumanoidModel<>(ctx.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE)),
                        0.5f
                ) {
                    private final net.minecraft.resources.ResourceLocation TEX = new net.minecraft.resources.ResourceLocation("totm", "textures/entity/gray_alien.png");
                    @Override
                    public net.minecraft.resources.ResourceLocation getTextureLocation(com.exoticmatter.totm.world.entity.GrayAlienEntity e) {
                        return TEX;
                    }
                }
        );

        // Crashed saucer renderer
        event.registerEntityRenderer(ModEntities.CRASHED_SAUCER.get(), CrashedSaucerRenderer::new);

        // Override Cow renderer to use alien skin when tagged
        event.registerEntityRenderer(net.minecraft.world.entity.EntityType.COW,
                ctx -> new net.minecraft.client.renderer.entity.CowRenderer(ctx) {
                    private final net.minecraft.resources.ResourceLocation ALIEN = new net.minecraft.resources.ResourceLocation("totm", "textures/entity/alien_cow.png");
                    @Override
                    public net.minecraft.resources.ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Cow cow) {
                        if (cow.getTags().contains("totm_alien_cow") || cow.getPersistentData().getBoolean("totm_alien_cow")) return ALIEN;
                        return super.getTextureLocation(cow);
                    }
                }
        );

    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        // No-op: texture override handles alien cow appearance
    }

    @SubscribeEvent
    public static void registerSkullModels(EntityRenderersEvent.CreateSkullModels event) {
        // CreateSkullModels does not expose a model set; use Minecraft client model set
        var model = new net.minecraft.client.model.SkullModel(
                net.minecraft.client.Minecraft.getInstance()
                        .getEntityModels()
                        .bakeLayer(net.minecraft.client.model.geom.ModelLayers.SKELETON_SKULL)
        );
        event.registerSkullModel(ModSkullTypes.ALIEN, model);
        net.minecraft.client.renderer.blockentity.SkullBlockRenderer.SKIN_BY_TYPE.put(
                ModSkullTypes.ALIEN,
                new net.minecraft.resources.ResourceLocation("totm", "textures/entity/alien_skull.png")
        );
    }

    // Client particle override removed for 1.20.1 compatibility; particles are supplied via
    // blockstates referencing a simple model with the desired particle texture.

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(com.exoticmatter.totm.registry.ModItems.PROBE.get(),
                    new ResourceLocation("totm", "bound"),
                    (stack, level, entity, seed) -> com.exoticmatter.totm.world.item.ProbeItem.isBound(stack) ? 1.0F : 0.0F);
        });
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.exoticmatter.totm.registry.ModParticles.SPARK.get(),
                com.exoticmatter.totm.client.particle.SparkParticle.Provider::new);
    }

}
