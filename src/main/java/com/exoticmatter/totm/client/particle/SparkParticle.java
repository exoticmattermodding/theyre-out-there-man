package com.exoticmatter.totm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class SparkParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected SparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        try {
            this.setSpriteFromAge(sprites);
        } catch (Throwable t) {
            // Sprite set not ready (e.g., missing atlas frame during init); skip to avoid crash
        }

        // Behavior similar to lava pops: brief life, upward motion, light gravity, damping
        this.gravity = 0.06F;
        this.lifetime = 18 + this.random.nextInt(8);
        this.quadSize = 0.12F + this.random.nextFloat() * 0.06F;
        this.hasPhysics = true;

        // Initial velocity from emitter
        this.xd = vx * 0.8 + (this.random.nextDouble() - 0.5) * 0.02;
        this.yd = Math.max(0.02, vy * 0.9 + 0.08 + this.random.nextDouble() * 0.04);
        this.zd = vz * 0.8 + (this.random.nextDouble() - 0.5) * 0.02;

        // Neutral white so texture tint does not skew color
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.lifetime-- <= 0) {
            this.remove();
            return;
        }
        // Apply gravity
        this.yd -= this.gravity;
        // Move
        this.move(this.xd, this.yd, this.zd);
        // Damping similar to lava particle
        float damp = 0.86F;
        this.xd *= damp;
        this.yd *= 0.9F;
        this.zd *= damp;

        // Pop when hitting ground
        if (this.onGround) {
            this.remove();
        }

        // Animate sprite by age (guard if sprite set not yet populated)
        try {
            this.setSpriteFromAge(this.sprites);
        } catch (Throwable t) {
            // ignore if sprite frames are not available yet
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        // Slightly emissive look
        int i = super.getLightColor(partialTick);
        int j = 240;
        int k = i & 0xFF;
        int l = (i >> 16) & 0xFF;
        k = Math.max(k, j);
        return (l << 16) | k;
    }

    @Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        // Guard: if sprite not ready yet, skip rendering this frame to avoid NPE inside parent
        if (this.sprite == null) return;
        super.render(buffer, camera, partialTicks);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            try {
                return new SparkParticle(level, x, y, z, vx, vy, vz, sprites);
            } catch (Throwable t) {
                // Sprite set not ready; skip spawning to avoid crashes
                return null;
            }
        }
    }
}
