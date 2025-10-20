package com.exoticmatter.totm.world.entity;

import com.exoticmatter.totm.network.ModNetwork;
import com.exoticmatter.totm.network.packet.SaucerJumpPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.PlayerRideableJumping;

public class FlyingSaucerEntity extends Mob implements PlayerRideableJumping {

    private static final double HOVER_OFFSET_IDLE = 4.0;
    private static final double HOVER_OFFSET_RIDDEN = 20.0;

    private static final double HOVER_STRENGTH_RIDDEN = 0.18;
    private static final double HOVER_STRENGTH_IDLE = 0.12;
    private static final double HOVER_DAMP = 0.86;
    private static final double MAX_DESCENT_SPEED = 0.25; // faster descent

    private static final double BOB_AMPLITUDE_BASE = 0.02;
    private static final double BOB_SPEED = 0.12;
    private static final int REV_WOBBLE_TICKS = 10;
    private static final double REV_WOBBLE_MULT = 7.0;

    // Jump cooldown to prevent immediate re-dash
    private static final int JUMP_COOLDOWN_TICKS = 0; // no enforced cooldown between dashes

    // Launch physics (no teleport): distance proportional to jump bar
    private static final int BURN_TICKS_MIN = 60;   // minimum dash ~3 seconds
    private static final int BURN_TICKS_MAX = 80;   // allow slightly longer window
    private static final double DIST_MIN = 32.0; // much longer dash at low charge
    private static final double DIST_MAX = 128.0; // dramatically increased max distance
    private static final double ABS_SPEED_CAP = 4.50;  // higher cap to match longer steps
    private static final double POST_BURN_DRAG = 0.90;

    private static final int SCUFFS_PER_TICK = 10;
    private static final int ABDUCT_COOLDOWN_TICKS = 60; // ~3 seconds
    private static final int ABDUCT_MAX_TICKS = 12 * 20; // 12 seconds max beam time

    private int jumpCooldown = 0;
    private int abductionCooldown = 0;
    private int abductionActiveTicks = 0;

    private int boostTicks = 0;
    private Vec3 boostDir = Vec3.ZERO;     // normalized 3D aim
    private Vec3 boostStep = Vec3.ZERO;     // per-tick displacement during burn
    private double targetFwdSpeed = 0.0;       // magnitude of boostStep

    private int revWobbleLeft = 0;

    private final float wobblePhase;

    private Vec3 lastDashDir = new Vec3(0, 0, 1);
    private int clientLastJumpPower = 0;
    private boolean noClipBurn = false; // enable no-physics during burn for guaranteed motion
    private int dashingPlayerId = -1;   // server: temporarily ejected rider id during dash
    private float pilotYaw = 0f;
    private float pilotPitch = 0f;
    private float pilotStrafe = 0f;
    private float pilotForward = 0f;
    private double filteredGroundY = Double.NaN;
    private boolean pilotSneak = false;
    // Formation state for unoccupied saucers
    private int formationLeaderId = -1; // server authoritative
    private int formationSlot = -1;     // 0..2 for wing positions, -1 none
    private long formationExpireAt = 0; // game time expiry unless refreshed

    // Abduction state (synced to clients)
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> ABDUCTING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FlyingSaucerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> GLOW_ENABLED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FlyingSaucerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DASHING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FlyingSaucerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final double ABDUCT_RADIUS = 1.5; // blocks
    private static final double ABDUCT_LIFT_PER_TICK = 0.35;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DM_CUR =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FlyingSaucerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DM_MAX =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FlyingSaucerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final int DM_MAX_DEFAULT = 1000;

