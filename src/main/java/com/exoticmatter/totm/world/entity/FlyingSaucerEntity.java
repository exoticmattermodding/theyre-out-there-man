package com.exoticmatter.totm.world.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.PlayerRideableJumping;

// ...package/imports unchanged...

public class FlyingSaucerEntity extends Mob implements PlayerRideableJumping {

    private static final double HOVER_OFFSET_IDLE   = 4.0;
    private static final double HOVER_OFFSET_RIDDEN = 20.0;

    private static final double HOVER_STRENGTH_RIDDEN = 0.18;
    private static final double HOVER_STRENGTH_IDLE   = 0.12;
    private static final double HOVER_DAMP            = 0.86;
    private static final double MAX_DESCENT_SPEED     = 0.10;

    private static final double BOB_AMPLITUDE_BASE    = 0.02;
    private static final double BOB_SPEED             = 0.12;
    private static final int    REV_WOBBLE_TICKS      = 10;
    private static final double REV_WOBBLE_MULT       = 7.0;

    private static final int    JUMP_COOLDOWN_TICKS   = 10;

    // Dash physics (no teleport)
    private static final int    BURN_TICKS_MIN        = 22;        // ~1.1s
    private static final int    BURN_TICKS_MAX        = 28;        // ~1.4s
    private static final double TARGET_SPEED_MIN      = 0.80;      // blocks/tick
    private static final double TARGET_SPEED_MAX      = 1.05;      // blocks/tick (~20 blocks total)
    private static final double ACCEL_GAIN_PER_TICK   = 0.22;      // approach target speed
    private static final double ABS_SPEED_CAP         = 1.12;      // hard cap for smoothness
    private static final double INITIAL_KICK_MIN      = 0.28;
    private static final double INITIAL_KICK_MAX      = 0.55;
    private static final double POST_BURN_DRAG        = 0.90;

    private static final int    SCUFFS_PER_TICK       = 10;

    private int   jumpCooldown = 0;

    private int   boostTicks   = 0;
    private Vec3  boostDir     = Vec3.ZERO;
    private double targetFwdSpeed  = 0.0;

    private int   heldMaxPower = 0;
    private float lastJumpPowerClient = 0f;
    private int   revWobbleLeft = 0;

    private final float wobblePhase;

    private Vec3 lastDashDir = new Vec3(0, 0, 1);

