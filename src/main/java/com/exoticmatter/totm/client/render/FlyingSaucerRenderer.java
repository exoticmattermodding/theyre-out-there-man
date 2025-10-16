package com.exoticmatter.totm.client.render;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import com.exoticmatter.totm.client.model.FlyingSaucerModel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;

public class FlyingSaucerRenderer extends EntityRenderer<FlyingSaucerEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("totm", "textures/entity/flying_saucer.png");

    // ---- Tune this to visually line up the model with the 5.0 × 0.8 collider ----
    // Positive lifts the model up; negative lowers it. Units are *blocks*.
    // Start with about -1.6f if your Blockbench model’s geometry sits ~2 blocks above its origin.
    private static final float MODEL_Y_OFFSET_BLOCKS = -1.2f;

    // If your model scale doesn’t match world units, you can tweak these (1 = no scale).
    private static final float MODEL_SCALE = 1.0f;

    private final FlyingSaucerModel<FlyingSaucerEntity> model;

    public FlyingSaucerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.4f; // ~ half of 5-block diameter looks nicer
        this.model = new FlyingSaucerModel<>(ctx.bakeLayer(FlyingSaucerModel.LAYER_LOCATION));
    }

    // Optional: offset the whole render position (camera-space), not physics.
    // This helps keep selection box and model feeling consistent while aiming.
    @Override
    public Vec3 getRenderOffset(FlyingSaucerEntity entity, float partialTicks) {
        // Only vertical offset needed
        return new Vec3(0.0, MODEL_Y_OFFSET_BLOCKS, 0.0);
    }

    @Override
    public void render(FlyingSaucerEntity entity, float entityYaw, float partialTicks,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        pose.pushPose();

        // Additional local offset (kept at 0 because getRenderOffset already applied it).
        // If you prefer not to override getRenderOffset, comment that override out
        // and use: pose.translate(0.0, MODEL_Y_OFFSET_BLOCKS, 0.0);
        // pose.translate(0.0, MODEL_Y_OFFSET_BLOCKS, 0.0);

        // Face the direction the entity is pointing (so spin happens around "facing")
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));

        // Idle spin + gentle wobble around its local axes
        float t = entity.tickCount + partialTicks;
        float wobblePitch = (float) Math.sin(t * 0.07f) * 2.0f;
        float wobbleRoll  = (float) Math.cos(t * 0.06f) * 2.0f;
        float spin        = (t * 0.5f) % 360.0f;

        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.mulPose(Axis.XP.rotationDegrees(wobblePitch));
        pose.mulPose(Axis.ZP.rotationDegrees(wobbleRoll));

        if (MODEL_SCALE != 1.0f) {
            pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        }

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderToBuffer(pose, vc, packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);

        pose.popPose();
        super.render(entity, entityYaw, partialTicks, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FlyingSaucerEntity entity) {
        return TEXTURE;
    }
}
