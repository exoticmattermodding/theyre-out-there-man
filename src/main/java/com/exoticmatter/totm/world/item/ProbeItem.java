package com.exoticmatter.totm.world.item;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class ProbeItem extends Item {
    private static final String TAG_BOUND = "totm_bound";
    private static final String TAG_BOUND_UUID = "totm_bound_uuid";
    private static final String TAG_BOUND_TYPE = "totm_bound_type";

    public ProbeItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // Run logic on server; client returns success for smooth hand animation
        if (player.level().isClientSide) return InteractionResult.SUCCESS;

        // If already bound and clicking the same entity, show info instead of rebinding
        if (isBound(stack)) {
            var tag = stack.getTag();
            if (tag != null && tag.hasUUID(TAG_BOUND_UUID) && tag.getUUID(TAG_BOUND_UUID).equals(target.getUUID())) {
                // Feedback with friendly name
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.totm.probe.bound",
                                net.minecraft.network.chat.Component.translatable(target.getType().getDescriptionId())),
                        true);
                return InteractionResult.CONSUME;
            }
        }

        // If probing a cow, mark it as an Alien Cow (skin override) and bind
        if (target instanceof Cow cow) {
            cow.addTag("totm_alien_cow");
            cow.getPersistentData().putBoolean("totm_alien_cow", true);
            if (player.level() instanceof net.minecraft.server.level.ServerLevel) {
                com.exoticmatter.totm.network.ModNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> cow),
                        new com.exoticmatter.totm.network.packet.AlienCowSkinS2CPacket(cow.getId(), true)
                );
            }
            bindTo(stack, cow);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.totm.probe.bound",
                            net.minecraft.network.chat.Component.translatable(cow.getType().getDescriptionId())),
                    true);
            return InteractionResult.CONSUME;
        }

        // Bind to the target entity
        bindTo(stack, target);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.totm.probe.bound",
                        net.minecraft.network.chat.Component.translatable(target.getType().getDescriptionId())),
                true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isBound(stack)) {
            if (level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.totm.probe.unbound"), true);
            }
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            Entity e = findBoundEntity((ServerLevel)player.level(), stack);
            if (e == null) {
                // Show friendly type name if we can resolve it from stored type
                var tag = stack.getTag();
                net.minecraft.network.chat.Component typeName = null;
                if (tag != null && tag.contains(TAG_BOUND_TYPE)) {
                    try {
                        var rl = new net.minecraft.resources.ResourceLocation(tag.getString(TAG_BOUND_TYPE));
                        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(rl);
                        if (type != null) typeName = net.minecraft.network.chat.Component.translatable(type.getDescriptionId());
                    } catch (Throwable ignored) {}
                }
                if (typeName == null) typeName = net.minecraft.network.chat.Component.literal("unknown");
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.totm.probe.target_missing", typeName),
                        true);
            } else {
                double dx = e.getX() - player.getX();
                double dy = e.getY() - player.getY();
                double dz = e.getZ() - player.getZ();
                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.totm.probe.target_info",
                                net.minecraft.network.chat.Component.translatable(((LivingEntity)e).getType().getDescriptionId()),
                                String.format("%.1f", dist)
                        ),
                        true);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void bindTo(ItemStack stack, Entity entity) {
        var tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_BOUND, true);
        tag.putUUID(TAG_BOUND_UUID, entity.getUUID());
        tag.putString(TAG_BOUND_TYPE, net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    public static boolean isBound(ItemStack stack) {
        var tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_BOUND) && tag.hasUUID(TAG_BOUND_UUID);
    }

    public static Entity findBoundEntity(ServerLevel atLevel, ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.hasUUID(TAG_BOUND_UUID)) return null;
        UUID id = tag.getUUID(TAG_BOUND_UUID);

        // Try current level first
        Entity e = atLevel.getEntity(id);
        if (e != null) return e;

        // Search other levels on the server
        var server = atLevel.getServer();
        for (ServerLevel lvl : server.getAllLevels()) {
            e = lvl.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }
}
