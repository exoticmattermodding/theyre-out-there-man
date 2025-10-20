package com.exoticmatter.totm.world.entity.ai.goal;

import com.exoticmatter.totm.world.entity.GrayAlienEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * When a nearby player is holding an alien skull, approach curiously
 * but maintain a respectful distance.
 */
public class GrayAlienApproachSkullHolderGoal extends Goal {
    private final GrayAlienEntity alien;
    private final double speed;
    private final double triggerRange;
    private final double stopMin;
    private final double stopMax;

    private Player target;
    private int recalc;

    public GrayAlienApproachSkullHolderGoal(GrayAlienEntity alien, double speed, double triggerRange, double stopMin, double stopMax) {
        this.alien = alien;
        this.speed = speed;
        this.triggerRange = triggerRange;
        this.stopMin = stopMin;
        this.stopMax = stopMax;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (alien.isNoAi() || alien.isDeadOrDying()) return false;
        // If lured by dropped skull, that behavior takes precedence
        if (alien.getPersistentData().getBoolean("totm_lured_skull")) return false;
        Player p = alien.level().getNearestPlayer(alien, this.triggerRange);
        if (p == null) return false;
        var head = com.exoticmatter.totm.registry.ModItems.ALIEN_HEAD.get();
        if (head == null) return false;
        boolean holding = p.getMainHandItem().is(head) || p.getOffhandItem().is(head);
        if (!holding) return false;
        this.target = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive()) return false;
        if (alien.distanceToSqr(target) > triggerRange * triggerRange) return false;
        var head = com.exoticmatter.totm.registry.ModItems.ALIEN_HEAD.get();
        if (head == null) return false;
        boolean holding = target.getMainHandItem().is(head) || target.getOffhandItem().is(head);
        return holding;
    }

    @Override
    public void start() {
        this.recalc = 0;
    }

    @Override
    public void tick() {
        if (target == null) return;
        // Always look at the player
        alien.getLookControl().setLookAt(target, 30.0f, 30.0f);
        alien.getPersistentData().putString("totm_behavior", "approach_skull_holder");

        if (--recalc <= 0) {
            recalc = 10; // adjust path periodically
            double d = alien.distanceTo(target);
            Vec3 toAlien = new Vec3(alien.getX() - target.getX(), 0.0, alien.getZ() - target.getZ());
            if (toAlien.lengthSqr() < 1.0e-6) {
                toAlien = new Vec3(1, 0, 0);
            }
            Vec3 dir = toAlien.normalize();

            double desired = (stopMin + stopMax) * 0.5;
            if (d < stopMin) desired = stopMax;           // back off a bit
            else if (d > stopMax) desired = stopMin;      // come closer

            Vec3 dest = new Vec3(
                    target.getX() + dir.x * desired,
                    alien.getY(),
                    target.getZ() + dir.z * desired
            );
            alien.getNavigation().moveTo(dest.x, dest.y, dest.z, this.speed);
        }
    }

    @Override
    public void stop() {
        this.target = null;
    }
}

