package com.exoticmatter.totm.command;

import com.exoticmatter.totm.world.entity.GrayAlienEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm")
public class TotmCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("totm")
            .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
            .then(Commands.literal("alien")
                .then(Commands.literal("status")
                    .executes(ctx -> alienStatus(ctx.getSource(), 64))
                    .then(Commands.argument("range", IntegerArgumentType.integer(8, 256))
                        .executes(ctx -> alienStatus(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "range")))))
            )
            .then(Commands.literal("saucer")
                .then(Commands.literal("status")
                    .executes(ctx -> saucerStatus(ctx.getSource(), 96))
                    .then(Commands.argument("range", IntegerArgumentType.integer(16, 256))
                        .executes(ctx -> saucerStatus(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "range")))))
                .then(Commands.literal("beam")
                    .then(Commands.literal("toggle")
                        .executes(ctx -> saucerBeam(ctx.getSource(), 2))))
                .then(Commands.literal("spawn_encounter").executes(ctx -> saucerSpawnEncounter(ctx.getSource())))
                .then(Commands.literal("spawn_crash").executes(ctx -> saucerSpawnCrash(ctx.getSource())))
                .then(Commands.literal("deploy").executes(ctx -> saucerDeployAlien(ctx.getSource())))
            )
            .then(Commands.literal("trigger_encounter").executes(ctx -> saucerTriggerEncounter(ctx.getSource())))
        );
    }

    private static int help(CommandSourceStack src) {
        return 1;
    }

    private static int alienStatus(CommandSourceStack src, int range) {
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return 0;
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        GrayAlienEntity alien = sl.getNearestEntity(GrayAlienEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight().range(range),
                sp, sp.getX(), sp.getY(), sp.getZ(), sp.getBoundingBox().inflate(range));
        if (alien == null) {
            return 1;
        }
        // No in-game chat output
        return 1;
    }

    private static int saucerStatus(CommandSourceStack src, int range) {
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return 0;
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        com.exoticmatter.totm.world.entity.FlyingSaucerEntity saucer = sl.getNearestEntity(
                com.exoticmatter.totm.world.entity.FlyingSaucerEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight().range(range),
                sp, sp.getX(), sp.getY(), sp.getZ(), sp.getBoundingBox().inflate(range));
        if (saucer == null) {
            return 1;
        }
        // No in-game chat output
        return 1;
    }

    private static int saucerBeam(CommandSourceStack src, int mode) {
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return 0;
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        var saucer = sl.getNearestEntity(com.exoticmatter.totm.world.entity.FlyingSaucerEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight().range(128),
                sp, sp.getX(), sp.getY(), sp.getZ(), sp.getBoundingBox().inflate(128));
        if (saucer == null) { return 1; }
        if (mode == 0) saucer.setAbductionActive(false);
        else if (mode == 1) saucer.setAbductionActive(true);
        else saucer.setAbductionActive(!saucer.isAbductionActive());
        // No in-game chat output
        return 1;
    }

    private static int saucerSpawnEncounter(CommandSourceStack src) {
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return 0;
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        double dist = 48.0;
        var dir = sp.getLookAngle();
        double sx = sp.getX() + dir.x * dist;
        double sz = sp.getZ() + dir.z * dist;
        int gy = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, net.minecraft.util.Mth.floor(sx), net.minecraft.util.Mth.floor(sz));
        double sy = Math.max(gy + 12.0, sp.getY() + 6.0);
        var type = com.exoticmatter.totm.registry.ModEntities.FLYING_SAUCER.get();
        var saucer = type.create(sl);
        if (saucer == null) return 0;
        saucer.moveTo(sx, sy, sz, sp.getYRot(), 0.0f);
        var tag = saucer.getPersistentData();
        tag.putBoolean("totm_encounter", true);
        tag.putDouble("totm_enc_anchor_x", sp.getX());
        tag.putDouble("totm_enc_anchor_z", sp.getZ());
        tag.putInt("totm_enc_stage", 0);
        tag.putInt("totm_enc_mode", new java.util.Random().nextInt(3));
        tag.putInt("totm_enc_timer", 20 * 15);
        tag.remove("totm_min_view_set");
        saucer.setGlowEnabled(false);
        saucer.setAbductionActive(false);
        sl.addFreshEntity(saucer);
        return 1;
    }

    private static int saucerSpawnCrash(CommandSourceStack src) {
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return 0;
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        double dist = 32.0;
        var dir = sp.getLookAngle();
        double sx = sp.getX() + dir.x * dist;
        double sz = sp.getZ() + dir.z * dist;
        int gy = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, net.minecraft.util.Mth.floor(sx), net.minecraft.util.Mth.floor(sz));
        double sy = gy + 20.0;
        var type = com.exoticmatter.totm.registry.ModEntities.FLYING_SAUCER.get();
        var saucer = type.create(sl);
        if (saucer == null) return 0;
        saucer.moveTo(sx, sy, sz, sp.getYRot(), 0.0f);
        var tag = saucer.getPersistentData();
        // Treat as an encounter: mark and anchor to the player
        tag.putBoolean("totm_encounter", true);
        tag.putDouble("totm_enc_anchor_x", sp.getX());
        tag.putDouble("totm_enc_anchor_z", sp.getZ());
        // Immediately schedule crash via lightning sequence
        tag.putBoolean("totm_crash_pending", true);
        saucer.setGlowEnabled(false);
        saucer.setAbductionActive(false);
        sl.addFreshEntity(saucer);
        return 1;
    }

    private static int saucerDeployAlien(CommandSourceStack src) {
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return 0;
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        var saucer = sl.getNearestEntity(com.exoticmatter.totm.world.entity.FlyingSaucerEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight().range(128),
                sp, sp.getX(), sp.getY(), sp.getZ(), sp.getBoundingBox().inflate(128));
        if (saucer == null) { return 1; }
        var type = com.exoticmatter.totm.registry.ModEntities.GRAY_ALIEN.get();
        var alien = type.create(sl);
        if (alien == null) return 0;
        double gy = Math.max(sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, saucer.blockPosition().getX(), saucer.blockPosition().getZ()) + 0.2, sl.getMinBuildHeight()+1);
        alien.moveTo(saucer.getX(), gy, saucer.getZ(), saucer.getYRot(), 0.0f);
        var atag = alien.getPersistentData();
        atag.putBoolean("totm_mission_probe_cow", true);
        atag.putBoolean("totm_mission_return", true);
        atag.putInt("totm_return_after", 20 * 6);
        atag.putInt("totm_return_target", saucer.getId());
        var probe = com.exoticmatter.totm.registry.ModItems.PROBE.get();
        if (probe != null) alien.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(probe));
        sl.addFreshEntity(alien);
        return 1;
    }

    private static int saucerTriggerEncounter(CommandSourceStack src) {
        return saucerSpawnEncounter(src);
    }
}
