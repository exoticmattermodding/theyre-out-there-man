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
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

public class FlyingSaucerRenderer extends EntityRenderer<FlyingSaucerEntity> {

    @SuppressWarnings("deprecation")
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("totm", "textures/entity/flying_saucer.png");
    private static final ResourceLocation DAMAGED_TEX =
            new ResourceLocation("totm", "textures/entity/saucer_damaged.png");

    // ---- Tune this to visually line up the model with the 5.0 × 0.8 collider ----
    // Positive lifts the model up; negative lowers it. Units are *blocks*.
    // Start with about -1.6f if your Blockbench model’s geometry sits ~2 blocks above its origin.
    private static final float MODEL_Y_OFFSET_BLOCKS = 1.55f; // raised by additional ~3px (0.1875 blocks)
    // Bottom-most model vertex relative to model origin, in blocks (Blockbench Y=-2 -> -2/16f)
    private static final float MODEL_MIN_Y_BLOCKS = -2.0f / 16.0f;
    // Extra tuning to raise the beam top further into the model underside
    // Beam top sits slightly lower to better meet the saucer underside
    private static final float BEAM_TOP_EXTRA = 2.7f;

    // If your model scale doesn’t match world units, you can tweak these (1 = no scale).
    private static final float MODEL_SCALE = 1.0f;

    private final FlyingSaucerModel<FlyingSaucerEntity> model;

    @SuppressWarnings("deprecation")
    private static final ResourceLocation BEAM_TEX =
            new ResourceLocation("minecraft", "textures/entity/beacon_beam.png");

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

        // Face the direction the entity is pointing
        pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        // Correct upside-down orientation from model export: flip 180° around X
        pose.mulPose(Axis.XP.rotationDegrees(180.0f));

        // Idle spin + gentle wobble; reduce when damaged/crashing and apply a fixed tilt
        float t = entity.tickCount + partialTicks;
        boolean damaged = entity.getPersistentData().getBoolean("totm_damaged") || entity.getPersistentData().getBoolean("totm_crash_started");
        float wobblePitch = damaged ? 1.0f : (float) Math.sin(t * 0.07f) * 2.0f;
        float wobbleRoll  = damaged ? 1.0f : (float) Math.cos(t * 0.06f) * 2.0f;
        float spin        = damaged ? 0.0f : (t * 0.5f) % 360.0f;

        int tiltP = entity.getPersistentData().getInt("totm_tilt_pitch");
        int tiltR = entity.getPersistentData().getInt("totm_tilt_roll");

        // Directional tilt: lean in the direction of movement
        try {
            net.minecraft.world.phys.Vec3 v = entity.getDeltaMovement();
            // Forward unit from current yaw (ignore pitch)
            net.minecraft.world.phys.Vec3 fwd = net.minecraft.world.phys.Vec3.directionFromRotation(0.0f, entity.getYRot());
            fwd = new net.minecraft.world.phys.Vec3(fwd.x, 0.0, fwd.z);
            double fl = fwd.length();
            if (fl < 1.0e-6) fwd = new net.minecraft.world.phys.Vec3(0.0, 0.0, 1.0);
            else fwd = fwd.scale(1.0 / fl);
            net.minecraft.world.phys.Vec3 right = new net.minecraft.world.phys.Vec3(-fwd.z, 0.0, fwd.x);
            double vF = v.x * fwd.x + v.z * fwd.z;    // forward/back speed
            double vR = v.x * right.x + v.z * right.z; // right/left speed
            float gain = damaged ? 6.0f : 10.0f;       // reduce when damaged
            float maxTilt = 12.0f;
            float tiltPitch = net.minecraft.util.Mth.clamp((float)(-vF * gain), -maxTilt, maxTilt);
            float tiltRoll  = net.minecraft.util.Mth.clamp((float)( vR * gain), -maxTilt, maxTilt);
            pose.mulPose(Axis.XP.rotationDegrees(tiltPitch));
            pose.mulPose(Axis.ZP.rotationDegrees(tiltRoll));
        } catch (Throwable ignored) { }

        pose.mulPose(Axis.YP.rotationDegrees(spin));
        if (damaged) {
            pose.mulPose(Axis.XP.rotationDegrees(tiltP));
            pose.mulPose(Axis.ZP.rotationDegrees(tiltR));
        }
        pose.mulPose(Axis.XP.rotationDegrees(wobblePitch));
        pose.mulPose(Axis.ZP.rotationDegrees(wobbleRoll));

