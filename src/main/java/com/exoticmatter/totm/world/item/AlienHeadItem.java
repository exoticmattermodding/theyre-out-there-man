package com.exoticmatter.totm.world.item;

import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.StandingAndWallBlockItem;

public class AlienHeadItem extends StandingAndWallBlockItem {
    public AlienHeadItem(Block floor, Block wall, Item.Properties props, Direction attachment) {
        super(floor, wall, props, attachment);
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext ctx) {
        // Prefer standing placement when clicking the top face
        if (ctx.getClickedFace() == Direction.UP) {
            var state = this.getBlock().getStateForPlacement(ctx);
            if (state != null && state.canSurvive(ctx.getLevel(), ctx.getClickedPos())) {
                return state;
            }
        }
        return super.getPlacementState(ctx);
    }
}

