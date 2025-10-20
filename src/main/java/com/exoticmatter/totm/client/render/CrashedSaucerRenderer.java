package com.exoticmatter.totm.client.render;

import com.exoticmatter.totm.client.model.FlyingSaucerModel;
import com.exoticmatter.totm.world.entity.CrashedSaucerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;

public class CrashedSaucerRenderer extends EntityRenderer<CrashedSaucerEntity> {

    private static final ResourceLocation DAMAGED_TEX = new ResourceLocation("totm", "textures/entity/saucer_damaged.png");
    private static final float MODEL_Y_OFFSET_BLOCKS = 1.55f;
    private static final float MODEL_SCALE = 1.0f;

    private final FlyingSaucerModel<CrashedSaucerEntity> model;

    public CrashedSaucerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.4f;
        this.model = new FlyingSaucerModel<>(ctx.bakeLayer(com.exoticmatter.totm.client.model.FlyingSaucerModel.LAYER_LOCATION));
    }

    @Override
    public void render(CrashedSaucerEntity entity, float entityYaw, float partialTicks, PoseStack pose, MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        // Offset to align model with collider
        pose.translate(0.0, MODEL_Y_OFFSET_BLOCKS, 0.0);

        // Orient to yaw, then apply a fixed 45-degree crash tilt
        pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        pose.mulPose(Axis.XP.rotationDegrees(180.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0f));

        if (MODEL_SCALE != 1.0f) pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(DAMAGED_TEX));
        model.renderToBuffer(pose, vc, packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);

        pose.popPose();

        super.render(entity, entityYaw, partialTicks, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CrashedSaucerEntity entity) {
        return DAMAGED_TEX;
    }
}

