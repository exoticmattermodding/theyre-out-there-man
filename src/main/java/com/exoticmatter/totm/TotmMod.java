package com.exoticmatter.totm;

import com.exoticmatter.totm.registry.ModEntities;
import com.exoticmatter.totm.registry.ModItems;
import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TotmMod.MODID)
public class TotmMod {
    public static final String MODID = "totm";

    public TotmMod() {

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Lifecycle listeners
        modBus.addListener(this::registerAttributes);

        // Deferred registers
        ModItems.REGISTER.register(modBus);
        ModEntities.ENTITIES.register(modBus);
    }


    // Hook attributes at load-time on the MOD bus
    private void registerAttributes(final EntityAttributeCreationEvent event) {
        event.put(ModEntities.FLYING_SAUCER.get(),
                FlyingSaucerEntity.createAttributes().build());
    }
}