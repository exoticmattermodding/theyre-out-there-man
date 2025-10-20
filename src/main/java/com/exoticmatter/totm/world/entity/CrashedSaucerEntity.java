package com.exoticmatter.totm.world.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class CrashedSaucerEntity extends Entity {
    // Client VFX ticker to spread effects and reduce churn
    private int clientVfxTicker = 0;

    public CrashedSaucerEntity(EntityType<? extends CrashedSaucerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        // Update bounding box to our custom dimensions approximation
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        // Allow gravity and vanilla physics to act on the wreck.
        // Apply a little ground friction to settle the wreck when it lands.
        if (this.onGround()) {
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x * 0.6, v.y * 0.0, v.z * 0.6);
        }

        if (!level().isClientSide && this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            // Electrical/logic only on server; ambient visuals are client-only below
        
            // Occasionally spawn a Gray Alien near the wreck
            var tag = this.getPersistentData();
            long now = sl.getGameTime();
            long windowUntil = tag.getLong("totm_spawn_window_until");
            int spawned = tag.getInt("totm_spawned_aliens");
            long next = tag.getLong("totm_alien_next");
            // During the post-crash window, try to spawn up to 2 gray aliens near the wreck
            if (spawned < 2 && now >= next && (windowUntil == 0L || now <= windowUntil)) {
                var nearby = sl.getEntitiesOfClass(com.exoticmatter.totm.world.entity.GrayAlienEntity.class,
                        this.getBoundingBox().inflate(24, 12, 24));
                if (nearby.size() < 3) { // avoid overcrowding
                    var type = com.exoticmatter.totm.registry.ModEntities.GRAY_ALIEN.get();
                    var alien = type.create(sl);
                    if (alien != null) {
                        double r = 4.0 + this.random.nextDouble() * 4.0;
                        double ang = this.random.nextDouble() * Math.PI * 2.0;
                        double ax = this.getX() + Math.cos(ang) * r;
                        double az = this.getZ() + Math.sin(ang) * r;
                        int gy = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                                net.minecraft.util.Mth.floor(ax), net.minecraft.util.Mth.floor(az));
                        double ay = Math.max(gy + 0.2, sl.getMinBuildHeight() + 1);
                        alien.moveTo(ax, ay, az, this.getYRot(), 0.0f);
                        if (this.random.nextBoolean()) {
                            var probe = com.exoticmatter.totm.registry.ModItems.PROBE.get();
                            if (probe != null) {
                                alien.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(probe));
                            }
                        }
                        sl.addFreshEntity(alien);
                        spawned++;
                        tag.putInt("totm_spawned_aliens", spawned);
                    }
                }
                // Schedule next attempt soon: 3–10s, but stop after window or after two spawns
                if (spawned < 2 && (windowUntil == 0L || now <= windowUntil)) {
                    tag.putLong("totm_alien_next", now + (20L * (3 + this.random.nextInt(8))));
                } else {
                    tag.remove("totm_alien_next");
                }
            }

            // Auto-consume disabled: repairs are applied via player right-click with Alien Tech only

            if (tag.getInt("totm_repair_parts") >= 4) {
                // Spawn a fresh saucer and transfer ownership
                var type = com.exoticmatter.totm.registry.ModEntities.FLYING_SAUCER.get();
                var saucer = type.create(sl);
                if (saucer != null) {
                    saucer.moveTo(this.getX(), Math.max(this.getY() + 6.0, this.level().getMinBuildHeight() + 2), this.getZ(), this.getYRot(), 0.0f);
                    // Owner and persistence
                    var nearest = sl.getNearestPlayer(this.getX(), this.getY(), this.getZ(), 16.0, false);
                    if (nearest != null) {
                        saucer.getPersistentData().putUUID("totm_owner", nearest.getUUID());
                        saucer.setPersistenceRequired();
                    }
                    // Ensure not in encounter or crash flows
                    saucer.getPersistentData().remove("totm_encounter");
                    // Full energy on repair
                    saucer.addDarkMatter(saucer.getMaxDarkMatter());
                    saucer.setGlowEnabled(false);
                    saucer.setAbductionActive(false);
                    sl.addFreshEntity(saucer);
                }
                this.discard();
                return;
            }
        }
    }

    @Override
    public void push(Entity e) {
        super.push(e);
        if (e == null) return;
        // Simulate a tilted disc surface so entities stand and slide realistically
        // Hitbox is still AABB, but we nudge entities toward a rotated support plane.
        final double radius = 2.5; // matches 5.0 sized width
        double dx = e.getX() - this.getX();
        double dz = e.getZ() - this.getZ();
        double dist2 = dx*dx + dz*dz;
        if (dist2 > (radius + 0.6)*(radius + 0.6)) return; // outside influence

        // Local right vector based on saucer yaw; we use it as the upslope direction
        double yawRad = Math.toRadians(this.getYRot());
        double rx = Math.cos(yawRad + Math.PI / 2.0);
        double rz = Math.sin(yawRad + Math.PI / 2.0);

        // Surface plane: y = base + slope * projection along right vector
        // 45-degree tilt -> slope ~1 block rise per block along right vector
        double proj = dx * rx + dz * rz;
        double base = this.getY();
        double surfaceY = base + proj; // simple 45° plane through entity center

        double ey = e.getY();
        boolean belowSurface = ey < surfaceY + 0.1;

        // Gently push outward to avoid clipping inside the disc
        double len2 = dx*dx + dz*dz;
        if (len2 > 1.0e-6) {
            double inv = 1.0 / Math.sqrt(len2);
            double push = 0.02;
            e.setDeltaMovement(e.getDeltaMovement().add(dx * inv * push, 0.0, dz * inv * push));
        }

        // Lift entities up to the support plane when they intersect it
        if (belowSurface) {
            // Add upward velocity and snap slightly if deeply embedded
            double dy = (surfaceY + 0.05) - ey;
            double addVy = Math.max(0.12, Math.min(0.3, dy));
            e.setDeltaMovement(e.getDeltaMovement().x, Math.max(e.getDeltaMovement().y, addVy), e.getDeltaMovement().z);
            e.hurtMarked = true;
        }
    }

    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (this.level().isClientSide) return net.minecraft.world.InteractionResult.sidedSuccess(true);
        var held = player.getItemInHand(hand);
        boolean creative = player.getAbilities().instabuild; // true in creative mode
        boolean hasTech = held.is(com.exoticmatter.totm.registry.ModItems.ALIEN_TECH.get());
        // Require Alien Tech in hand for repairs (creative does not bypass holding)
        if (!hasTech) return net.minecraft.world.InteractionResult.PASS;
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return net.minecraft.world.InteractionResult.PASS;

        var tag = this.getPersistentData();
        int parts = tag.getInt("totm_repair_parts");
        if (parts < 4 && (creative || held.getCount() > 0)) {
            // In creative, do not consume the item; otherwise consume one Alien Tech
            if (!creative) held.shrink(1);
            parts++;
            tag.putInt("totm_repair_parts", parts);
            // Electrical-like feedback
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 16, 0.3, 0.2, 0.3, 0.03);
            sl.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1.4f);
        }

        if (parts >= 4) {
            // Spawn repaired saucer immediately (same as tick path)
            var type = com.exoticmatter.totm.registry.ModEntities.FLYING_SAUCER.get();
            var saucer = type.create(sl);
            if (saucer != null) {
                saucer.moveTo(this.getX(), Math.max(this.getY() + 6.0, this.level().getMinBuildHeight() + 2), this.getZ(), this.getYRot(), 0.0f);
                // Owner and persistence
                var nearest = sl.getNearestPlayer(this.getX(), this.getY(), this.getZ(), 16.0, false);
                if (nearest != null) {
                    saucer.getPersistentData().putUUID("totm_owner", nearest.getUUID());
                    saucer.setPersistenceRequired();
                }
                saucer.getPersistentData().remove("totm_encounter");
                saucer.addDarkMatter(saucer.getMaxDarkMatter());
                saucer.setGlowEnabled(false);
                saucer.setAbductionActive(false);
                sl.addFreshEntity(saucer);
            }
            this.discard();
        }

        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // Approximate the tilted disc by increasing the vertical extent of the AABB.
    // Note: Entity hitboxes in Minecraft are axis‑aligned and cannot be rotated.
    // This returns a taller box so interaction/selection better envelopes the visible tilt.
    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        // Base disc diameter ~5.0; visual tilt is ~45°. Projected vertical span ~1.8–2.0 blocks.
        // Keep width consistent with registration and increase height modestly for gameplay.
        final float width = 5.0f;
        final float height = 2.0f; // from 1.0f previously; better matches the visible angled model
        return net.minecraft.world.entity.EntityDimensions.scalable(width, height);
    }

    // Client-side SPARK particles to avoid network warnings if sprite missing
    @Override
    public void baseTick() {
        super.baseTick();
        if (this.level().isClientSide) {
            // Throttled ambient VFX: campfire smoke + flame + more sparks in an arc
            clientVfxTicker++;
            final double x = this.getX();
            final double y = this.getY();
            final double z = this.getZ();

            // Campfire smoke plume
            if (clientVfxTicker % 3 == 0) {
                double sx = (this.random.nextDouble() - 0.5) * 0.9; // spread a bit more
                double sz = (this.random.nextDouble() - 0.5) * 0.9;
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        x + sx, y + 0.1 + this.random.nextDouble() * 0.5, z + sz,
                        0.0, 0.02, 0.0);
            }

            // Small flame less often
            if (clientVfxTicker % 7 == 0) {
                double fx = (this.random.nextDouble() - 0.5) * 0.7;
                double fz = (this.random.nextDouble() - 0.5) * 0.7;
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,
                        x + fx, y - 0.15 + this.random.nextDouble() * 0.2, z + fz,
                        0.0, 0.005, 0.0);
            }

            // Custom sparks in an arc pattern, more frequent
            if (clientVfxTicker % 2 == 0) {
                int pts = 10;
                double baseAng = (clientVfxTicker % 360) * 0.05;
                for (int i = 0; i < pts; i++) {
                    double ang = baseAng + i * (Math.PI / (pts - 1)); // arc across ~180 degrees
                    double rad = 0.6 + this.random.nextDouble() * 0.6;
                    double ox = Math.cos(ang) * rad;
                    double oz = Math.sin(ang) * rad;
                    double vy = 0.06 + this.random.nextDouble() * 0.04;
                    double vx = (this.random.nextDouble() - 0.5) * 0.02;
                    double vz = (this.random.nextDouble() - 0.5) * 0.02;
                    this.level().addParticle(com.exoticmatter.totm.registry.ModParticles.SPARK.get(),
                            x + ox, y + 0.25 + this.random.nextDouble() * 0.2, z + oz,
                            vx, vy, vz);
                }
            }
        }
    }
}
