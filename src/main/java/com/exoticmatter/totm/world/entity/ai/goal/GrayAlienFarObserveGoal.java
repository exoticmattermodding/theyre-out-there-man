package com.exoticmatter.totm.world.entity.ai.goal;

import com.exoticmatter.totm.world.entity.GrayAlienEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class GrayAlienFarObserveGoal extends Goal {
    private final GrayAlienEntity alien;
    private final double fleeSpeed;
    private Player target;
    private double prevDist;

    public GrayAlienFarObserveGoal(GrayAlienEntity alien, double fleeSpeed) {
        this.alien = alien;
        this.fleeSpeed = fleeSpeed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (alien.isNoAi() || alien.isDeadOrDying()) return false;
        long calmUntil = alien.getPersistentData().getLong("totm_calm_until");
        if (calmUntil > 0 && alien.level().getGameTime() < calmUntil) return false;
        Player p = alien.level().getNearestPlayer(alien, 96.0);
        if (p == null) return false;
        // Do not run if player is enticing with an alien skull (holding or wearing)
        var head = com.exoticmatter.totm.registry.ModItems.ALIEN_HEAD.get();
        if (head != null) {
            boolean holding = p.getMainHandItem().is(head) || p.getOffhandItem().is(head);
            boolean wearing = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(head);
            if (holding || wearing) return false;
        }
        double d2 = alien.distanceToSqr(p);
        if (d2 < 48.0 * 48.0) return false; // too close, other goals handle
        if (!p.hasLineOfSight(alien)) return false; // must be visible to the player
        this.target = p;
        this.prevDist = Math.sqrt(d2);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive()) return false;
        long calmUntil = alien.getPersistentData().getLong("totm_calm_until");
        if (calmUntil > 0 && alien.level().getGameTime() < calmUntil) return false;
        double d = alien.distanceTo(target);
        if (d < 40.0) return false; // hand off to close behaviors
        if (!target.hasLineOfSight(alien)) return false;
        return true;
    }

    @Override
    public void start() {
        this.alien.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) return;
        double d = alien.distanceTo(target);

        // Always stare when far
        alien.getLookControl().setLookAt(target, 30.0f, 30.0f);
        alien.getPersistentData().putString("totm_behavior", "far_observe");

        boolean approaching = d < prevDist - 0.25;
        prevDist = d;

        if (approaching) {
            // Flee away from the player
            Vec3 away = new Vec3(alien.getX() - target.getX(), 0.0, alien.getZ() - target.getZ()).normalize();
            Vec3 dest = new Vec3(alien.getX() + away.x * 16.0, alien.getY(), alien.getZ() + away.z * 16.0);
            this.alien.getNavigation().moveTo(dest.x, dest.y, dest.z, this.fleeSpeed);
        } else {
            // Stand still and observe
            this.alien.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        this.target = null;
    }
}
