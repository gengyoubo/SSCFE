package github.com.gengyoubo.sscfe.water;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WaterCurseConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue MIN_CAST_TICKS = BUILDER
            .comment("Minimum player-selected casting duration. 20 ticks = 1 second.")
            .defineInRange("water_purple.min_cast_ticks", 60, 20, 12000);
    public static final ForgeConfigSpec.IntValue MAX_CAST_TICKS = BUILDER
            .comment("Maximum duration; longer than 600 ticks adds presentation time, not power.")
            .defineInRange("water_purple.max_cast_ticks", 12000, 20, 12000);
    public static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS = BUILDER
            .define("water_purple.break_blocks", true);
    public static final ForgeConfigSpec.IntValue BLOCK_BUDGET = BUILDER
            .comment("Global candidate block checks per tick; no chunks are force-loaded.")
            .defineInRange("water_purple.block_checks_per_tick", 8192, 256, 32768);
    public static final ForgeConfigSpec SPEC = BUILDER.build();
    private WaterCurseConfig() {}
}
