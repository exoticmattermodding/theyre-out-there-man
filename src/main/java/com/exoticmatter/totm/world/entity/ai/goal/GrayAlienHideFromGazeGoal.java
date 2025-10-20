package com.exoticmatter.totm.world.entity.ai.goal;

import com.exoticmatter.totm.world.entity.GrayAlienEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

public class GrayAlienHideFromGazeGoal extends Goal {
    private final GrayAlienEntity alien;
    private final double speedModifier;
    private final double triggerRange;

    private Player watcher;
    private Vec3 targetPos;
    private int recalcCooldown;
    private boolean hidingMode; // true = hide fully; false = peek while keeping LoS
    private double prevDist;

    // Cosine thresholds for "looking" and "crosshair on"
    private static final double LOOK_COS_THRESHOLD = 0.85;   // ~31 degrees (more permissive)
    private static final double AIM_COS_THRESHOLD  = 0.999;  // ~2.6 degrees (more sensitive)

    public GrayAlienHideFromGazeGoal(GrayAlienEntity alien, double speedModifier, double triggerRange) {
        this.alien = alien;
        this.speedModifier = speedModifier;
        this.triggerRange = triggerRange;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (alien.isNoAi() || alien.isDeadOrDying()) return false;
        long calmUntil = alien.getPersistentData().getLong("totm_calm_until");
        if (calmUntil > 0 && alien.level().getGameTime() < calmUntil) return false; // remain calm briefly after interaction
        if (alien.getPersistentData().getBoolean("totm_lured_skull")) return false; // do not hide when lured by skull
        if (alien.getPersistentData().getBoolean("totm_mission_return")) return false; // priority: return to saucer
        Player nearest = alien.level().getNearestPlayer(alien, triggerRange);
        if (nearest == null) return false;
        // If player is holding or wearing an alien skull, alien will not hide
        var head = com.exoticmatter.totm.registry.ModItems.ALIEN_HEAD.get();
        if (head != null) {
            boolean holding = nearest.getMainHandItem().is(head) || nearest.getOffhandItem().is(head);
            boolean wearing = nearest.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(head);
            if (holding || wearing) return false;
        }
        boolean forceHide = alien.getPersistentData().getBoolean("totm_force_hide");
        boolean forcePeek = alien.getPersistentData().getBoolean("totm_force_peek");
        if (!forceHide && !forcePeek) {
            if (!nearest.hasLineOfSight(alien)) return false;
        }

        Vec3 toAlien = alien.getEyePosition().subtract(nearest.getEyePosition()).normalize();
        double dot = nearest.getLookAngle().normalize().dot(toAlien);
        if (!forceHide && !forcePeek) {
            if (dot < LOOK_COS_THRESHOLD) return false;
        }

        // Decide if we must hide urgently or can peek
        this.hidingMode = forceHide || (dot >= AIM_COS_THRESHOLD && !forcePeek);
        Vec3 found = this.hidingMode ? findHidePosition(nearest) : findPeekPosition(nearest);
        if (found == null) {
            // fallback: try the other mode before giving up
            found = this.hidingMode ? findPeekPosition(nearest) : findHidePosition(nearest);
        }
        if (found == null) return false;

        this.watcher = nearest;
        this.targetPos = found;
        this.prevDist = alien.distanceTo(nearest);
        return true;
    }

    @Override
    public void start() {
        if (targetPos != null) {
            this.alien.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, this.speedModifier);
        }
        this.recalcCooldown = 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (watcher == null) return false;
        if (!watcher.isAlive()) return false;
        if (alien.distanceToSqr(watcher) > (triggerRange * triggerRange)) return false;
        long calmUntil = alien.getPersistentData().getLong("totm_calm_until");
        if (calmUntil > 0 && alien.level().getGameTime() < calmUntil) return false;

        // Recompute attention
        Vec3 toAlien = alien.getEyePosition().subtract(watcher.getEyePosition()).normalize();
        double dot = watcher.getLookAngle().normalize().dot(toAlien);
        boolean forceHide = alien.getPersistentData().getBoolean("totm_force_hide");
        boolean forcePeek = alien.getPersistentData().getBoolean("totm_force_peek");
        if (!forceHide && !forcePeek) {
            if (dot < LOOK_COS_THRESHOLD && this.alien.getNavigation().isDone()) return false;
            if (this.hidingMode && !watcher.hasLineOfSight(alien) && this.alien.getNavigation().isDone()) return false; // already hidden successfully
        }

