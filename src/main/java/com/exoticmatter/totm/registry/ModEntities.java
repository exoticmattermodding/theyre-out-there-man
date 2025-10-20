package com.exoticmatter.totm.registry;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import com.exoticmatter.totm.world.entity.CrashedSaucerEntity;
import com.exoticmatter.totm.world.entity.GrayAlienEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "totm");

    public static final RegistryObject<EntityType<FlyingSaucerEntity>> FLYING_SAUCER =
            ENTITIES.register("flying_saucer",
                    () -> EntityType.Builder.<FlyingSaucerEntity>of(FlyingSaucerEntity::new,
                                    MobCategory.MISC)
                            .sized(5.0F, 1.0F)
                            .build(new ResourceLocation("totm", "flying_saucer").toString())
            );

    public static final RegistryObject<EntityType<GrayAlienEntity>> GRAY_ALIEN =
            ENTITIES.register("gray_alien",
                    () -> EntityType.Builder.<GrayAlienEntity>of(GrayAlienEntity::new,
                                    MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .build(new ResourceLocation("totm", "gray_alien").toString())
            );

    public static final RegistryObject<EntityType<CrashedSaucerEntity>> CRASHED_SAUCER =
            ENTITIES.register("crashed_saucer",
                    () -> EntityType.Builder.<CrashedSaucerEntity>of(CrashedSaucerEntity::new,
                                    MobCategory.MISC)
                            .sized(5.0F, 1.0F)
                            .build(new ResourceLocation("totm", "crashed_saucer").toString())
            );

}
