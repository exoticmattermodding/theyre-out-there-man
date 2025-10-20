package com.exoticmatter.totm.world.block;

import com.exoticmatter.totm.TotmMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

// ModPaintings.java
public class ModPaintings {
    public static final DeferredRegister<PaintingVariant> PAINTINGS =
            DeferredRegister.create(Registries.PAINTING_VARIANT, TotmMod.MODID);

    public static final RegistryObject<PaintingVariant> IWTB =
            PAINTINGS.register("iwtb",
                    () -> new PaintingVariant(16, 32)); // Width & height in pixels

    public static final RegistryObject<PaintingVariant> CONSPIRACY =
            PAINTINGS.register("conspiracy",
                    () -> new PaintingVariant(32, 16)); // Width & height in pixels
}
