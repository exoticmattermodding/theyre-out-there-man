package com.exoticmatter.totm.event;

import com.exoticmatter.totm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm")
public class GrayAlienSleepSpawnHandler {

    @SubscribeEvent
    public static void onPlayerSleep(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event) {
        var player = event.getEntity();
        Level level = player.level();
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return;

        // Night only
        if (sl.isDay()) return;

        // 50% chance arm abduction; do not spawn yet. Wait until the player actually sleeps.
        if (sl.getRandom().nextFloat() > 0.5f) return;

        BlockPos bedPos = event.getPos() != null ? event.getPos() : player.blockPosition();
        var state = sl.getBlockState(bedPos);
        // Resolve bed foot position
        BlockPos footPos = bedPos;
        if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock bed) {
            var part = state.getValue(net.minecraft.world.level.block.BedBlock.PART);
            var facing = state.getValue(net.minecraft.world.level.block.BedBlock.FACING);
            if (part == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                footPos = bedPos.relative(facing.getOpposite());
            } else {
                footPos = bedPos;
            }
        }

        var ptag = player.getPersistentData();
        ptag.putBoolean("totm_abduction_armed", true);
        ptag.putInt("totm_abd_foot_x", footPos.getX());
        ptag.putInt("totm_abd_foot_y", footPos.getY());
        ptag.putInt("totm_abd_foot_z", footPos.getZ());
    }
}
