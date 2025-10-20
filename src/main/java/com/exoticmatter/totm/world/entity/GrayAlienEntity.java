package com.exoticmatter.totm.world.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import com.exoticmatter.totm.world.entity.ai.goal.GrayAlienHideFromGazeGoal;
import com.exoticmatter.totm.world.entity.ai.goal.GrayAlienFarObserveGoal;

public class GrayAlienEntity extends Mob {
    public GrayAlienEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
        // Strongly avoid water and common hazards in pathfinding
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_FIRE, 16.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.LAVA, -1.0F);
        // Cactus-specific path type may not exist on 1.20.1; avoid via safe-position checks instead
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.POWDER_SNOW, 8.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.33D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // If a player holds an alien skull, approach curiously and keep distance
        this.goalSelector.addGoal(0, new com.exoticmatter.totm.world.entity.ai.goal.GrayAlienApproachSkullHolderGoal(this, 0.95D, 64.0D, 5.5D, 8.5D));
        // Hide from direct gaze when not enticed by skull
        this.goalSelector.addGoal(1, new GrayAlienHideFromGazeGoal(this, 1.25D, 32.0D));
        // Observe player from afar, flee only if they close distance and not holding skull
        this.goalSelector.addGoal(2, new GrayAlienFarObserveGoal(this, 1.4D));
        // Always try to look at nearby players when not hiding
        this.goalSelector.addGoal(8, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 48.0F));
        this.goalSelector.addGoal(9, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) { }
    @Override public void addAdditionalSaveData(CompoundTag tag) { }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return null; }
    @Override protected SoundEvent getDeathSound() { return null; }

    @Override
    public void tick() {
        super.tick();
        // Handle abduction despawn at dawn
        if (!level().isClientSide) {
            var data = this.getPersistentData();
            if (data.getBoolean("totm_abduction") && level().isDay()) {
                for (int i = 0; i < 20; i++) {
                    double ox = (random.nextDouble() - 0.5) * 0.5;
                    double oy = random.nextDouble() * 0.5 + 0.2;
                    double oz = (random.nextDouble() - 0.5) * 0.5;
                    ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                            getX() + ox, getY() + 1.0 + oy, getZ() + oz,
                            1, 0, 0, 0, 0.0);
                }
                this.discard();
            }

            // Abduction scripted behavior (stay near end of bed, face player)
            if (data.getBoolean("totm_abduction") && !level().isDay()) {
                int stage = data.getInt("totm_abd_stage");
                int timer = data.getInt("totm_abd_timer");
                java.util.UUID targetId = data.hasUUID("totm_target") ? data.getUUID("totm_target") : null;
                net.minecraft.world.entity.player.Player target = null;
                if (targetId != null && this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    var e = sl.getEntity(targetId);
                    if (e instanceof net.minecraft.world.entity.player.Player p) target = p;
                }
                if (target != null) this.getLookControl().setLookAt(target, 30.0f, 30.0f);
                this.getNavigation().stop();
                this.setDeltaMovement(0, this.getDeltaMovement().y, 0);

                double bx = data.getInt("totm_bed_x") + 0.5;
                double by = data.getInt("totm_bed_y");
                double bz = data.getInt("totm_bed_z") + 0.5;

                if (stage == 0) {
                    // Move to foot of bed
                    if (this.distanceToSqr(bx, this.getY(), bz) > 1.2 * 1.2) {
                        this.getNavigation().moveTo(bx, this.getY(), bz, 1.25);
                    } else {
                        data.putInt("totm_abd_stage", 1);
                        data.putInt("totm_abd_timer", 40); // wait 2 seconds before probing
                        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                    }
                } else if (stage == 1) {
                    if (timer > 0) {
                        timer--;
                        data.putInt("totm_abd_timer", timer);
                    } else {
                        // Bind the alien's probe to the target player and drop a copy
                        if (target != null && this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                            var probeItem = com.exoticmatter.totm.registry.ModItems.PROBE.get();
                            if (probeItem != null) {
                                if (!this.getMainHandItem().is(probeItem)) {
                                    this.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(probeItem));
                                }
                                var stack = this.getMainHandItem();
                                var tagProbe = stack.getOrCreateTag();
                                tagProbe.putBoolean("totm_bound", true);
                                tagProbe.putUUID("totm_bound_uuid", target.getUUID());
                                tagProbe.putString("totm_bound_type", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
                                var drop = new net.minecraft.world.entity.item.ItemEntity(sl, target.getX(), target.getY() + 0.5, target.getZ(), stack.copy());
                                drop.setDefaultPickUpDelay();
                                sl.addFreshEntity(drop);
                            }
                        }
                        data.putInt("totm_abd_stage", 2);
                    }
                }
            }

            // Simple mission: after a delay, return to a saucer and despawn when close
            if (data.getBoolean("totm_mission_return")) {
                int after = data.getInt("totm_return_after");
                if (after > 0) {
                    data.putInt("totm_return_after", after - 1);
                } else {
                    int sid = data.getInt("totm_return_target");
                    if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        var e = sl.getEntity(sid);
                        if (e instanceof com.exoticmatter.totm.world.entity.FlyingSaucerEntity s) {
                            this.getNavigation().moveTo(s.getX(), s.getY(), s.getZ(), 1.3);
                            if (this.distanceTo(s) < 3.0) {
                                this.discard();
                            }
                        }
                    }
                }
            }

            // Mission: probe a cow if nearby, then return
            if (data.getBoolean("totm_mission_probe_cow")) {
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    var cows = sl.getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, this.getBoundingBox().inflate(16, 6, 16));
                    if (!cows.isEmpty()) {
                        var cow = cows.get(this.random.nextInt(cows.size()));
                        this.getNavigation().moveTo(cow.getX(), cow.getY(), cow.getZ(), 1.3);
                        if (this.distanceTo(cow) < 2.2) {
                            this.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                            cow.addTag("totm_alien_cow");
                            cow.getPersistentData().putBoolean("totm_alien_cow", true);
                            com.exoticmatter.totm.network.ModNetwork.CHANNEL.send(
                                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> cow),
                                    new com.exoticmatter.totm.network.packet.AlienCowSkinS2CPacket(cow.getId(), true)
                            );
                            data.remove("totm_mission_probe_cow");
                            data.putInt("totm_return_after", 20 * 2);
                            data.putBoolean("totm_mission_return", true);
                        }
                    } else {
                        // No cows around; time out the probe task
                        data.remove("totm_mission_probe_cow");
                    }
                }
            }

            // Always be interested in picking up a Probe item
            if (this.tickCount % 40 == 0 && this.getMainHandItem().isEmpty()) {
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    var probe = com.exoticmatter.totm.registry.ModItems.PROBE.get();
                    var items = sl.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, this.getBoundingBox().inflate(12, 4, 12), it -> it.getItem().getItem() == probe);
                    if (!items.isEmpty()) {
                        var it = items.get(0);
                        this.getNavigation().moveTo(it.getX(), it.getY(), it.getZ(), 1.2);
                        if (this.distanceTo(it) < 1.6) {
                            var stack = it.getItem();
                            this.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack.copyWithCount(1));
                            stack.shrink(1);
                            if (stack.isEmpty()) it.discard();
                        }
                    }
                }
            }

            // If a nearby player is holding an alien skull, show alien tech in hand
            if (this.tickCount % 10 == 0) {
                var head = com.exoticmatter.totm.registry.ModItems.ALIEN_HEAD.get();
                var tech = com.exoticmatter.totm.registry.ModItems.ALIEN_TECH.get();
                if (head != null && tech != null) {
                    var p = level().getNearestPlayer(this, 32.0);
                    boolean show = false;
                    if (p != null) {
                        show = p.getMainHandItem().is(head) || p.getOffhandItem().is(head);
                    }
                    if (show) {
                        if (!this.getMainHandItem().is(tech)) {
                            this.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(tech));
                        }
                    } else {
                        if (this.getMainHandItem().is(tech)) {
                            this.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                        }
                    }
                }
            }

            // If a player drops an alien skull near an alien, the alien will not hide, picks it up, and drops alien tech
            if (this.tickCount % 20 == 0) {
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    var skullItem = com.exoticmatter.totm.registry.ModItems.ALIEN_HEAD.get();
                    var skulls = sl.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, this.getBoundingBox().inflate(8, 3, 8), it -> it.getItem().getItem() == skullItem);
                    if (!skulls.isEmpty()) {
                        data.putBoolean("totm_lured_skull", true);
                        var sk = skulls.get(0);
                        // Approach calmly
                        this.getNavigation().moveTo(sk.getX(), sk.getY(), sk.getZ(), 0.9);
                        this.setSprinting(false);
                        if (this.distanceTo(sk) < 1.6) {
                            // Consume skull and drop alien tech
                            sk.discard();
                            var tech = new net.minecraft.world.item.ItemStack(com.exoticmatter.totm.registry.ModItems.ALIEN_TECH.get());
                            var ent = new net.minecraft.world.entity.item.ItemEntity(sl, this.getX(), this.getY(), this.getZ(), tech);
                            sl.addFreshEntity(ent);
                            // Remain calm for a few seconds before resuming evasive behavior
                            data.putLong("totm_calm_until", sl.getGameTime() + 100L); // ~5s
                            data.remove("totm_lured_skull");
                        }
                    } else {
                        data.remove("totm_lured_skull");
                    }
                }
            }

            // Occasional scare tactic: dart closer briefly
            if (this.tickCount % 200 == 0 && this.random.nextInt(20) == 0) {
                var p = this.level().getNearestPlayer(this, 32.0);
                if (p != null) {
                    double ang = Math.atan2(this.getZ() - p.getZ(), this.getX() - p.getX()) + (this.random.nextDouble()-0.5)*0.6;
                    double dx = p.getX() + Math.cos(ang) * 3.0;
                    double dz = p.getZ() + Math.sin(ang) * 3.0;
                    this.getNavigation().moveTo(dx, this.getY(), dz, 1.6);
                    // small sound cue
                    ((net.minecraft.server.level.ServerLevel)level()).playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_SCREAM, net.minecraft.sounds.SoundSource.HOSTILE, 0.5f, 1.2f);
                }
            }

            // Simple boids-like cohesion/avoidance with nearby aliens
            if (this.tickCount % 60 == 0) {
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    var others = sl.getEntitiesOfClass(GrayAlienEntity.class, this.getBoundingBox().inflate(10, 5, 10), a -> a != this);
                    if (!others.isEmpty()) {
                        double cx = 0, cz = 0; int n = 0;
                        for (var o : others) { cx += o.getX(); cz += o.getZ(); n++; }
                        cx /= n; cz /= n;
                        double toX = cx, toZ = cz;
                        double dist2 = (this.getX()-cx)*(this.getX()-cx) + (this.getZ()-cz)*(this.getZ()-cz);
                        if (dist2 > 16.0) {
                            this.getNavigation().moveTo(toX, this.getY(), toZ, 1.1);
                        }
                    }
                }
            }

            // If far from any player, occasionally mark a nearby cow as an alien cow variant (client re-skins)
            if (this.tickCount % 100 == 0) {
                var nearestPlayer = level().getNearestPlayer(this, 64.0);
                if (nearestPlayer == null || this.distanceToSqr(nearestPlayer) > 64.0 * 64.0) {
                    var aabb = this.getBoundingBox().inflate(12.0);
                    java.util.List<net.minecraft.world.entity.animal.Cow> cows = level().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, aabb);
                    if (!cows.isEmpty()) {
                        var cow = cows.get(this.random.nextInt(cows.size()));
                        cow.addTag("totm_alien_cow");
                        cow.getPersistentData().putBoolean("totm_alien_cow", true);
                        if (this.level() instanceof net.minecraft.server.level.ServerLevel sl2) {
                            com.exoticmatter.totm.network.ModNetwork.CHANNEL.send(
                                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> cow),
                                    new com.exoticmatter.totm.network.packet.AlienCowSkinS2CPacket(cow.getId(), true)
                            );
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source, int lootingMultiplier, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, lootingMultiplier, recentlyHit);
        var probe = com.exoticmatter.totm.registry.ModItems.PROBE.get();
        if (probe != null && this.getMainHandItem().getItem() == probe) {
            this.spawnAtLocation(new net.minecraft.world.item.ItemStack(probe));
        }
    }
}
