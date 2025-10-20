package com.exoticmatter.totm.registry;

import com.exoticmatter.totm.TotmMod;
import com.exoticmatter.totm.world.item.SaucerDebugWandItem;
import net.minecraft.core.Direction;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> REGISTER =
            DeferredRegister.create(ForgeRegistries.ITEMS, TotmMod.MODID);

    public static final RegistryObject<Item> SAUCER_WAND = REGISTER.register(
            "saucer_wand",
            () -> new SaucerDebugWandItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> ALIEN_HEAD = REGISTER.register(
            "alien_head",
            () -> new com.exoticmatter.totm.world.item.AlienHeadItem(ModBlocks.ALIEN_HEAD.get(), ModBlocks.ALIEN_WALL_HEAD.get(), new Item.Properties().rarity(Rarity.UNCOMMON), Direction.UP)
    );

    public static final RegistryObject<Item> PROBE = REGISTER.register(
            "probe",
            () -> new com.exoticmatter.totm.world.item.ProbeItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> GRAY_ALIEN_SPAWN_EGG = REGISTER.register(
            "gray_alien_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.GRAY_ALIEN, 0x000000, 0x39FF14, new Item.Properties())
    );

    public static final RegistryObject<Item> ALIEN_TECH = REGISTER.register(
            "alien_tech",
            () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC))
    );

    public static final RegistryObject<Item> ALIEN_SHERD = REGISTER.register(
            "alien_pottery_sherd",
            () -> new Item(new Item.Properties())
    );
}
