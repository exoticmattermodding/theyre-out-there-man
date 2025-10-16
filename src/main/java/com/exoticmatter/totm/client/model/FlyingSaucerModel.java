package com.exoticmatter.totm.client.model; // Made with Blockbench 4.9.4

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class FlyingSaucerModel<T extends Entity> extends EntityModel<T> {
	// Bake with EntityRendererProvider.Context in the renderer
	public static final ModelLayerLocation LAYER_LOCATION =
			new ModelLayerLocation(new ResourceLocation("totm", "flying_saucer"), "main");

	private final ModelPart flying_saucer;

	public FlyingSaucerModel(ModelPart root) {
		this.flying_saucer = root.getChild("flying_saucer");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition root = meshdefinition.getRoot();

		// NOTE:
		// - Removed CubeDeformation(1.0F) -> 0.0F to keep UVs exact.
		// - Swapped Y of the two 40x2x40 plates so "top" / "bottom" align with Blockbench.

		PartDefinition flying_saucer = root.addOrReplaceChild(
				"flying_saucer",
				CubeListBuilder.create()
						// large disk (was 80x1x80 with +1 inflation); keep geometry but no inflation
						.texOffs(0, 0).addBox(-40.0F, 0.0F, -40.0F, 80.0F, 1.0F, 80.0F, new CubeDeformation(0.0F))
						// TOP plate (swap to y = -5 -> y = 0 if it looked inverted)
						.texOffs(120, 83).addBox(-20.0F, 1.0F, -20.0F, 40.0F, 2.0F, 40.0F, new CubeDeformation(0.0F))
						// BOTTOM plate (swap to y = -5)
						.texOffs(0, 81).addBox(-20.0F, -2.0F, -20.0F, 40.0F, 2.0F, 40.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 24.0F, 0.0F)
		);

		// Make sure your PNG is exactly 320x125 (or change these numbers to match the PNG)
		return LayerDefinition.create(meshdefinition, 320, 125);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		// no-op: animations handled in renderer transform
	}

	@Override
	public void renderToBuffer(PoseStack pose, VertexConsumer vc, int packedLight, int packedOverlay,
							   float r, float g, float b, float a) {
		flying_saucer.render(pose, vc, packedLight, packedOverlay, r, g, b, a);
	}
}
