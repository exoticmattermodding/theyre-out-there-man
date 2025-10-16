package com.exoticmatter.totm.registry;

import com.exoticmatter.totm.TotmMod;
import com.exoticmatter.totm.world.item.SaucerDebugWandItem;
import net.minecraft.world.item.Item;
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
}
