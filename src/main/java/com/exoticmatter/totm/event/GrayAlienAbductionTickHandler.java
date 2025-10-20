package com.exoticmatter.totm.event;

import com.exoticmatter.totm.registry.ModEntities;
import com.exoticmatter.totm.world.entity.GrayAlienEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm")
public class GrayAlienAbductionTickHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var player = event.player;
        if (!(player.level() instanceof ServerLevel sl)) return;
        var tag = player.getPersistentData();


        if (!tag.getBoolean("totm_abduction_armed")) return;
        if (!player.isSleeping()) return; // wait until actually sleeping
        if (sl.isDay()) { // abort at dawn
            tag.remove("totm_abduction_armed");
            return;
        }

        int fx = tag.getInt("totm_abd_foot_x");
        int fy = tag.getInt("totm_abd_foot_y");
        int fz = tag.getInt("totm_abd_foot_z");
        BlockPos foot = new BlockPos(fx, fy, fz);

        // Determine spawn: find an indoor spot near foot; fallback to left side
        var bedState = sl.getBlockState(foot);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        if (bedState.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
            facing = bedState.getValue(net.minecraft.world.level.block.BedBlock.FACING);
        }
        BlockPos spawnPos = findIndoorSpawnNear(sl, foot, facing);
        if (spawnPos == null) {
            net.minecraft.core.Direction left = facing.getCounterClockWise();
            spawnPos = foot.relative(left, 2);
        }
        double sx = spawnPos.getX() + 0.5;
        double sz = spawnPos.getZ() + 0.5;
        double sy = spawnPos.getY() + 0.1;

        var type = ModEntities.GRAY_ALIEN.get();
        var alien = type.create(sl);
        if (alien == null) { tag.remove("totm_abduction_armed"); return; }

        // Equip probe
        var probe = com.exoticmatter.totm.registry.ModItems.PROBE.get();
        if (probe != null) {
            alien.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(probe));
        }

        // Move to spawn; face the player
        double dx = player.getX() - sx;
        double dz = player.getZ() - sz;
        float faceYaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        alien.moveTo(sx, sy, sz, faceYaw, 0.0f);

        // Mark mission details
        var atag = alien.getPersistentData();
        atag.putBoolean("totm_abduction", true);
        atag.putInt("totm_bed_x", foot.getX());
        atag.putInt("totm_bed_y", foot.getY());
        atag.putInt("totm_bed_z", foot.getZ());
        atag.putUUID("totm_target", player.getUUID());
        atag.putInt("totm_abd_stage", 0); // 0: going to foot, 1: probing, 2: done
        atag.putInt("totm_abd_timer", 0);

        sl.addFreshEntity(alien);

        // Choose a stand position at the end of the bed (not on top)
        BlockPos stand = foot.relative(facing.getOpposite());
        alien.getNavigation().moveTo(stand.getX() + 0.5, sy, stand.getZ() + 0.5, 1.25);

        // Consume the arming tag so this runs once
        tag.remove("totm_abduction_armed");
    }

    private static BlockPos findIndoorSpawnNear(ServerLevel sl, BlockPos foot, net.minecraft.core.Direction facing) {
        // Search a small neighborhood for a spot that is indoors (no sky), with headroom
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // ring only
                    BlockPos pos = foot.offset(dx, 0, dz);
                    // Ground Y from heightmap is unreliable inside structures; use existing Y and adjust
                    BlockPos below = pos.below();
                    if (!sl.getBlockState(below).isAir() && sl.getBlockState(pos).isAir() && sl.getBlockState(pos.above()).isAir()) {
                        // Prefer indoors: not sky visible
                        if (!sl.canSeeSky(pos)) {
                            return pos;
                        }
                    }
                }
            }
        }
        // Fallback: try directly beside the bed
        BlockPos side = foot.relative(facing.getCounterClockWise());
        if (sl.getBlockState(side).isAir() && sl.getBlockState(side.above()).isAir()) return side;
        return null;
    }
}
