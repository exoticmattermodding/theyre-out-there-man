package com.exoticmatter.totm.client.sound;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class SaucerDashLoopSound extends AbstractTickableSoundInstance {
    private final FlyingSaucerEntity saucer;

    public SaucerDashLoopSound(FlyingSaucerEntity saucer, SoundEvent event) {
        super(event, SoundSource.PLAYERS, Minecraft.getInstance().level.random);
        this.saucer = saucer;
        this.looping = true;
        this.delay = 0;
        this.x = (float) saucer.getX();
        this.y = (float) saucer.getY();
        this.z = (float) saucer.getZ();
        this.volume = 1.0f;
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        if (saucer == null || saucer.isRemoved() || saucer.isDeadOrDying() || !saucer.isDashing()) {
            this.stop();
            return;
        }
        this.x = (float) saucer.getX();
        this.y = (float) saucer.getY();
        this.z = (float) saucer.getZ();
        // Slightly increase pitch with speed
        double spd = saucer.getDeltaMovement().length();
        this.pitch = 1.0f + (float)Math.min(0.25, spd * 0.5);
    }
}