    public FlyingSaucerEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.wobblePhase = (float) (this.getRandom().nextFloat() * (float) (Math.PI * 2));
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.8D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ABDUCTING, Boolean.FALSE);
        this.entityData.define(GLOW_ENABLED, Boolean.FALSE);
        this.entityData.define(DM_CUR, DM_MAX_DEFAULT);
        this.entityData.define(DM_MAX, DM_MAX_DEFAULT);
        this.entityData.define(DASHING, Boolean.FALSE);
    }

    @Override
    public void tick() {
        super.tick();
        if (jumpCooldown > 0) jumpCooldown--;
        if (abductionCooldown > 0) abductionCooldown--; // legacy: no longer used for gating
        // Enforce maximum abduction beam duration
        if (isAbductionActive()) {
            abductionActiveTicks++;
            if (abductionActiveTicks >= ABDUCT_MAX_TICKS) {
                setAbductionActive(false);
                abductionActiveTicks = 0;
            }
            // Drain Dark Matter while beam is active (server) — significantly higher cost
            if (!level().isClientSide) {
                addDarkMatter(-6);
                if (getDarkMatter() <= 0) setAbductionActive(false);
            }
        } else {
            abductionActiveTicks = 0;
        }
        if (revWobbleLeft > 0) revWobbleLeft--;

        // No server-side pilot teleport during possession; camera is set to saucer entity client-side

        if (!level().isClientSide && boostTicks > 0) {
            // Authoritative server movement: step exactly along the beam path every tick (no particles)
            Vec3 step = this.boostStep;
            double spd = step.length();
            if (spd > ABS_SPEED_CAP) step = step.scale(ABS_SPEED_CAP / spd);

            double nx = this.getX() + step.x;
            double ny = this.getY() + step.y;
            double nz = this.getZ() + step.z;

            this.absMoveTo(nx, ny, nz);
            this.setDeltaMovement(step);
            this.hasImpulse = true;
            this.setOnGround(false);
            this.fallDistance = 0;

            boostTicks--;
            if (boostTicks <= 0 && this.entityData.get(DASHING)) {
                this.entityData.set(DASHING, Boolean.FALSE);
            }
            if (boostTicks <= 0 && this.noClipBurn) {
                this.noPhysics = false;
                this.noClipBurn = false;
            }
        }

        // Server: handle confirmation glow blinking
        if (!level().isClientSide) {
            processConfirmBlink();
            // Passive DM regen while idle
            if (!isAbductionActive() && boostTicks <= 0) {
                // Faster recharge (keeps improved rate, removes visuals per request)
                addDarkMatter(+4);
            }
        }

        // (abduction beam rendered by renderer)

        if (!level().isClientSide) {
            spawnGroundScuffsAtAnyAltitudeUpTo20();
            processEncounterAI();
            processShieldProjectiles();
            processThunderAttraction();
        } else {
            // Client-side: impact visuals where the abduction beam meets terrain or water
            if (this.isAbductionActive() && this.tickCount % 3 == 0) {
                final var lvl = this.level();
                final double x = this.getX();
                final double y = this.getY();
                final double z = this.getZ();
                // Try raytrace to ground first for accurate impact point
                var start = new net.minecraft.world.phys.Vec3(x, y - 0.1, z);
                var end = new net.minecraft.world.phys.Vec3(x, y - 64.0, z);
                var hit = lvl.clip(new net.minecraft.world.level.ClipContext(start, end,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, this));
                double gy;
                net.minecraft.core.BlockPos basePos;
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    gy = hit.getLocation().y + 0.02;
                    basePos = new net.minecraft.core.BlockPos(net.minecraft.util.Mth.floor(hit.getLocation().x), net.minecraft.util.Mth.floor(hit.getLocation().y), net.minecraft.util.Mth.floor(hit.getLocation().z));
                } else {
                    gy = groundYAt(x, z) + 0.05;
                    basePos = new net.minecraft.core.BlockPos(net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(gy), net.minecraft.util.Mth.floor(z));
                }
                if (gy > lvl.getMinBuildHeight()) {
                    boolean centerWaterHere = lvl.getBlockState(basePos).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
                    boolean centerWaterBelow = lvl.getBlockState(basePos.below()).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
                    boolean centerWater = centerWaterHere || centerWaterBelow;
                    // Subtle spark particles at impact to verify custom atlas + provider
                    for (int i = 0; i < 4; i++) {
                        double sx = x + (this.random.nextDouble() - 0.5) * 0.6;
                        double sz = z + (this.random.nextDouble() - 0.5) * 0.6;
                        double sy = gy + this.random.nextDouble() * 0.1;
                        double svx = (this.random.nextDouble() - 0.5) * 0.02;
                        double svy = 0.02 + this.random.nextDouble() * 0.03;
                        double svz = (this.random.nextDouble() - 0.5) * 0.02;
                        lvl.addParticle(com.exoticmatter.totm.registry.ModParticles.SPARK.get(), sx, sy, sz, svx, svy, svz);
                    }
                    if (centerWater) {
                        // Adjust Y to water surface (top of water block)
                        if (centerWaterHere) gy = basePos.getY() + 1.0; else gy = basePos.below().getY() + 1.0;
                    }

                    // Center impact: water-specific or ground scuff
                    if (centerWater) {
                        // Sub-surface bubbles + surface splashes/pop
                        lvl.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE,
                                x, gy - 0.08, z, 0.0, 0.03, 0.0);
                        lvl.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                                x, gy + 0.02, z, 0.0, 0.0, 0.0);
                        lvl.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,
                                x, gy + 0.02, z, 0.0, 0.1, 0.0);
                    } else {
                        // Center puff that stays and spreads slightly
                        lvl.addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                                x, gy, z, 0.03 * (this.random.nextDouble()-0.5), 0.01, 0.03 * (this.random.nextDouble()-0.5));
                    }

                    // Ring: decide per-point whether to use water bubbles or ground scuff
                    int pts = 14;
                    for (int i = 0; i < pts; i++) {
                        double ang = (i / (double) pts) * Math.PI * 2.0;
                        double rad = ABDUCT_RADIUS * (1.0 + this.getRandom().nextDouble() * 1.2);
                        double px = x + Math.cos(ang) * rad;
                        double pz = z + Math.sin(ang) * rad;
                        // Compute an appropriate base pos near the ring point; adjust to local water surface if needed
                        net.minecraft.core.BlockPos rp = new net.minecraft.core.BlockPos(
                                net.minecraft.util.Mth.floor(px), net.minecraft.util.Mth.floor(gy), net.minecraft.util.Mth.floor(pz));
                        boolean wHere = this.level().getBlockState(rp).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
                        boolean wBelow = this.level().getBlockState(rp.below()).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
                        boolean w = wHere || wBelow;
                        double ry = gy;
                        if (w) ry = (wHere ? rp.getY() + 1.0 : rp.below().getY() + 1.0);

                        double vx = Math.cos(ang) * 0.06;
                        double vz = Math.sin(ang) * 0.06;
                        if (w) {
                            // Sub-surface ring bubbles + surface action
                            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE,
                                    px, ry - 0.08, pz, vx, 0.02, vz);
                            if (this.random.nextInt(3) == 0) {
                                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                                        px, ry + 0.02, pz, 0.0, 0.0, 0.0);
                            }
                            if (this.random.nextInt(4) == 0) {
                                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,
                                        px, ry + 0.02, pz, 0.0, 0.06, 0.0);
                            }
                        } else {
                            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                                    px, ry, pz, vx, 0.01, vz);
                        }
                    }
                }
            }
            // Client-side: cooldown steam emission while jump is recharging
            if (this.jumpCooldown > 0 && JUMP_COOLDOWN_TICKS > 0) {
                double frac = this.jumpCooldown / (double) JUMP_COOLDOWN_TICKS; // 1->0
                int count = 2 + (int) Math.ceil(6 * frac);
                double r = 0.8;
                for (int i = 0; i < count; i++) {
                    double ang = this.getRandom().nextDouble() * Math.PI * 2.0;
                    double rad = r * (0.5 + this.getRandom().nextDouble() * 0.8);
                    double px = this.getX() + Math.cos(ang) * rad;
                    double pz = this.getZ() + Math.sin(ang) * rad;
                    double py = this.getY() + 0.6 + this.getRandom().nextDouble() * 0.5;
                    double vy = 0.03 + this.getRandom().nextDouble() * 0.04;
                    double vx = (this.getRandom().nextDouble() - 0.5) * 0.02;
                    double vz = (this.getRandom().nextDouble() - 0.5) * 0.02;
                    this.level().addParticle(ParticleTypes.CLOUD, px, py, pz, vx, vy, vz);
                }
            }
        }
    }

    @Override
    protected void doPush(Entity e) {
        // Improve collision for entities standing on the saucer: gently nudge upward and outward
        if (e == null) return;
        double dy = e.getY() - this.getY();
        if (dy >= 0.0 && dy <= this.getBbHeight() + 0.5) {
            double dx = e.getX() - this.getX();
            double dz = e.getZ() - this.getZ();
            double len2 = dx*dx + dz*dz;
            if (len2 < 1e-4) { dx = 0.1; dz = 0.0; len2 = dx*dx; }
            double inv = 1.0 / Math.sqrt(len2);
            double push = 0.05;
            e.setDeltaMovement(e.getDeltaMovement().add(dx*inv*push, 0.08, dz*inv*push));
            e.hurtMarked = true;
        }
    }

    // During thunderstorms, occasionally attract a lightning strike if under open sky
    private void processThunderAttraction() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        if (!sl.isThundering()) return;
        // Simple cooldown to avoid excessive strikes per saucer
        var tag = this.getPersistentData();
        long now = sl.getGameTime();
        long cdUntil = tag.getLong("totm_lightning_cd");
        if (now < cdUntil) return;

        // Require sky access to feel like a natural strike
        net.minecraft.core.BlockPos pos = this.blockPosition();
        if (!sl.canSeeSky(pos)) return;

        // Roughly once every ~10–20 seconds during thunder per saucer
        if (this.getRandom().nextInt(200) == 0) {
            var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(sl);
            if (bolt != null) {
                bolt.moveTo(this.getX(), this.getY(), this.getZ());
                sl.addFreshEntity(bolt);
                // Next strike after a short delay window
                tag.putLong("totm_lightning_cd", now + 200L + this.getRandom().nextInt(200));
            }

            // Random angled exhaust bursts while flying
            if (!this.isAbductionActive() && this.tickCount % 7 == 0 && this.random.nextInt(6) == 0) {
                final double x = this.getX();
                final double y = this.getY();
                final double z = this.getZ();
                // Use yaw to emit generally behind with a slight random angle
                double yawRad = Math.toRadians(this.getYRot());
                double behindX = -Math.sin(yawRad);
                double behindZ = Math.cos(yawRad);
                double angle = (this.random.nextDouble() - 0.5) * 0.6; // spread angle
                double cos = Math.cos(angle), sin = Math.sin(angle);
                double dirX = behindX * cos - behindZ * sin;
                double dirZ = behindX * sin + behindZ * cos;
                double dirY = (this.random.nextDouble() - 0.5) * 0.15; // small vertical variation

                // Spawn a few particles forming an exhaust puff
                for (int i = 0; i < 6; i++) {
                    double t = 0.2 + i * 0.06;
                    double px = x + dirX * (0.8 + t);
                    double py = y + 0.3 + dirY * (0.5 + i * 0.02);
                    double pz = z + dirZ * (0.8 + t);
                    this.level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, px, py, pz, dirX * 0.02, 0.01, dirZ * 0.02);
                    if (i % 2 == 0) {
                        this.level().addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, px, py - 0.05, pz, 0, 0.001, 0);
                    }
                }
            }
        }
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
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) return net.minecraft.world.InteractionResult.sidedSuccess(true);
        var held = player.getItemInHand(hand);
        // Alien Tech healing disabled on flyable saucers; use it on crashed saucers only
        if (held.is(com.exoticmatter.totm.registry.ModItems.ALIEN_TECH.get())) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            // Toggle piloting on right-click
            if (this.pilotId == sp.getId()) {
                this.stopPiloting();
            } else {
                this.startPiloting(sp);
            }
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    public double getPassengersRidingOffset() {
        return Math.max(0.5D, this.getBbHeight() * 0.5D);
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public boolean canJump() {
        return this.jumpCooldown == 0;
    }

    @Override
    public int getJumpCooldown() {
        return jumpCooldown;
    }

    // --- Piloting state (server authoritative) ---
    private int pilotId = -1;

    private void startPiloting(net.minecraft.server.level.ServerPlayer sp) {
        this.pilotId = sp.getId();
        // Ensure the player's current motion doesn't carry over into piloting
        sp.setDeltaMovement(0, 0, 0);
        sp.noPhysics = true;
        sp.setInvisible(true);
        sp.setInvulnerable(true);
        sp.fallDistance = 0f;
        // Mount sound is handled client-side via packet for non-positional playback
        com.exoticmatter.totm.network.ModNetwork.CHANNEL.sendTo(new com.exoticmatter.totm.network.packet.PilotStartS2CPacket(this.getId()), sp.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    private void stopPiloting() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        if (pilotId == -1) return;
        var e = sl.getEntity(pilotId);
        if (e instanceof net.minecraft.server.level.ServerPlayer sp) {
            // Place player at the saucer on exit
            sp.teleportTo(this.getX(), this.getY() + this.getPassengersRidingOffset(), this.getZ());
            // Prevent fall damage immediately after dismount (especially at ~4 blocks)
            sp.fallDistance = 0f;
            // Clear any residual velocity so the player doesn't slam into ground
            sp.setDeltaMovement(0, 0, 0);
            double groundY = groundYAt(this.getX(), this.getZ());
            double alt = (this.getY() + this.getPassengersRidingOffset()) - groundY;
            if (alt <= 5.0) {
                sp.getPersistentData().putLong("totm_no_fall_until", sl.getGameTime() + 40L);
            }
            sp.noPhysics = false;
            sp.setInvisible(false);
            sp.setInvulnerable(false);
            com.exoticmatter.totm.network.ModNetwork.CHANNEL.sendTo(new com.exoticmatter.totm.network.packet.PilotStopS2CPacket(), sp.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        }
        pilotId = -1;
    }

    private static final double SHIELD_RADIUS = 8.0;
    private void processShieldProjectiles() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        var aabb = this.getBoundingBox().inflate(SHIELD_RADIUS, SHIELD_RADIUS, SHIELD_RADIUS);
        var projs = sl.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, aabb);
        for (var proj : projs) {
            // Only intercept if moving toward saucer and within radius from center horizontally
            net.minecraft.world.phys.Vec3 toSaucer = new net.minecraft.world.phys.Vec3(this.getX() - proj.getX(), 0.0, this.getZ() - proj.getZ());
            double dist2 = toSaucer.lengthSqr();
            if (dist2 > (SHIELD_RADIUS * SHIELD_RADIUS)) continue;
            var v = proj.getDeltaMovement();
            if (v.lengthSqr() < 1e-6) continue;
            double dot = v.normalize().dot(toSaucer.normalize());
            if (dot <= 0.2) continue; // not heading toward
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    proj.getX(), proj.getY(), proj.getZ(), 16, 0.3, 0.3, 0.3, 0.02);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    proj.getX(), proj.getY(), proj.getZ(), 8, 0.15, 0.15, 0.15, 0.01);
            sl.playSound(null, new net.minecraft.core.BlockPos((int)proj.getX(), (int)proj.getY(), (int)proj.getZ()),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.6f);
            proj.discard();
        }
    }

    private void processEncounterAI() {
        boolean piloted = (getPilot() != null);
        var tag = this.getPersistentData();

        // If this saucer has an owner (player-repaired), treat it as persistent and never run encounter logic
        if (tag.hasUUID("totm_owner")) {
            return;
        }

        // Crash handling should work even for paired and piloted saucers
        if (this.level() instanceof net.minecraft.server.level.ServerLevel slx) {
            if (tag.getBoolean("totm_crash_pending") && !tag.getBoolean("totm_crash_started")) {
                // Strike by lightning once
                var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(slx);
                if (bolt != null) {
                    bolt.moveTo(this.getX(), this.getY(), this.getZ());
                    slx.addFreshEntity(bolt);
                    // Airburst explosion at strike altitude (visual only, no block damage)
                    slx.explode(null, this.getX(), this.getY() + 0.5, this.getZ(), 2.5f, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
                    // Initial impact visuals: more intense flames/smoke and sci-fi shockwave
                    slx.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                            this.getX(), this.getY(), this.getZ(),
                            60, 0.8, 0.4, 0.8, 0.12);
                    slx.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                            this.getX(), this.getY() + 0.2, this.getZ(),
                            60, 0.6, 0.25, 0.6, 0.02);
                    slx.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                            this.getX(), this.getY() + 0.2, this.getZ(),
                            20, 0.6, 0.25, 0.6, 0.01);
                    slx.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                            this.getX(), this.getY() + 0.2, this.getZ(),
                            1, 0.0, 0.0, 0.0, 0.0);
                    // Shockwave ring
                    int ring = 36;
                    double baseY = this.getY() + 0.5;
                    for (int i = 0; i < ring; i++) {
                        double ang = (i / (double) ring) * Math.PI * 2.0;
                        double rad = 1.5;
                        double px = this.getX() + Math.cos(ang) * rad;
                        double pz = this.getZ() + Math.sin(ang) * rad;
                        double vx = Math.cos(ang) * 0.6;
                        double vz = Math.sin(ang) * 0.6;
                        slx.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, px, baseY, pz, 2, 0.0, 0.0, 0.0, 0.0);
                        slx.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, px, baseY, pz, 2, 0.0, 0.0, 0.0, 0.0);
                        // outward motion streak
                        slx.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, px, baseY, pz, 1, vx * 0.02, 0.0, vz * 0.02, 0.0);
                    }
                    // Sci-fi swoosh
                    slx.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.HOSTILE, 1.2f, 1.0f);
                    tag.putBoolean("totm_crash_started", true);
                    tag.putInt("totm_enc_stage", -1); // crashing
                    this.setAbductionActive(false);
                    this.setGlowEnabled(false);
                    // Random render tilt for damaged/crashing look
                    tag.putInt("totm_tilt_pitch", 10 + this.getRandom().nextInt(20));
                    tag.putInt("totm_tilt_roll", 8 + this.getRandom().nextInt(16));
                    // Propagate crash to nearby paired followers
                    var followers = slx.getEntitiesOfClass(FlyingSaucerEntity.class,
                            this.getBoundingBox().inflate(32, 32, 32),
                            s -> s != this && (s.getFormationLeaderId() == this.getId() || this.getFormationLeaderId() == s.getId()));
                    for (var f : followers) {
                        f.getPersistentData().putBoolean("totm_crash_pending", true);
                    }
                }
            }
        }

        // If we are crashing or wrecked, run that flow regardless of encounter
        int stageCheck = tag.getInt("totm_enc_stage");
        if (stageCheck == -1 || stageCheck == -2 || tag.getBoolean("totm_crash_started") || tag.getBoolean("totm_damaged")) {
            // Reuse the crash/wreck handlers below by allowing execution to continue without requiring encounter flag
        } else if (!tag.getBoolean("totm_encounter") || piloted) {
            return;
        }
        if (tag.getBoolean("totm_reset_motion")) {
            this.boostTicks = 0;
            this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            tag.remove("totm_reset_motion");
        }
        int stage = tag.getInt("totm_enc_stage");
        int timer = tag.getInt("totm_enc_timer");
        boolean debugHold = tag.getBoolean("totm_enc_debug");

        // Anchor around which to play
        double ax = tag.getDouble("totm_enc_anchor_x");
        double az = tag.getDouble("totm_enc_anchor_z");

        if (stage == -1) {
            // Crashing: descend to ground, smoke and fire, explode on impact
            double gy = groundYAt(getX(), getZ()) + 0.4;
            double ny = Math.max(gy, this.getY() - 0.3);
            this.setPos(this.getX(), ny, this.getZ());
            if (this.level() instanceof net.minecraft.server.level.ServerLevel sl3) {
                sl3.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 16, 0.4, 0.25, 0.4, 0.012);
                sl3.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, this.getX(), this.getY()-0.4, this.getZ(), 14, 0.25, 0.12, 0.25, 0.012);
            }
            if (this.getY() <= gy + 0.01) {
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl4) {
                    // Stronger explosion that damages terrain and can start fires
                    sl4.explode(null, this.getX(), gy, this.getZ(), 3.5f, true, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                    // Additional scattered fires near the impact site to sell the crash
                    for (int i=0;i<6;i++) {
                        int dx = this.random.nextInt(5)-2;
                        int dz = this.random.nextInt(5)-2;
                        var p = this.blockPosition().offset(dx, 0, dz);
                        var bp = new net.minecraft.core.BlockPos(p.getX(), (int)gy, p.getZ());
                        if (sl4.getBlockState(bp).isAir() && sl4.getBlockState(bp.below()).isSolid()) {
                            sl4.setBlock(bp, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
                        }
                    }
                }
                tag.putBoolean("totm_damaged", true);
                tag.remove("totm_crash_pending");
                tag.putInt("totm_enc_stage", -2); // wrecked
            }
            return;
        }
        if (stage == -2) {
            // Convert to a dedicated crashed saucer entity and remove this one
            if (this.level() instanceof net.minecraft.server.level.ServerLevel slw) {
                if (!tag.getBoolean("totm_crashed_spawned")) {
                    var crashedType = com.exoticmatter.totm.registry.ModEntities.CRASHED_SAUCER.get();
                    var crashed = crashedType.create(slw);
                    if (crashed != null) {
                        double gy = groundYAt(getX(), getZ()) + 0.4;
                        crashed.moveTo(this.getX(), gy, this.getZ(), this.getYRot(), 0.0f);
                        // Initialize spawn window for post-crash gray alien spawns
                        var ctag = crashed.getPersistentData();
                        long now = slw.getGameTime();
                        ctag.putLong("totm_crash_time", now);
                        ctag.putInt("totm_spawned_aliens", 0);
                        ctag.putLong("totm_spawn_window_until", now + 20L * 30L); // ~30s window
                        ctag.putLong("totm_alien_next", now + 20L * (2 + this.random.nextInt(5))); // first attempt within 2-6s
                        slw.addFreshEntity(crashed);
                    }
                    tag.putBoolean("totm_crashed_spawned", true);
                    this.discard();
                }
            }
            return;
        }
        if (stage == 0) {
            // Patrol: mode 0 = cross-view dash; mode 1 = loiter; mode 2 = hover then ascend
            int mode = tag.getInt("totm_enc_mode");
            this.setAbductionActive(false);
            // Guarantee at least 15s view time in patrol (set once)
            if (!tag.getBoolean("totm_min_view_set")) {
                if (timer < 20 * 15) {
                    tag.putInt("totm_enc_timer", 20 * 15);
                    timer = 20 * 15;
                }
                tag.putBoolean("totm_min_view_set", true);
            }
            if (!debugHold && this.boostTicks <= 0) {
                if (mode == 0) {
                    // Tangential direction relative to anchor (player)
                    int dashCd = tag.getInt("totm_dash_cool");
                    if (dashCd > 0) {
                        tag.putInt("totm_dash_cool", dashCd - 1);
                    } else {
                        double vx = this.getX() - ax;
                        double vz = this.getZ() - az;
                        if (vx*vx + vz*vz < 1e-4) { vx = 1; vz = 0; }
                        // Perpendicular vector to (vx, vz)
                        double tx = -vz;
                        double tz = vx;
                        if (this.random.nextBoolean()) { tx = -tx; tz = -tz; }
                        net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(tx, 0.01, tz).normalize();
                        int pwr = 40 + this.random.nextInt(30); // slower dash for longer view
                        this.applySlingshotServer(pwr, dir);
                        tag.putInt("totm_dash_cool", 30); // ~1.5s between passes
                    }
                } else if (mode == 1) {
                    // Loiter: small random drift with rare quick dash
                    if (this.random.nextInt(6) == 0) {
                        double ang = this.random.nextDouble() * Math.PI * 2.0;
                        net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(Math.cos(ang), 0.01, Math.sin(ang)).normalize();
                        int pwr = 20 + this.random.nextInt(10);
                        this.applySlingshotServer(pwr, dir);
                    } else {
                        // Gentle hover correction toward anchor ring (~24 blocks away)
                        double vx = this.getX() - ax;
                        double vz = this.getZ() - az;
                        double dist = Math.sqrt(vx*vx + vz*vz);
                        double target = 24.0;
                        double err = dist - target;
                        if (Math.abs(err) > 2.0) {
                           double fx = (vx / (dist + 1e-4)) * -Math.signum(err) * 0.05;
                           double fz = (vz / (dist + 1e-4)) * -Math.signum(err) * 0.05;
                           this.setPos(this.getX() + fx, this.getY(), this.getZ() + fz);
                        }
                    }
                } else if (mode == 2) {
                    // Hover only; no movement here, let hover system keep altitude
                }
            }
            if (timer > 0) { tag.putInt("totm_enc_timer", timer - 1); }
            else {
                if (mode == 2) {
                    tag.putInt("totm_enc_stage", 4); // ascend
                    tag.putInt("totm_ascend_ticks", 60);
                } else {
                    tag.putInt("totm_enc_stage", 1);
                    tag.putInt("totm_enc_timer", 20 * 15); // longer deploy window ~15s
                    tag.remove("totm_deploy_phase");
                    tag.remove("totm_deployed_id");
                }
            }
        } else if (stage == 1) {
            // Move over a landing spot (near ground) and deploy a gray alien
            double gx = this.getX();
            double gz = this.getZ();
            double gy = groundYAt(gx, gz) + 6.0; // hover low
            // Nudge toward low hover height
            this.setPos(this.getX(), net.minecraft.util.Mth.lerp(0.15, this.getY(), gy), this.getZ());
            if (timer > 0) {
                tag.putInt("totm_enc_timer", timer - 1);
                this.setAbductionActive(true); // show beam while deploying
                // Phase 0: spawn alien at saucer height and beam it down
                int phase = tag.getInt("totm_deploy_phase");
                if (phase == 0) {
                    if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        var alienType = com.exoticmatter.totm.registry.ModEntities.GRAY_ALIEN.get();
                        var alien = alienType.create(sl);
                        if (alien != null) {
                            double startY = this.getY() - 0.5;
                            alien.moveTo(this.getX(), startY, this.getZ(), this.getYRot(), 0.0f);
                            // Tag mission and beam-down target
                            var atag = alien.getPersistentData();
                            atag.putBoolean("totm_mission_return", true);
                            atag.putInt("totm_return_after", 20 * (10 + this.random.nextInt(10))); // 10-19s on ground
                            atag.putInt("totm_return_target", this.getId());
                            atag.putBoolean("totm_mission_probe_cow", true);
                            atag.putBoolean("totm_beam_down", true);
                            atag.putDouble("totm_beam_target_y", groundYAt(this.getX(), this.getZ()) + 0.2);
                            // Equip probe for flavor
                            var probe = com.exoticmatter.totm.registry.ModItems.PROBE.get();
                            if (probe != null) {
                                alien.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(probe));
                            }
                            sl.addFreshEntity(alien);
                            tag.putInt("totm_deployed_id", alien.getId());
                            tag.putInt("totm_deploy_phase", 1);
                        }
                    }
                } else if (phase == 1) {
                    // Beam the deployed alien down smoothly
                    if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        var e = sl.getEntity(tag.getInt("totm_deployed_id"));
                        if (e instanceof com.exoticmatter.totm.world.entity.GrayAlienEntity a) {
                            double ty = a.getPersistentData().getDouble("totm_beam_target_y");
                            double ny = a.getY() - 0.28;
                            if (ny <= ty) {
                                ny = ty;
                                a.getPersistentData().remove("totm_beam_down");
                                tag.putInt("totm_deploy_phase", 2);
                            }
                            a.fallDistance = 0f;
                            a.setDeltaMovement(0, 0, 0);
                            a.setPos(a.getX(), ny, a.getZ());
                        } else {
                            // Missing alien; move on
                            tag.putInt("totm_deploy_phase", 2);
                        }
                    }
                }
            } else {
                tag.putInt("totm_enc_stage", 2);
                tag.putInt("totm_enc_timer", 20 * 20); // longer wait for alien to return
                this.setAbductionActive(false);
            }
        } else if (stage == 2) {
            // Wait for alien to finish; then depart
            if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                var nearby = sl.getEntitiesOfClass(com.exoticmatter.totm.world.entity.GrayAlienEntity.class,
                        this.getBoundingBox().inflate(16, 8, 16),
                        a -> a.getPersistentData().getInt("totm_return_target") == this.getId());
                // Non-piloted beam pull: lift returning aliens up through the beam and despawn at top
                this.setAbductionActive(true);
                if (!nearby.isEmpty()) {
                    double baseY = groundYAt(this.getX(), this.getZ()) + 0.5;
                    double topY = this.getY() - 0.5;
                    for (var a : nearby) {
                        if (a.getY() < topY) {
                            var dv = a.getDeltaMovement();
                            double vy = Math.max(dv.y, 0.30);
                            a.setDeltaMovement(dv.x * 0.6, vy, dv.z * 0.6);
                            a.fallDistance = 0f;
                            a.hasImpulse = true;
                        } else {
                            a.discard();
                        }
                    }
                }
                // If a mission alien is close enough, "pick it up": despawn the alien
                for (var a : nearby) {
                    if (a.distanceTo(this) < 3.0) {
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, a.getX(), a.getY() + 1.0, a.getZ(), 8, 0.2, 0.2, 0.2, 0.01);
                        a.discard();
                    }
                }
                // If no mission alien remains nearby, depart
                if (nearby.isEmpty()) {
                    tag.putInt("totm_enc_stage", 3);
                    tag.putInt("totm_enc_timer", 20 * 10); // longer departure sequence
                    this.setAbductionActive(false);
                }
            } else {
                if (timer > 0) { tag.putInt("totm_enc_timer", timer - 1); } else { tag.putInt("totm_enc_stage", 3); tag.putInt("totm_enc_timer", 20 * 10);}            
            }
        } else if (stage == 3) {
            // Depart with a few long dashes, then vanish
            if (!debugHold && this.boostTicks <= 0) {
                double ang = this.random.nextDouble() * Math.PI * 2.0;
                net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(Math.cos(ang), 0.05, Math.sin(ang)).normalize();
                int pwr = 60 + this.random.nextInt(20);
                this.applySlingshotServer(pwr, dir);
            }
            // One-time alien-themed drop on departure
            if (!tag.getBoolean("totm_drop_done") && this.level() instanceof net.minecraft.server.level.ServerLevel sl2) {
                tag.putBoolean("totm_drop_done", true);
                var drop = this.random.nextBoolean() ?
                        new net.minecraft.world.item.ItemStack(com.exoticmatter.totm.registry.ModItems.PROBE.get()) :
                        new net.minecraft.world.item.ItemStack(com.exoticmatter.totm.registry.ModItems.ALIEN_HEAD.get());
                var item = new net.minecraft.world.entity.item.ItemEntity(sl2, this.getX(), this.getY() - 1.0, this.getZ(), drop);
                sl2.addFreshEntity(item);
            }
            if (timer > 0) {
                tag.putInt("totm_enc_timer", timer - 1);
            } else {
                this.discard();
            }
        } else if (stage == 4) {
            // Ascend straight up for a short duration, then despawn
            int left = tag.getInt("totm_ascend_ticks");
            if (left > 0) {
                tag.putInt("totm_ascend_ticks", left - 1);
                this.setPos(this.getX(), this.getY() + 0.7, this.getZ());
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sla) {
                    sla.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            this.getX(), this.getY() - 0.5, this.getZ(), 4, 0.3, 0.2, 0.3, 0.01);
                }
            } else {
                this.discard();
            }
        }
    }

    private net.minecraft.server.level.ServerPlayer getPilot() {
        if (pilotId == -1 || !(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return null;
        var e = sl.getEntity(pilotId);
        return (e instanceof net.minecraft.server.level.ServerPlayer sp) ? sp : null;
    }

    @Override
    public void handleStartJump(int power) {
        if (!level().isClientSide) return;

        clientLastJumpPower = power;
        revWobbleLeft = Math.min(revWobbleLeft + 1, REV_WOBBLE_TICKS);
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() == this) {
            // no debug text or particles
        }

        // (abduction visuals handled during tick for smoother motion)
    }

    @Override
    public void handleStopJump() {
        // No-op; the engine reports final power through onPlayerJump
    }

    @Override
    public void onPlayerJump(int power) {
        if (!level().isClientSide) {
            LivingEntity rider = this.getControllingPassenger();
            Vec3 dir = null;
            if (rider != null) {
                Vec3 look = rider.getViewVector(1.0F);
                dir = look.normalize(); // include vertical to allow aiming up/down
            }
            if (dir == null || dir.lengthSqr() < 1.0e-6) {
                Vec3 fallback = Vec3.directionFromRotation(this.getXRot(), this.getYRot());
                dir = fallback.normalize();
            }

            applySlingshotServer(power, dir);
            return;
        }

        // Client: send jump to server with the resolved final power
        ModNetwork.sendToServer(new SaucerJumpPacket(this.getId(), power));

        revWobbleLeft = REV_WOBBLE_TICKS;
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() == this) {
            // no debug text or sounds
        }
    }

    // Server-side application of launch
    public void applySlingshotServer(int power, Vec3 dashDirFromPacket) {
        if (!this.isAlive()) return;
        // Respect cooldown and avoid overlapping burns
        // Allow immediate re-dash if not currently burning
        if (this.boostTicks > 0) return;

        int pwr = Mth.clamp(power, 1, 100);
        double t = pwr / 100.0;
        double eased = t * t;

        // Distance and duration scale with charge (proportional feel)
        int ticks = (int) Math.round(Mth.lerp(eased, BURN_TICKS_MIN, BURN_TICKS_MAX));
        double dist = Mth.lerp(eased, DIST_MIN, DIST_MAX);
        double speed = dist / Math.max(1, ticks);

        Vec3 dir = (dashDirFromPacket != null && dashDirFromPacket.lengthSqr() > 1e-6)
                ? dashDirFromPacket.normalize()
                : new Vec3(0, 0, 1);

        // Dark Matter dash cost (significantly higher than before)
        if (!level().isClientSide) {
            // Cost model: base 100 + 4 per power point
            int available = getDarkMatter();
            if (available <= 0) return;
            int maxPowerByDM = Math.max(0, (available - 100) / 4);
            if (maxPowerByDM <= 0) return; // not enough DM for even a minimal dash
            if (pwr > maxPowerByDM) pwr = maxPowerByDM;
            int dashCost = 100 + (pwr * 4);
            addDarkMatter(-dashCost);
        }

        this.boostDir = dir;
        this.boostTicks = ticks;
        this.targetFwdSpeed = speed;
        this.boostStep = dir.scale(speed);

        // Seed delta so clients interpolate the first tick
        this.setDeltaMovement(this.boostStep);
        this.hasImpulse = true;

        this.fallDistance = 0f;
        this.entityData.set(DASHING, Boolean.TRUE);

        // no debug chat

        // no server-side beam

        // Do not alter physics or eject the pilot; keep default riding behavior during burn
    }

    // Movement
    @Override
    public void travel(Vec3 travelVector) {
        var tag = this.getPersistentData();
        int crashStage = tag.getInt("totm_enc_stage");
        boolean crashing = (crashStage == -1) || tag.getBoolean("totm_crash_started");
        boolean wrecked = (crashStage == -2); // rely on explicit stage for grounding

        // Force descent during crash; freeze when wrecked
        if (crashing) {
            // Descend steadily; ignore hover logic
            double gy = groundYAt(this.getX(), this.getZ()) + 0.4;
            double vy = this.getY() > gy ? -0.35 : 0.0;
            this.setDeltaMovement(0.0, vy, 0.0);
            this.hasImpulse = true;
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setOnGround(false);
            this.fallDistance = 0;
            return;
        }
        if (wrecked) {
            // Stay put on the ground while wrecked
            double gy = groundYAt(this.getX(), this.getZ()) + 0.4;
            this.setPos(this.getX(), gy, this.getZ());
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0;
            return;
        }
        // Apply burn movement first, regardless of rider presence
        if (boostTicks > 0) {
            Vec3 step = this.boostStep;
            double spd = step.length();
            if (spd > ABS_SPEED_CAP) step = step.scale(ABS_SPEED_CAP / spd);

            // Move using standard movement to avoid teleport-like behavior
            this.setDeltaMovement(step);
            this.hasImpulse = true;
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setOnGround(false);
            this.fallDistance = 0;
            // Air displacement visuals while dashing (server)
            if (!level().isClientSide && this.tickCount % 2 == 0) {
                var sl = (net.minecraft.server.level.ServerLevel) this.level();
                int p = 6;
                for (int i = 0; i < p; i++) {
                    double ox = (this.getRandom().nextDouble() - 0.5) * 1.2;
                    double oz = (this.getRandom().nextDouble() - 0.5) * 1.2;
                    double px = this.getX() + ox;
                    double py = this.getY() + 0.6 + (this.getRandom().nextDouble() * 0.6 - 0.3);
                    double pz = this.getZ() + oz;
                    // small outward component perpendicular to motion
                    Vec3 dir = new Vec3(-step.z, 0.0, step.x).normalize().scale((this.getRandom().nextDouble() - 0.5) * 0.2);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, px, py, pz, 1, dir.x, 0.02, dir.z, 0.0);
                }
            }
            boostTicks--;
            return;
        }

        LivingEntity rider = this.getControllingPassenger();
        final double time = (this.tickCount + this.wobblePhase);

        net.minecraft.server.level.ServerPlayer pilot = getPilot();
        if (!level().isClientSide && pilot != null && this.boostTicks <= 0 && (this.tickCount % 10 == 0)) {
            // Refresh any explicitly paired followers so they stay in formation
            assignTriangleFormation();
        }
        if (pilot != null) {
            this.setYRot(pilotYaw);
            this.setXRot(pilotPitch * 0.5F);

            // Update lastDashDir from input (steer between dashes)
            // Flip strafe axis so D moves right and A moves left in world space
            double strafe = -pilotStrafe;
            double forward = pilotForward;
            float speedAttr = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
            Vec3 moveLocal = new Vec3(strafe, 0, forward);
            Vec3 moveWorld = moveLocal.yRot((float) Math.toRadians(-this.getYRot()));
            if (moveWorld.lengthSqr() > 0.0004) lastDashDir = moveWorld.normalize();

            // (burn handled above)

            // Not burning: gentle drag + standard hover + WASD blend
            Vec3 v = this.getDeltaMovement().scale(POST_BURN_DRAG);

            double measuredGround = groundYAt(this.getX(), this.getZ());
            if (Double.isNaN(filteredGroundY)) filteredGroundY = measuredGround;
            double diff = measuredGround - filteredGroundY;
            double maxStep = 0.35; // limit ground height change per tick to smooth elevation rises
            if (diff > maxStep) diff = maxStep;
            else if (diff < -maxStep) diff = -maxStep;
            filteredGroundY += diff;
            double ground = filteredGroundY;
            double offset = pilotSneak ? 4.0 : HOVER_OFFSET_RIDDEN;
            double targetY = ground + offset;
            double vy = v.y;
            double error = targetY - this.getY();
            double pull = HOVER_STRENGTH_RIDDEN * clamp(error, -2.0, 2.0);

            double wobbleAmp = BOB_AMPLITUDE_BASE * (revWobbleLeft > 0 ? REV_WOBBLE_MULT : 1.0);
            double bob = Math.sin(time * BOB_SPEED) * wobbleAmp;

            double vNewY = lerp(0.35, vy, vy * HOVER_DAMP + pull + bob);
            double maxDescent = pilotSneak ? MAX_DESCENT_SPEED * 3.0 : MAX_DESCENT_SPEED;
            if (vNewY < -maxDescent) vNewY = -maxDescent;

            Vec3 desiredWalk = moveWorld.lengthSqr() > 0 ? moveWorld.normalize().scale(speedAttr) : Vec3.ZERO;
            double keep = 0.60;
            v = new Vec3(
                    v.x * keep + (desiredWalk.x - v.x) * 0.40,
                    vNewY,
                    v.z * keep + (desiredWalk.z - v.z) * 0.40
            );

            this.setDeltaMovement(v);
            this.hasImpulse = true;
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setOnGround(false);
            this.fallDistance = 0;

        } else if (pilot == null) {
            // Unridden hover at +4
            Vec3 dm = this.getDeltaMovement();
            double measuredGround = groundYAt(this.getX(), this.getZ());
            if (Double.isNaN(filteredGroundY)) filteredGroundY = measuredGround;
            double diff2 = measuredGround - filteredGroundY;
            double maxStep2 = 0.35;
            if (diff2 > maxStep2) diff2 = maxStep2;
            else if (diff2 < -maxStep2) diff2 = -maxStep2;
            filteredGroundY += diff2;
            double ground = filteredGroundY;
            double targetY = ground + HOVER_OFFSET_IDLE;
            double error = targetY - this.getY();
            double pull = HOVER_STRENGTH_IDLE * clamp(error, -2.0, 2.0);
            double bob = Math.sin(time * BOB_SPEED) * BOB_AMPLITUDE_BASE;
            double yNext = dm.y * HOVER_DAMP + pull + bob;

            if (yNext < -MAX_DESCENT_SPEED) yNext = -MAX_DESCENT_SPEED;

            // Apply formation steering: move as one unit with leader (rigid triangle illusion)
            if (!level().isClientSide && formationLeaderId != -1 && level() instanceof net.minecraft.server.level.ServerLevel sl) {
                var leader = sl.getEntity(formationLeaderId);
                if (!(leader instanceof FlyingSaucerEntity ls) || ls.getPilot() == null || sl.getGameTime() > formationExpireAt) {
                    formationLeaderId = -1; formationSlot = -1;
                } else {
                    Vec3 tgt = formationTargetFor(ls, formationSlot);
                    // Strongly follow leader horizontal velocity and target offset
                    Vec3 lvel = ls.getDeltaMovement();
                    Vec3 here2D = new Vec3(this.getX(), 0, this.getZ());
                    Vec3 target2D = new Vec3(tgt.x, 0, tgt.z);
                    Vec3 to = target2D.subtract(here2D);
                    double dist = Math.sqrt(to.lengthSqr());
                    double gain = Math.min(0.6, dist * 0.15); // converge faster when far, capped
                    Vec3 corr = dist > 1.0e-4 ? to.scale(gain / dist) : Vec3.ZERO;

                    // Mild repulsion from the other wing to preserve triangle shape
                    Vec3 repel = Vec3.ZERO;
                    if (level() instanceof net.minecraft.server.level.ServerLevel sl2) {
                        var others = sl2.getEntitiesOfClass(FlyingSaucerEntity.class, this.getBoundingBox().inflate(24, 24, 24),
                                s -> s != this && s.formationLeaderId == this.formationLeaderId);
                        for (var o : others) {
                            Vec3 sep = new Vec3(this.getX() - o.getX(), 0, this.getZ() - o.getZ());
                            double d2 = Math.sqrt(sep.lengthSqr());
                            if (d2 > 1.0e-4) {
                                double desired = Math.max(6.0, sideForFormation());
                                double push = Math.max(0.0, (desired - d2) * 0.1);
                                repel = repel.add(sep.scale(push / d2));
                            }
                        }
                    }

                    // Desired movement: leader velocity plus corrections
                    dm = new Vec3(
                            lvel.x + corr.x + repel.x,
                            dm.y,
                            lvel.z + corr.z + repel.z
                    );
                    // Snap yaw to leader movement heading for cohesive look independent of camera
                    double yawMovDeg = (lvel.lengthSqr() > 1.0e-6)
                            ? Math.toDegrees(Math.atan2(lvel.x, -lvel.z))
                            : ls.getYRot();
                    this.setYRot((float)yawMovDeg);
                    // Match leader elevation quickly but smoothly
                    double yErr = ls.getY() - this.getY();
                    double climb = clamp(yErr * 0.8, -0.8, 0.8);
                    yNext = climb;
                }
            }
            this.setDeltaMovement(dm.x * 0.9, yNext, dm.z * 0.9);
            this.hasImpulse = true;
            super.travel(travelVector);
            this.setOnGround(false);
            this.fallDistance = 0;
        }

        // Server-only: apply abduction tractor logic (not during burn)
        if (!level().isClientSide) {
            if (this.boostTicks <= 0) processAbduction();
        }
    }

    private static double lerp(double a, double from, double to) {
        return from + a * (to - from);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    // Formation helpers
    private void assignTriangleFormation() {
        // Explicit pairing only: just refresh currently paired followers
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        double range = 96.0; // extend range to keep distant followers paired
        var box = this.getBoundingBox().inflate(range, range, range);
        var followers = sl.getEntitiesOfClass(FlyingSaucerEntity.class, box,
                s -> s != this && s.formationLeaderId == this.getId());
        long now = sl.getGameTime();
        for (var f : followers) {
            // Keep paired followers alive in formation with a comfortable buffer
            f.formationExpireAt = now + 40; // ~2s rolling refresh
        }
    }

    private static Vec3 formationTargetFor(FlyingSaucerEntity leader, int slot) {
        // True equilateral triangle with the player's saucer (leader) at the apex
        double side = sideForFormation(); // side length between saucers
        double halfBase = side * 0.5;             // lateral offset from centerline
        double back = side * Math.sqrt(3.0) * 0.5; // distance behind leader to the base line (height)

        // Determine heading from movement, fallback to yaw if nearly stationary
        Vec3 v = leader.getDeltaMovement();
        double radHeading = (v.lengthSqr() > 1.0e-6)
                ? Math.atan2(v.x, -v.z)
                : Math.toRadians(leader.getYRot());

        // Forward unit (xz-plane) and right unit based on heading
        double sin = Math.sin(radHeading);
        double cos = Math.cos(radHeading);
        Vec3 forward = new Vec3(sin, 0.0, -cos);
        Vec3 right = new Vec3(cos, 0.0, sin);

        // Base line is back along -forward, with lateral offset left/right
        Vec3 baseCenter = new Vec3(leader.getX(), leader.getY(), leader.getZ()).add(forward.scale(-back));
        Vec3 offset;
        if (slot == 0) offset = right.scale(-halfBase); // left wing
        else if (slot == 1) offset = right.scale(halfBase); // right wing
        else offset = Vec3.ZERO; // optional third follower directly centered behind
        Vec3 target = baseCenter.add(offset);
        return target;
    }

    private static double sideForFormation() {
        return 40.0;
    }

    public void setFormationLeaderSlot(int leaderId, int slot, long expireAt) {
        this.formationLeaderId = leaderId;
        this.formationSlot = slot;
        this.formationExpireAt = expireAt;
    }

    public int getFormationLeaderId() { return formationLeaderId; }
    public int getFormationSlot() { return formationSlot; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        // Energy shield: block projectiles and show particles
        Entity direct = source.getDirectEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile proj) {
            if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        proj.getX(), proj.getY(), proj.getZ(), 12, 0.2, 0.2, 0.2, 0.02);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        proj.getX(), proj.getY(), proj.getZ(), 6, 0.1, 0.1, 0.1, 0.01);
            }
            proj.discard();
            return false;
        }
        boolean pass = super.hurt(source, amount);
        if (!this.isAlive()) this.ejectPassengers();
        return pass;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (passenger != null) {
            passenger.noPhysics = false;
            if (passenger instanceof LivingEntity le) {
                le.setInvisible(false);
                // Gently place the passenger on the ground beneath the saucer
                double gx = this.getX();
                double gz = this.getZ();
                double gy = groundYAt(gx, gz) + 1.01; // a touch above ground
                // Reset motion and fall distance to avoid sudden drops
                le.fallDistance = 0f;
                le.setDeltaMovement(0, 0, 0);
                if (!level().isClientSide && le instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.connection.teleport(gx, gy, gz, sp.getYRot(), sp.getXRot());
                } else {
                    le.teleportTo(gx, gy, gz);
                }
            } else {
                // Non-living entities: still place on ground
                double gy = groundYAt(this.getX(), this.getZ()) + 1.01;
                passenger.teleportTo(this.getX(), gy, this.getZ());
            }
        }
    }

    @Override
    public boolean isControlledByLocalInstance() {
        if (level().isClientSide) {
            // During a burn dash, server is authoritative to avoid rubber-banding
            if (this.boostTicks > 0) return false;
            var lp = Minecraft.getInstance().player;
            return lp != null && getControllingPassenger() == lp;
        }
        return super.isControlledByLocalInstance();
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        // Rider becomes the saucer: no hitbox and hidden
        passenger.noPhysics = true;
        if (passenger instanceof LivingEntity le) {
            le.setInvisible(true);
        }
        passenger.teleportTo(this.getX(), this.getY() + this.getPassengersRidingOffset(), this.getZ());
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunc) {
        double off = this.getPassengersRidingOffset();
        moveFunc.accept(passenger, this.getX(), this.getY() + off, this.getZ());
        passenger.setYRot(this.getYRot());
        passenger.setXRot(0);
        passenger.setDeltaMovement(this.getDeltaMovement());
        passenger.noPhysics = true;
        if (!this.level().isClientSide && passenger instanceof net.minecraft.server.level.ServerPlayer sp) {
            // Extra safety sync
            sp.connection.teleport(this.getX(), this.getY() + off, this.getZ(), this.getYRot(), sp.getXRot());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private double groundYAt(double x, double z) {
        // Use MOTION_BLOCKING so leaves and foliage count as surface for hover logic
        return this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(x), (int) Math.floor(z));
    }

    private void spawnGroundScuffsAtAnyAltitudeUpTo20() {
        var sl = (net.minecraft.server.level.ServerLevel) this.level();
        int topY = (int) Math.floor(groundYAt(this.getX(), this.getZ())) - 1;
        if (topY < sl.getMinBuildHeight()) return;

        double alt = this.getY() - (topY + 1.0);
        if (alt > HOVER_OFFSET_RIDDEN + 0.5) return;

        BlockPos pos = BlockPos.containing(Math.floor(this.getX()), topY, Math.floor(this.getZ()));
        BlockState st = sl.getBlockState(pos);
        if (st.isAir()) {
            int y = topY;
            for (int i = 0; i < 2; i++) {
                y -= 1;
                if (y < sl.getMinBuildHeight()) break;
                BlockState st2 = sl.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));
                if (!st2.isAir()) {
                    pos = new BlockPos(pos.getX(), y, pos.getZ());
                    st = st2;
                    break;
                }
            }
            if (st.isAir()) return;
            topY = pos.getY();
        }

        double pxBase = pos.getX() + 0.5;
        double py = topY + 1.001;
        double pzBase = pos.getZ() + 0.5;

        double radius = 0.75;
        for (int n = 0; n < SCUFFS_PER_TICK; n++) {
            double rx = (this.getRandom().nextDouble() * 2 - 1) * radius;
            double rz = (this.getRandom().nextDouble() * 2 - 1) * radius;
            sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st),
                    pxBase + rx, py, pzBase + rz, 1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playHurtSound(DamageSource source) {
    }

    @Override
    protected void tickDeath() {
        this.remove(RemovalReason.KILLED);
    }

    @Override
    protected void dropAllDeathLoot(DamageSource source) {
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public void animateHurt(float yaw) {
    }

    // --- Abduction helpers ---
    public boolean isAbductionActive() {
        return this.entityData.get(ABDUCTING);
    }

    public void setAbductionActive(boolean active) {
        if (this.level().isClientSide) {
            // Client trusts server state; do not manage cooldown client-side
            this.entityData.set(ABDUCTING, active);
            return;
        }
        boolean was = this.entityData.get(ABDUCTING);
        if (active) {
            if (getDarkMatter() <= 0) return; // insufficient fuel
            this.entityData.set(ABDUCTING, true);
        } else {
            this.entityData.set(ABDUCTING, false);
        }
    }

    public int getDarkMatter() { return this.entityData.get(DM_CUR); }
    public int getMaxDarkMatter() { return this.entityData.get(DM_MAX); }
    public void addDarkMatter(int delta) {
        int cur = this.entityData.get(DM_CUR);
        int max = this.entityData.get(DM_MAX);
        cur += delta;
        if (cur < 0) cur = 0;
        if (cur > max) cur = max;
        this.entityData.set(DM_CUR, cur);
    }

    public boolean isGlowEnabled() { return this.entityData.get(GLOW_ENABLED); }
    public boolean isDashing() { return this.entityData.get(DASHING); }
    public void setGlowEnabled(boolean on) { this.entityData.set(GLOW_ENABLED, on); }
    public void toggleGlow() { this.entityData.set(GLOW_ENABLED, !isGlowEnabled()); }

    // --- Pairing confirmation blink ---
    private int blinkFlipsLeft = 0;
    private int blinkCooldown = 0;
    private boolean blinkRestore;
    private static final int BLINK_INTERVAL_TICKS = 6;

    public void requestConfirmBlink(int cycles) {
        if (cycles <= 0) return;
        this.blinkRestore = isGlowEnabled();
        this.blinkFlipsLeft = cycles * 2; // even number of flips to restore state
        this.blinkCooldown = 1; // start next tick for responsiveness
    }

    private void processConfirmBlink() {
        if (blinkFlipsLeft <= 0) return;
        if (--blinkCooldown <= 0) {
            toggleGlow();
            blinkFlipsLeft--;
            blinkCooldown = BLINK_INTERVAL_TICKS;
            if (blinkFlipsLeft <= 0) {
                setGlowEnabled(blinkRestore); // ensure original state
            }
        }
    }

    private void processAbduction() {
        if (!isAbductionActive()) return;
        net.minecraft.server.level.ServerPlayer pilot = getPilot();
        if (pilot == null) return;
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;

        double baseY = groundYAt(this.getX(), this.getZ()) + 0.5;
        double topY = this.getY() - 0.5;
        if (topY <= baseY) return;

        AABB box = new AABB(
                this.getX() - ABDUCT_RADIUS, baseY, this.getZ() - ABDUCT_RADIUS,
                this.getX() + ABDUCT_RADIUS, topY, this.getZ() + ABDUCT_RADIUS
        );

        var victims = sl.getEntitiesOfClass(LivingEntity.class, box, le -> !le.isRemoved() && le.isAlive());
        for (LivingEntity le : victims) {
            if (le.getId() == this.pilotId) continue; // skip pilot
            if (le instanceof net.minecraft.server.level.ServerPlayer sp) {
                if (sp.isCreative() || sp.isSpectator()) continue; // ignore creative/spectator
            }

            // Exclusions: do not affect other saucers, gray aliens, the wither, or the ender dragon
            if (le instanceof FlyingSaucerEntity) continue;
            if (le instanceof GrayAlienEntity) continue;
            if (le instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) continue;
            if (le instanceof net.minecraft.world.entity.boss.wither.WitherBoss) continue;

            // Pull towards center and lift up
            Vec3 toCenter = new Vec3(this.getX() - le.getX(), 0.0, this.getZ() - le.getZ());
            Vec3 dv = le.getDeltaMovement();
            Vec3 horiz = toCenter.lengthSqr() > 1e-4 ? toCenter.normalize().scale(0.1) : Vec3.ZERO;
            double vy = Math.max(dv.y, ABDUCT_LIFT_PER_TICK);
            le.setDeltaMovement(dv.x * 0.6 + horiz.x, vy, dv.z * 0.6 + horiz.z);
            le.fallDistance = 0f;
            le.hasImpulse = true;

            // If close to saucer, finalize: mark and kill to route drops
            if (le.getY() >= topY - 0.5) {
                var tag = le.getPersistentData();
                tag.putBoolean("totm_abduct", true);
                tag.putUUID("totm_abduct_player", pilot.getUUID());
                le.hurt(sl.damageSources().playerAttack(pilot), Float.MAX_VALUE);
            }
        }
    }
}


