package com.exoticmatter.totm.mixin;

import com.exoticmatter.totm.client.render.SaucerCameraHandler;
import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow public abstract float getXRot();
    @Shadow public abstract float getYRot();
    @Shadow public abstract void move(double x, double y, double z);
    @Shadow public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void totm$extendThirdPerson(Level level, Entity entity, boolean thirdPerson, boolean inverseView, float partialTicks, CallbackInfo ci) {
        if (!thirdPerson) return;
        if (!(entity instanceof Player player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) return;
        if (!(player.getVehicle() instanceof FlyingSaucerEntity)) return;

        double push = Math.max(0.0, SaucerCameraHandler.getExtraPush());
        if (push <= 0.01) return;

        Vec3 camPos = this.getPosition();
        Vec3 look = Vec3.directionFromRotation(getXRot(), getYRot());
        Vec3 desired = camPos.add(look.scale(-push));

        HitResult hit = level.clip(new ClipContext(
                camPos,
                desired,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                entity
        ));
        if (hit.getType() != HitResult.Type.MISS) {
            desired = hit.getLocation().add(look.scale(0.3));
        }

        Vec3 offset = desired.subtract(camPos);
        this.move(offset.x, offset.y, offset.z);
    }
}

