package com.exoticmatter.totm.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "totm")
public class AbductionDropsHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        var victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel sl)) return;
        var tag = victim.getPersistentData();
        if (!tag.getBoolean("totm_abduct")) return;

        UUID pid = null;
        try { pid = tag.getUUID("totm_abduct_player"); } catch (Exception ignored) {}
        if (pid == null) return;

        ServerPlayer player = sl.getServer().getPlayerList().getPlayer(pid);
        if (player == null) return;

        // Route drops into player's inventory where possible
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity ie = it.next();
            ItemStack stack = ie.getItem();
            if (stack.isEmpty()) continue;
            // Try to insert fully; Inventory#add returns true if fully inserted
            boolean added = player.getInventory().add(stack);
            if (added || stack.isEmpty()) {
                // Consumed entirely; prevent world spawn
                it.remove();
            } else {
                // Partial or none; update the entity's stack to remainder
                ie.setItem(stack);
            }
        }

        // Clean up tag marker
        tag.remove("totm_abduct");
        tag.remove("totm_abduct_player");
    }
}

