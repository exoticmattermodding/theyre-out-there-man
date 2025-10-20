package com.exoticmatter.totm.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class TotmConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue ENCOUNTER_MIN_VIEW_SECONDS;
    public static final ForgeConfigSpec.IntValue DEPLOY_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue RETURN_WAIT_SECONDS;

    public static final ForgeConfigSpec.DoubleValue ENCOUNTER_BASE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue ENCOUNTER_THUNDER_CHANCE;
    public static final ForgeConfigSpec.DoubleValue ENCOUNTER_DUSK_CHANCE;

    public static final ForgeConfigSpec.IntValue DM_MAX;
    public static final ForgeConfigSpec.IntValue DM_REGEN_PER_TICK;
    public static final ForgeConfigSpec.IntValue BEAM_DRAIN_PER_TICK;
    public static final ForgeConfigSpec.IntValue DASH_COST_BASE;
    public static final ForgeConfigSpec.IntValue DASH_COST_PER_POWER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("encounters");
        ENCOUNTER_MIN_VIEW_SECONDS = b.comment("Minimum stage 0 view time (seconds)")
                .defineInRange("minViewSeconds", 15, 0, 600);
        DEPLOY_DURATION_SECONDS = b.comment("Deploy stage length (seconds)")
                .defineInRange("deployDurationSeconds", 15, 1, 600);
        RETURN_WAIT_SECONDS = b.comment("Return wait stage length (seconds)")
                .defineInRange("returnWaitSeconds", 20, 1, 600);
        ENCOUNTER_BASE_CHANCE = b.comment("Base daily encounter chance (0..1)")
                .defineInRange("baseChance", 0.5, 0.0, 1.0);
        ENCOUNTER_THUNDER_CHANCE = b.comment("Thunder daily encounter chance (0..1)")
                .defineInRange("thunderChance", 0.9, 0.0, 1.0);
        ENCOUNTER_DUSK_CHANCE = b.comment("Opportunistic dusk encounter chance per tick (0..1)")
                .defineInRange("duskChance", 0.02, 0.0, 1.0);
        b.pop();

        b.push("darkMatter");
        DM_MAX = b.defineInRange("max", 1000, 1, 100000);
        DM_REGEN_PER_TICK = b.defineInRange("regenPerTick", 4, 0, 1000);
        BEAM_DRAIN_PER_TICK = b.defineInRange("beamDrainPerTick", 6, 0, 1000);
        DASH_COST_BASE = b.defineInRange("dashCostBase", 100, 0, 100000);
        DASH_COST_PER_POWER = b.defineInRange("dashCostPerPower", 4, 0, 100000);
        b.pop();

        SPEC = b.build();
    }
}

