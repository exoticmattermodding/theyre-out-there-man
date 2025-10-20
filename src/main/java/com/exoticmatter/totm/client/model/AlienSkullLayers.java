package com.exoticmatter.totm.client.model;

import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public class AlienSkullLayers {
    public static final ModelLayerLocation ALIEN_SKULL = new ModelLayerLocation(new ResourceLocation("totm", "alien_skull"), "main");

    // 64x32 texture layout compatible with skeleton/wither skull textures
    public static LayerDefinition createAlienSkullLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Head cube 8x8x8 at origin, UV at (0,0)
        root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        // No overlay/jaw; keep it simple like vanilla SkullModel base
        return LayerDefinition.create(mesh, 64, 32);
    }
}

