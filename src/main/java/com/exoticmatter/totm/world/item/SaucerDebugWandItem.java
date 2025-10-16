package com.exoticmatter.totm.world.item;

import com.exoticmatter.totm.registry.ModEntities;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SaucerDebugWandItem extends Item {
    public SaucerDebugWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            var saucer = ModEntities.FLYING_SAUCER.get().create(level);
            if (saucer != null) {
                var look = player.getLookAngle();
                double dist = 2.5D;
                saucer.moveTo(
                        player.getX() + look.x * dist,
                        player.getY() + 1.0 + look.y * dist,
                        player.getZ() + look.z * dist,
                        player.getYRot(), 0.0F
                );
                level.addFreshEntity(saucer);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
