package com.exoticmatter.totm.registry;

import com.exoticmatter.totm.TotmMod;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> REGISTER =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TotmMod.MODID);

    public static final RegistryObject<Block> ALIEN_HEAD = REGISTER.register(
            "alien_head",
            () -> new SkullBlock(ModSkullTypes.ALIEN, BlockBehaviour.Properties.copy(Blocks.SKELETON_SKULL).mapColor(MapColor.COLOR_GREEN))
    );

    public static final RegistryObject<Block> ALIEN_WALL_HEAD = REGISTER.register(
            "alien_wall_head",
            () -> new WallSkullBlock(ModSkullTypes.ALIEN, BlockBehaviour.Properties.copy(Blocks.SKELETON_WALL_SKULL).mapColor(MapColor.COLOR_GREEN))
    );
}
