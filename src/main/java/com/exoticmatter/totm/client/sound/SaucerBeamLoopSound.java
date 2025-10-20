package com.exoticmatter.totm.client.sound;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class SaucerBeamLoopSound extends AbstractTickableSoundInstance {
    private final FlyingSaucerEntity saucer;

    public SaucerBeamLoopSound(FlyingSaucerEntity saucer, SoundEvent event) {
        super(event, SoundSource.AMBIENT, Minecraft.getInstance().level.random);
        this.saucer = saucer;
        this.looping = true;
        this.delay = 0;
        this.x = (float) saucer.getX();
        this.y = (float) saucer.getY();
        this.z = (float) saucer.getZ();
        this.volume = 0.7f;
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        if (saucer == null || saucer.isRemoved() || saucer.isDeadOrDying() || !saucer.isAbductionActive()) {
            this.stop();
            return;
        }
        this.x = (float) saucer.getX();
        this.y = (float) saucer.getY();
        this.z = (float) saucer.getZ();
    }
}