        // Local client-only vibration when holding jump (space) while piloting
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            boolean localPilot = mc.player != null
                    && entity.level().isClientSide
                    && mc.level == entity.level()
                    && com.exoticmatter.totm.client.PilotClientState.isPiloting()
                    && com.exoticmatter.totm.client.PilotClientState.getPilotedSaucerId() == entity.getId();
            if (localPilot && mc.options.keyJump.isDown()) {
                float vibPhase = (entity.tickCount + partialTicks) * 16.0f; // fairly snappy
                float vibRot = (float) Math.sin(vibPhase) * 1.5f;           // small rotational shake
                float vibTrans = 0.02f;                                     // ~0.02 blocks lateral sway
                pose.mulPose(Axis.XP.rotationDegrees(vibRot));
                pose.mulPose(Axis.ZP.rotationDegrees(-vibRot));
                pose.translate(
                        Math.sin(vibPhase * 1.7f) * vibTrans,
                        0.0,
                        Math.cos(vibPhase * 1.3f) * vibTrans
                );
            }
        } catch (Throwable ignored) { }

        if (MODEL_SCALE != 1.0f) {
            pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        }

        ResourceLocation tex = entity.getPersistentData().getBoolean("totm_damaged") ? DAMAGED_TEX : TEXTURE;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(tex));
        model.renderToBuffer(pose, vc, packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);

        // Always-on emissive layer (full-bright) for the saucer emissive bits
        VertexConsumer emissive = buffer.getBuffer(RenderType.eyes(new ResourceLocation("totm", "textures/entity/emissive_saucer.png")));
        model.renderToBuffer(pose, emissive, 0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);

        if (entity.isGlowEnabled()) {
            // Otherworldly overlays: toned-down eerie glow + subtle energy shimmer
            float pulse = 0.55f + 0.25f * Mth.sin(t * 0.10f);
            int fullBright = 0xF000F0;

            // Eerie emissive glow pass (teal/green tone), reduced intensity (over emissive base)
            VertexConsumer glow = buffer.getBuffer(RenderType.eyes(new ResourceLocation("totm", "textures/entity/emissive_saucer.png")));
            model.renderToBuffer(pose, glow, fullBright, OverlayTexture.NO_OVERLAY,
                    0.58f, 0.92f, 0.85f, 0.10f * pulse);

            // Slow energy shimmer overlay with slight scroll (lighter alpha)
            float shift = (t * 0.0035f) % 1.0f;
            VertexConsumer swirl = buffer.getBuffer(RenderType.energySwirl(new ResourceLocation("totm", "textures/entity/emissive_saucer.png"), shift, shift));
            model.renderToBuffer(pose, swirl, fullBright, OverlayTexture.NO_OVERLAY,
                    0.50f, 0.85f, 1.00f, 0.05f);
        }

        pose.popPose();

        // Render abduction beam as a beacon-style column (approximation)
        if (entity.isAbductionActive()) {
            int baseY = entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                    Mth.floor(entity.getX()), Mth.floor(entity.getZ()));
            // Align the beam slightly below craft underside; further lower top by 6px (0.375 blocks)
            // Lower the beam top by an additional 5px total now (3px before + 2px now)
            // 5px = 5/16 = 0.3125 blocks
            float beamTopY = (float)(entity.getY() - 0.6875f - 0.375f - 0.3125f);
            float height = beamTopY - baseY;
            if (height > 0.05f) {
                renderAbductionBeam(pose, buffer, entity, baseY, height, partialTicks);
            }
        }
        super.render(entity, entityYaw, partialTicks, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FlyingSaucerEntity entity) {
        return TEXTURE;
    }

    private void renderAbductionBeam(PoseStack pose, MultiBufferSource buffer, FlyingSaucerEntity entity,
                                     int baseY, float height, float partialTicks) {
        float time = (entity.level().getGameTime() + partialTicks) * 0.02f;
        float v0 = -time;
        float v1 = height - time;

        // Translate so y=0 is ground contact
        pose.pushPose();
        pose.translate(0.0, (double) (baseY - entity.getY()), 0.0);
        // Rotate with saucer yaw and idle spin so the beam follows the craft orientation
        float yawDeg = 180.0f - entity.getYRot();
        float spin = ((entity.tickCount + partialTicks) * 0.5f) % 360.0f;
        // Match saucer model spin direction: rotate beam with negative spin to stay in sync
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yawDeg - spin));
        // Base translucent column
        drawBeamLayer(pose, buffer, height, v0, v1, 1.20f, 0.28f);
        // Soft emissive glow overlay
        drawBeamLayerGlow(pose, buffer, height, v0, v1, 1.35f, 0.18f);

        pose.popPose();
    }

    private void drawBeamLayer(PoseStack pose, MultiBufferSource buffer, float height, float v0, float v1,
                               float radius, float alpha) {
        float r = 0.25f, g = 0.95f, b = 0.85f;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(BEAM_TEX));
        var mat = pose.last().pose();
        var nMat = pose.last().normal();

        float x0 = -radius, z0 = -radius;
        float x1 =  radius, z1 =  radius;

        // +X face
        vc.vertex(mat, x1, 0, z0).color(r, g, b, alpha).uv(0.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 1f, 0f, 0f).endVertex();
        vc.vertex(mat, x1, height, z0).color(r, g, b, alpha).uv(0.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 1f, 0f, 0f).endVertex();
        vc.vertex(mat, x1, height, z1).color(r, g, b, alpha).uv(1.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 1f, 0f, 0f).endVertex();
        vc.vertex(mat, x1, 0, z1).color(r, g, b, alpha).uv(1.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 1f, 0f, 0f).endVertex();

        // -X face
        vc.vertex(mat, x0, 0, z1).color(r, g, b, alpha).uv(0.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, -1f, 0f, 0f).endVertex();
        vc.vertex(mat, x0, height, z1).color(r, g, b, alpha).uv(0.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, -1f, 0f, 0f).endVertex();
        vc.vertex(mat, x0, height, z0).color(r, g, b, alpha).uv(1.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, -1f, 0f, 0f).endVertex();
        vc.vertex(mat, x0, 0, z0).color(r, g, b, alpha).uv(1.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, -1f, 0f, 0f).endVertex();

        // +Z face
        vc.vertex(mat, x0, 0, z1).color(r, g, b, alpha).uv(0.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, 1f).endVertex();
        vc.vertex(mat, x0, height, z1).color(r, g, b, alpha).uv(0.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, 1f).endVertex();
        vc.vertex(mat, x1, height, z1).color(r, g, b, alpha).uv(1.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, 1f).endVertex();
        vc.vertex(mat, x1, 0, z1).color(r, g, b, alpha).uv(1.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, 1f).endVertex();

        // -Z face
        vc.vertex(mat, x1, 0, z0).color(r, g, b, alpha).uv(0.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, -1f).endVertex();
        vc.vertex(mat, x1, height, z0).color(r, g, b, alpha).uv(0.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, -1f).endVertex();
        vc.vertex(mat, x0, height, z0).color(r, g, b, alpha).uv(1.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, -1f).endVertex();
        vc.vertex(mat, x0, 0, z0).color(r, g, b, alpha).uv(1.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, -1f).endVertex();
    }
    private void drawBeamLayerGlow(PoseStack pose, MultiBufferSource buffer, float height, float v0, float v1,
                                   float radius, float alpha) {
        float r = 0.50f, g = 1.00f, b = 0.95f;
        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(BEAM_TEX));
        var mat = pose.last().pose();
        var nMat = pose.last().normal();

        float x0 = -radius, z0 = -radius;
        float x1 =  radius, z1 =  radius;

        // +X face
        vc.vertex(mat, x1, 0, z0).color(r, g, b, alpha).uv(0.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 1f, 0f, 0f).endVertex();
        vc.vertex(mat, x1, height, z0).color(r, g, b, alpha).uv(0.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 1f, 0f, 0f).endVertex();
        vc.vertex(mat, x1, height, z1).color(r, g, b, alpha).uv(1.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 1f, 0f, 0f).endVertex();
        vc.vertex(mat, x1, 0, z1).color(r, g, b, alpha).uv(1.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 1f, 0f, 0f).endVertex();

        // -X face
        vc.vertex(mat, x0, 0, z1).color(r, g, b, alpha).uv(0.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, -1f, 0f, 0f).endVertex();
        vc.vertex(mat, x0, height, z1).color(r, g, b, alpha).uv(0.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, -1f, 0f, 0f).endVertex();
        vc.vertex(mat, x0, height, z0).color(r, g, b, alpha).uv(1.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, -1f, 0f, 0f).endVertex();
        vc.vertex(mat, x0, 0, z0).color(r, g, b, alpha).uv(1.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, -1f, 0f, 0f).endVertex();

        // +Z face
        vc.vertex(mat, x0, 0, z1).color(r, g, b, alpha).uv(0.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, 1f).endVertex();
        vc.vertex(mat, x0, height, z1).color(r, g, b, alpha).uv(0.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, 1f).endVertex();
        vc.vertex(mat, x1, height, z1).color(r, g, b, alpha).uv(1.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, 1f).endVertex();
        vc.vertex(mat, x1, 0, z1).color(r, g, b, alpha).uv(1.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, 1f).endVertex();

        // -Z face
        vc.vertex(mat, x1, 0, z0).color(r, g, b, alpha).uv(0.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, -1f).endVertex();
        vc.vertex(mat, x1, height, z0).color(r, g, b, alpha).uv(0.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, -1f).endVertex();
        vc.vertex(mat, x0, height, z0).color(r, g, b, alpha).uv(1.0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, -1f).endVertex();
        vc.vertex(mat, x0, 0, z0).color(r, g, b, alpha).uv(1.0f, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nMat, 0f, 0f, -1f).endVertex();
    }
}