    public FlyingSaucerEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.wobblePhase = (float)(this.getRandom().nextFloat() * (float)(Math.PI * 2));
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.8D);
    }

    @Override
    public void tick() {
        super.tick();
        if (jumpCooldown > 0) jumpCooldown--;
        if (revWobbleLeft > 0) revWobbleLeft--;

        if (!level().isClientSide && boostTicks > 0) {
            var s = (net.minecraft.server.level.ServerLevel)this.level();
            Vec3 dir = boostDir.lengthSqr() > 1e-6 ? boostDir : this.getDeltaMovement();
            if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, 0, 1);
            Vec3 back = dir.normalize().scale(-0.35);
            s.sendParticles(ParticleTypes.END_ROD, this.getX()+back.x, this.getY()+0.2, this.getZ()+back.z,
                    12, 0.10, 0.10, 0.10, 0.0);
        }

        if (!level().isClientSide) spawnGroundScuffsAtAnyAltitudeUpTo20();
    }

    @Override protected void doPush(Entity e) {}
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean isPickable() { return true; }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) player.startRiding(this, true);
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override public double getPassengersRidingOffset() { return Math.max(0.5D, this.getBbHeight() * 0.5D); }
    @Override @Nullable public LivingEntity getControllingPassenger() {
        Entity first = this.getFirstPassenger();
        return first instanceof LivingEntity le ? le : null;
    }

    @Override
    public boolean canJump() {
        return this.isVehicle() && (this.level().isClientSide || this.jumpCooldown == 0);
    }


    @Override
    public void handleStartJump(int power) {
        if (level().isClientSide) {
            lastJumpPowerClient = power / 100f;
            if (power > heldMaxPower) heldMaxPower = power;
            revWobbleLeft = Math.min(revWobbleLeft + 1, REV_WOBBLE_TICKS);
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getVehicle() == this) {
                mc.level.playLocalSound(this.getX(), this.getY(), this.getZ(),
                        SoundEvents.CROSSBOW_QUICK_CHARGE_1, SoundSource.PLAYERS, 0.2f, 1.25f, false);
            }
        }
    }

    @Override
    public void handleStopJump() {
        // No client packet here; server will call onPlayerJump(power).
    }


    @Override
    public void onPlayerJump(int power) {
        if (level().isClientSide) return; // only the server applies motion

        // Choose a dash direction on the server.
        // Prefer the rider's current facing (flattened), fall back to the saucer's facing.
        Vec3 dir = null;
        LivingEntity rider = this.getControllingPassenger();
        if (rider != null) {
            Vec3 look = rider.getViewVector(1.0F);
            dir = new Vec3(look.x, 0.0, look.z); // keep level (no nose-dives)
        }
        if (dir == null || dir.lengthSqr() < 1.0e-6) {
            // Fallback: saucer forward from its yaw
            Vec3 fwd = Vec3.directionFromRotation(0.0F, this.getYRot());
            dir = new Vec3(fwd.x, 0.0, fwd.z);
        }

        // Re-use your existing server boost logic
        applySlingshotServer(power, dir);
    }



    // === SERVER-SIDE dash ===
    public void applySlingshotServer(int power, Vec3 dashDirFromPacket) {
        if (!this.isAlive()) return;

        // If horse bar never fired, power can be zero—give a sensible floor
        int pwr = Math.max(power, 60); // floor so the dash is visible

        Vec3 dir = (dashDirFromPacket != null && dashDirFromPacket.lengthSqr() > 1e-6)
                ? dashDirFromPacket.normalize()
                : new Vec3(0, 0, 1);

        double t = Mth.clamp(pwr / 100.0, 0.0, 1.0);
        double eased = t * t;

        this.boostDir       = dir;
        this.boostTicks     = (int)Math.round(Mth.lerp(eased, BURN_TICKS_MIN, BURN_TICKS_MAX));
        this.targetFwdSpeed = Mth.lerp(eased, TARGET_SPEED_MIN, TARGET_SPEED_MAX);

        double kick = Mth.lerp(eased, INITIAL_KICK_MIN, INITIAL_KICK_MAX);
        this.setDeltaMovement(this.getDeltaMovement().add(dir.scale(kick)));
        this.hasImpulse = true;

        this.fallDistance = 0f;
        this.jumpCooldown = JUMP_COOLDOWN_TICKS;

        level().playSound(null, this.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    // ---- Movement ----
    @Override
    public void travel(Vec3 travelVector) {
        LivingEntity rider = this.getControllingPassenger();
        final double time = (this.tickCount + this.wobblePhase);

        if (this.isVehicle() && rider != null) {
            this.setYRot(rider.getYRot());
            this.setXRot(rider.getXRot() * 0.5F);

            // Update lastDashDir from input (for steering between dashes)
            double strafe   = rider.xxa;
            double forward  = rider.zza;
            float speedAttr = (float)this.getAttributeValue(Attributes.MOVEMENT_SPEED);
            Vec3 moveLocal  = new Vec3(strafe, 0, forward);
            Vec3 moveWorld  = moveLocal.yRot((float)Math.toRadians(-this.getYRot()));
            if (moveWorld.lengthSqr() > 0.0004) lastDashDir = moveWorld.normalize();

            // If burning: take over horizontal motion entirely this tick (no blending can cancel it)
            if (boostTicks > 0) {
                Vec3 v = this.getDeltaMovement();

                // small steer toward last input while burning
                boostDir = (boostDir.lengthSqr() > 1e-6 ? boostDir.normalize() : lastDashDir)
                        .lerp(lastDashDir, 0.08f).normalize();

                // accelerate toward forward target
                double fwd = v.dot(boostDir);
                double need = targetFwdSpeed - fwd;
                if (need > 0) {
                    double add = Math.min(need, ACCEL_GAIN_PER_TICK);
                    v = v.add(boostDir.scale(add));
                }

                // cap speed
                double spd = v.length();
                if (spd > ABS_SPEED_CAP) v = v.scale(ABS_SPEED_CAP / spd);

                // vertical hover still active (no wobble while burning)
                double ground = groundYAt(this.getX(), this.getZ());
                double targetY = ground + HOVER_OFFSET_RIDDEN;
                double vy      = v.y;
                double error   = targetY - this.getY();
                double pull    = HOVER_STRENGTH_RIDDEN * clamp(error, -2.0, 2.0);
                double vNewY   = lerp(0.35, vy, vy * HOVER_DAMP + pull);

                v = new Vec3(v.x, vNewY, v.z);

                this.setDeltaMovement(v);
                this.hasImpulse = true;
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setOnGround(false);
                this.fallDistance = 0;

                // floor clamp (ground + 4)
                double minY = ground + HOVER_OFFSET_IDLE;
                if (this.getY() < minY) {
                    this.setPos(this.getX(), minY, this.getZ());
                    if (this.getDeltaMovement().y < 0)
                        this.setDeltaMovement(this.getDeltaMovement().x, 0.0, this.getDeltaMovement().z);
                }

                boostTicks--;
                return; // <-- nothing else can overwrite our burn this tick
            }

            // Not burning: gentle drag + standard hover + WASD blend
            Vec3 v = this.getDeltaMovement().scale(POST_BURN_DRAG);

            double ground = groundYAt(this.getX(), this.getZ());
            double targetY = ground + HOVER_OFFSET_RIDDEN;
            double vy      = v.y;
            double error   = targetY - this.getY();
            double pull    = HOVER_STRENGTH_RIDDEN * clamp(error, -2.0, 2.0);

            double wobbleAmp = BOB_AMPLITUDE_BASE * (revWobbleLeft > 0 ? REV_WOBBLE_MULT : 1.0);
            double bob       = Math.sin(time * BOB_SPEED) * wobbleAmp;

            double vNewY = lerp(0.35, vy, vy * HOVER_DAMP + pull + bob);
            if (vNewY < -MAX_DESCENT_SPEED) vNewY = -MAX_DESCENT_SPEED;

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

            double minY = ground + HOVER_OFFSET_IDLE;
            if (this.getY() < minY) {
                this.setPos(this.getX(), minY, this.getZ());
                if (this.getDeltaMovement().y < 0)
                    this.setDeltaMovement(this.getDeltaMovement().x, 0.0, this.getDeltaMovement().z);
            }

        } else {
            // Unridden hover at +4
            Vec3 dm = this.getDeltaMovement();
            double ground = groundYAt(this.getX(), this.getZ());
            double targetY = ground + HOVER_OFFSET_IDLE;
            double error   = targetY - this.getY();
            double pull    = HOVER_STRENGTH_IDLE * clamp(error, -2.0, 2.0);
            double bob     = Math.sin(time * BOB_SPEED) * BOB_AMPLITUDE_BASE;
            double yNext   = dm.y * HOVER_DAMP + pull + bob;

            if (yNext < -MAX_DESCENT_SPEED) yNext = -MAX_DESCENT_SPEED;

            this.setDeltaMovement(dm.x * 0.9, yNext, dm.z * 0.9);
            this.hasImpulse = true;
            super.travel(travelVector);
            this.setOnGround(false);
            this.fallDistance = 0;

            double minY = ground + HOVER_OFFSET_IDLE;
            if (this.getY() < minY) {
                this.setPos(this.getX(), minY, this.getZ());
                Vec3 d2 = this.getDeltaMovement();
                if (d2.y < 0) this.setDeltaMovement(d2.x, 0.0, d2.z);
            }
        }
    }

    private static double lerp(double a, double from, double to) { return from + a * (to - from); }
    private static double clamp(double v, double min, double max) { return v < min ? min : (v > max ? max : v); }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        boolean pass = super.hurt(source, amount);
        if (!this.isAlive()) this.ejectPassengers();
        return pass;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (passenger != null) passenger.teleportTo(this.getX(), this.getY() + 0.5, this.getZ());
    }

    @Override
    public boolean isControlledByLocalInstance() {
        if (level().isClientSide) {
            var lp = Minecraft.getInstance().player;
            return lp != null && getControllingPassenger() == lp;
        }
        return super.isControlledByLocalInstance();
    }

    @Override protected boolean canRide(Entity entity) { return true; }
    @Override protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof LivingEntity;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private double groundYAt(double x, double z) {
        return this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int)Math.floor(x), (int)Math.floor(z));
    }

    private void spawnGroundScuffsAtAnyAltitudeUpTo20() {
        var sl = (net.minecraft.server.level.ServerLevel)this.level();
        int topY = (int)Math.floor(groundYAt(this.getX(), this.getZ())) - 1;
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
                if (!st2.isAir()) { pos = new BlockPos(pos.getX(), y, pos.getZ()); st = st2; break; }
            }
            if (st.isAir()) return;
            topY = pos.getY();
        }

        double pxBase = pos.getX() + 0.5;
        double py     = topY + 1.001;
        double pzBase = pos.getZ() + 0.5;

        double radius = 0.75;
        for (int n = 0; n < SCUFFS_PER_TICK; n++) {
            double rx = (this.getRandom().nextDouble() * 2 - 1) * radius;
            double rz = (this.getRandom().nextDouble() * 2 - 1) * radius;
            sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st),
                    pxBase + rx, py, pzBase + rz, 1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) { }
    @Override public void addAdditionalSaveData(CompoundTag tag) { }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return null; }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override protected void playHurtSound(DamageSource source) { }
    @Override protected void tickDeath() { this.remove(RemovalReason.KILLED); }
    @Override protected void dropAllDeathLoot(DamageSource source) { }
    @Override public boolean shouldDropExperience() { return false; }
    @Override public void animateHurt(float yaw) { }
}
