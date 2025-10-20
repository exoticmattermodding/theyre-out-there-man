package com.exoticmatter.totm.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "totm");

    public static final RegistryObject<SimpleParticleType> SPARK = REGISTER.register(
            "spark", () -> new SimpleParticleType(true)
    );
}

