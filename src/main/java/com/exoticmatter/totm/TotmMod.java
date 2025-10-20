package com.exoticmatter.totm;

import com.exoticmatter.totm.registry.ModEntities;
import com.exoticmatter.totm.registry.ModItems;
import com.exoticmatter.totm.registry.ModBlocks;
import com.exoticmatter.totm.registry.ModCreativeTabs;
import com.exoticmatter.totm.network.ModNetwork;
import com.exoticmatter.totm.world.block.ModPaintings;
import com.exoticmatter.totm.world.block.entity.ModPotDecorations;
import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.util.Set;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

@Mod(TotmMod.MODID)
public class TotmMod {
    public static final String MODID = "totm";

    public TotmMod() {

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Lifecycle listeners
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::commonSetup);

        // Deferred registers
        ModItems.REGISTER.register(modBus);
        ModBlocks.REGISTER.register(modBus);
        ModCreativeTabs.REGISTER.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModPaintings.PAINTINGS.register(modBus);
        com.exoticmatter.totm.registry.ModParticles.REGISTER.register(modBus);
        com.exoticmatter.totm.registry.ModSounds.REGISTER.register(modBus);

        //Pot Pattern
        ModPotDecorations.POT_DECORATION_PATTERNS.register(modBus);

        // Config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.exoticmatter.totm.config.TotmConfig.SPEC);



        // Networking
        ModNetwork.init();
    }


    // Hook attributes at load-time on the MOD bus
    private void registerAttributes(final EntityAttributeCreationEvent event) {
        event.put(ModEntities.FLYING_SAUCER.get(),
                FlyingSaucerEntity.createAttributes().build());
        event.put(ModEntities.GRAY_ALIEN.get(),
                com.exoticmatter.totm.world.entity.GrayAlienEntity.createAttributes().build());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // ✅ Ensure our custom skull blocks are recognized by the SKULL BlockEntityType.
            // Forge 1.20.1 has no addBlocks API; use SRG reflection to extend validBlocks.
            try {
                Set<Block> valid = ObfuscationReflectionHelper.getPrivateValue(
                        BlockEntityType.class, BlockEntityType.SKULL, "f_58915_" // BlockEntityType.validBlocks
                );
                if (valid != null) {
                    java.util.HashSet<Block> replaced = new java.util.HashSet<>(valid);
                    replaced.add(ModBlocks.ALIEN_HEAD.get());
                    replaced.add(ModBlocks.ALIEN_WALL_HEAD.get());
                    ObfuscationReflectionHelper.setPrivateValue(
                            BlockEntityType.class, BlockEntityType.SKULL, replaced, "f_58915_"
                    );
                }
            } catch (Exception ex) {
                // As a fallback, do nothing; the skull may render missing if not registered.
                // Logging can be added if a logger is present.
            }

            // ✅ Register pot pattern mapping
            ModPotDecorations.registerPatternForItem(
                    ModItems.ALIEN_SHERD.get(),
                    ModPotDecorations.ALIEN_POTTERY_PATTERN
            );
            System.out.println("[TotmMod] Registered pot pattern mapping for ALIEN_SHERD");
        });
    }

}
