package com.exoticmatter.totm.registry;

import com.exoticmatter.totm.TotmMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TotmMod.MODID);

    public static final RegistryObject<CreativeModeTab> TOTM_TAB = REGISTER.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.totm"))
                    .icon(() -> {
                        ItemStack stack = new ItemStack(Items.PAPER);
                        stack.getOrCreateTag().putInt("CustomModelData", 1001);
                        return stack;
                    })
                    .displayItems((params, out) -> {
                        out.accept(ModItems.ALIEN_HEAD.get());
                        out.accept(ModItems.SAUCER_WAND.get());
                        out.accept(ModItems.PROBE.get());
                        out.accept(ModItems.GRAY_ALIEN_SPAWN_EGG.get());
                        out.accept(ModItems.ALIEN_TECH.get());
                        out.accept(ModItems.ALIEN_SHERD.get());

                    })
                    .build());
}
