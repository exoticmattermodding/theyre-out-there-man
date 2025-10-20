package com.exoticmatter.totm.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "totm");

    private static RegistryObject<SoundEvent> event(String id) {
        return REGISTER.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("totm", id)));
    }

    public static final RegistryObject<SoundEvent> SAUCER_MOUNT_THEREMIN = event("saucer.mount_theremin");
    public static final RegistryObject<SoundEvent> SAUCER_ENGINE = event("saucer.engine");
    public static final RegistryObject<SoundEvent> SAUCER_BEAM_LOOP = event("saucer.beam_loop");
    public static final RegistryObject<SoundEvent> SAUCER_DASH = event("saucer.dash");
    public static final RegistryObject<SoundEvent> SAUCER_DASH_LOOP = event("saucer.dash_loop");
}