        return true;
    }

    @Override
    public void tick() {
        if (watcher == null) return;

        // Update mode based on current aim
        boolean urgent = isAimingAtMe(watcher);
        boolean forceHide2 = alien.getPersistentData().getBoolean("totm_force_hide");
        boolean forcePeek2 = alien.getPersistentData().getBoolean("totm_force_peek");
        this.hidingMode = forceHide2 || (urgent && !forcePeek2);

        if (urgent || --recalcCooldown <= 0) {
            recalcCooldown = urgent ? 5 : 20; // refresh more often when aimed at
            Vec3 newPos = this.hidingMode ? findHidePosition(watcher) : findPeekPosition(watcher);
            if (newPos != null) {
                targetPos = newPos;
                this.alien.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, urgent ? this.speedModifier * 1.25D : this.speedModifier);
            } else if (forcePeek2) {
                // Fallback peeking movement when no corner found: sidestep while facing player
                Vec3 to = new Vec3(alien.getX() - watcher.getX(), 0.0, alien.getZ() - watcher.getZ());
                if (to.lengthSqr() < 1.0e-4) to = new Vec3(1,0,0);
                Vec3 perp = new Vec3(-to.z, 0, to.x).normalize();
                Vec3 cand = new Vec3(alien.getX(), alien.getY(), alien.getZ()).add(perp.scale(4.0));
                this.alien.getNavigation().moveTo(cand.x, cand.y, cand.z, this.speedModifier);
            }
        }

        // Peek behavior: look at the player only when not in hiding mode
        if (!this.hidingMode) {
            this.alien.getLookControl().setLookAt(watcher, 30.0f, 30.0f);
        }

        // Update debug behavior tag every tick
        this.alien.getPersistentData().putString("totm_behavior", this.hidingMode ? "hiding" : "peeking");

        // If the player is approaching, increase urgency/speed even when peeking
        double d = alien.distanceTo(watcher);
        boolean approaching = d < prevDist - 0.2;
        prevDist = d;
        if (!this.hidingMode && approaching) {
            this.alien.getNavigation().setSpeedModifier(this.speedModifier * 1.35D);
        }

        // Sprint and occasional jump while fleeing or urgently hiding to look more intelligent
        boolean fleeing = this.hidingMode || approaching || urgent;
        this.alien.setSprinting(fleeing);
        if (fleeing && this.alien.onGround() && this.alien.getRandom().nextInt(6) == 0) {
            this.alien.getJumpControl().jump();
        }
    }

    @Override
    public void stop() {
        this.watcher = null;
        this.targetPos = null;
    }

    private boolean isAimingAtMe(Player player) {
        if (!player.hasLineOfSight(alien)) return false;
        Vec3 toAlien = alien.getEyePosition().subtract(player.getEyePosition()).normalize();
        double dot = player.getLookAngle().normalize().dot(toAlien);
        return dot >= AIM_COS_THRESHOLD;
    }

    private Vec3 findHidePosition(Player player) {
        Level level = alien.level();

        Vec3 playerToAlien = new Vec3(
                alien.getX() - player.getX(),
                0.0,
                alien.getZ() - player.getZ()
        ).normalize();
        if (playerToAlien.lengthSqr() < 1.0e-4) {
            playerToAlien = new Vec3(1, 0, 0);
        }
        Vec3 perpLeft = new Vec3(-playerToAlien.z, 0, playerToAlien.x);
        Vec3 perpRight = perpLeft.scale(-1);

        // Try a handful of candidate offsets: behind and sidesteps, increasing radius
        double[] radii = new double[] { 6.0, 8.0, 10.0 };
        Vec3[] basis = new Vec3[] { playerToAlien, perpLeft, perpRight };

        double currentDist2 = player.distanceToSqr(alien);
        for (double r : radii) {
            for (Vec3 b : basis) {
                // Try center and slight side offsets to use natural cover
                Vec3[] offsets = new Vec3[] {
                        b.scale(r),
                        b.scale(r).add(perpLeft.scale(1.5)),
                        b.scale(r).add(perpRight.scale(1.5))
                };
                for (Vec3 off : offsets) {
                    Vec3 candidate = new Vec3(
                            alien.getX() + off.x,
                            floorToWalkableY(alien.getBlockY()),
                            alien.getZ() + off.z
                    );
                    // Avoid moving toward player; ensure distance increases
                    if (player.distanceToSqr(candidate.x, alien.getY(), candidate.z) <= currentDist2) continue;
                    if (!isReachable(candidate)) continue;
                    if (isLineOfSightBlocked(player, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        // Fallback: just move away from the player; may not block LoS but creates distance
        Vec3 fallback = new Vec3(
                alien.getX() + playerToAlien.x * 10.0,
                floorToWalkableY(alien.getBlockY()),
                alien.getZ() + playerToAlien.z * 10.0
        );
        if (player.distanceToSqr(fallback.x, alien.getY(), fallback.z) <= currentDist2) {
            fallback = new Vec3(
                    alien.getX() + playerToAlien.x * 14.0,
                    floorToWalkableY(alien.getBlockY()),
                    alien.getZ() + playerToAlien.z * 14.0
            );
        }
        return isReachable(fallback) ? fallback : null;
    }

    private Vec3 findPeekPosition(Player player) {
        // Try to find a reachable position near an occluding block edge that still keeps LoS to the player.
        Vec3 dirToPlayer = new Vec3(
                player.getX() - alien.getX(),
                0.0,
                player.getZ() - alien.getZ()
        ).normalize();
        if (dirToPlayer.lengthSqr() < 1.0e-4) dirToPlayer = new Vec3(1, 0, 0);
        Vec3 perpLeft = new Vec3(-dirToPlayer.z, 0, dirToPlayer.x);
        Vec3 perpRight = perpLeft.scale(-1);

        double[] radii = new double[] { 4.0, 6.0, 8.0 };
        Vec3[] laterals = new Vec3[] { perpLeft, perpRight };

        for (double r : radii) {
            for (Vec3 lateral : laterals) {
                Vec3 off = lateral.scale(r * 0.8).add(dirToPlayer.scale(-0.5)); // hug cover slightly behind the edge
                Vec3 candidate = new Vec3(
                        alien.getX() + off.x,
                        floorToWalkableY(alien.getBlockY()),
                        alien.getZ() + off.z
                );
                if (!isReachable(candidate)) continue;

                // Must have LoS to player to maintain eye contact
                if (isLineOfSightBlocked(player, candidate)) continue;
                // Avoid moving closer if too close already
                if (player.distanceToSqr(candidate.x, alien.getY(), candidate.z) < player.distanceToSqr(alien)) continue;

                // Check that stepping a bit back is blocked (indicating a corner/edge to peek from)
                Vec3 tucked = candidate.add(dirToPlayer.scale(-0.8));
                if (!isLineOfSightBlocked(player, tucked)) continue;

                // Also ensure there is a solid block near the tucked side to act as cover
                BlockPos coverPos = BlockPos.containing(candidate.add(dirToPlayer.scale(-1.0)));
                if (!alien.level().getBlockState(coverPos).isSolidRender(alien.level(), coverPos)) continue;

                return candidate;
            }
        }

        // If forced peek, allow a near-side position even if no perfect corner found
        if (alien.getPersistentData().getBoolean("totm_force_peek")) {
            Vec3 dirToPlayer2 = new Vec3(
                    player.getX() - alien.getX(), 0.0, player.getZ() - alien.getZ()
            ).normalize();
            Vec3 side = new Vec3(-dirToPlayer2.z, 0, dirToPlayer2.x).scale(4.0);
            Vec3 cand = new Vec3(alien.getX() + side.x, floorToWalkableY(alien.getBlockY()), alien.getZ() + side.z);
            return isReachable(cand) ? cand : null;
        }
        return null;
    }

    private boolean isReachable(Vec3 target) {
        BlockPos pos = BlockPos.containing(target);
        if (!isSafePosition(pos)) return false;
        Path path = this.alien.getNavigation().createPath(pos, 0);
        return path != null;
    }

    private boolean isSafePosition(BlockPos pos) {
        var level = alien.level();
        var state = level.getBlockState(pos);
        var below = level.getBlockState(pos.below());
        if (!state.getFluidState().isEmpty() || !below.getFluidState().isEmpty()) return false;
        if (state.is(net.minecraft.world.level.block.Blocks.FIRE) || state.is(net.minecraft.world.level.block.Blocks.LAVA)) return false;
        if (below.is(net.minecraft.world.level.block.Blocks.LAVA) || below.is(net.minecraft.world.level.block.Blocks.FIRE)) return false;
        if (state.is(net.minecraft.world.level.block.Blocks.CACTUS)) return false;
        return true;
    }

    private boolean isLineOfSightBlocked(Player player, Vec3 target) {
        Level level = alien.level();
        Vec3 start = player.getEyePosition();
        Vec3 end = new Vec3(target.x, target.y + alien.getEyeHeight(alien.getPose()), target.z);
        HitResult res = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return res.getType() == HitResult.Type.BLOCK;
    }

    private double floorToWalkableY(int y) {
        // Keep Y stable with a slight bias downward to favor ground paths
        return Mth.floor(y);
    }
}
