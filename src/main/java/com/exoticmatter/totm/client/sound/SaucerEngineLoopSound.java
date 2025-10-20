package com.exoticmatter.totm.client.sound;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class SaucerEngineLoopSound extends AbstractTickableSoundInstance {
    private final FlyingSaucerEntity saucer;

    public SaucerEngineLoopSound(FlyingSaucerEntity saucer, SoundEvent event) {
        super(event, SoundSource.PLAYERS, Minecraft.getInstance().level.random);
        this.saucer = saucer;
        this.looping = true;
        this.delay = 0;
        this.x = (float) saucer.getX();
        this.y = (float) saucer.getY();
        this.z = (float) saucer.getZ();
        this.volume = 0.95f; // louder default engine hum
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        if (saucer == null || saucer.isRemoved() || saucer.isDeadOrDying()) {
            this.stop();
            return;
        }
        // Follow the saucer
        this.x = (float) saucer.getX();
        this.y = (float) saucer.getY();
        this.z = (float) saucer.getZ();
        // Subtle doppler-like pitch with speed
        double spd = saucer.getDeltaMovement().length();
        this.pitch = 0.95f + (float)Math.min(0.15, spd * 0.25);
    }
}
