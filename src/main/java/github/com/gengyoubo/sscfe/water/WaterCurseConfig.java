package github.com.gengyoubo.sscfe.water;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WaterCurseConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.DoubleValue CHARGE_DISTANCE = BUILDER
            .comment("Distance from the caster's eyes to the charge core and projectile origin, in blocks.",
                    "Captured when casting starts and sent to every client. Small values can overlap the caster.")
            .defineInRange("water_purple.charge_distance", 12D, 1D, 64D);
    public static final ForgeConfigSpec.IntValue MIN_CAST_TICKS = BUILDER
            .comment("Minimum player-selected casting duration. 20 ticks = 1 second.")
            .defineInRange("water_purple.min_cast_ticks", 60, 20, 12000);
    public static final ForgeConfigSpec.IntValue MAX_CAST_TICKS = BUILDER
            .comment("Maximum duration; longer than 600 ticks adds presentation time, not power.")
            .defineInRange("water_purple.max_cast_ticks", 12000, 20, 12000);
    public static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS = BUILDER
            .define("water_purple.break_blocks", true);
    public static final ForgeConfigSpec.IntValue BLOCK_BUDGET = BUILDER
            .comment("Global candidate block checks per tick; chunk loading uses a separate bounded window.")
            .defineInRange("water_purple.block_checks_per_tick", 8192, 256, 32768);
    public static final ForgeConfigSpec.BooleanValue CHUNK_LOADING = BUILDER.define("water_purple.chunk_loading.enabled", true);
    public static final ForgeConfigSpec.IntValue PRELOAD_CHUNKS = BUILDER.defineInRange("water_purple.chunk_loading.preload_chunks", 3, 0, 8);
    public static final ForgeConfigSpec.IntValue REAR_CHUNKS = BUILDER.defineInRange("water_purple.chunk_loading.rear_chunks", 2, 0, 8);
    public static final ForgeConfigSpec.IntValue SIDE_MARGIN_CHUNKS = BUILDER.defineInRange("water_purple.chunk_loading.side_margin_chunks", 1, 0, 4);
    public static final ForgeConfigSpec.IntValue MAX_CHUNKS_PER_BEAM = BUILDER.defineInRange("water_purple.chunk_loading.max_forced_chunks_per_beam", 64, 4, 256);
    public static final ForgeConfigSpec.IntValue MAX_TOTAL_CHUNKS = BUILDER.defineInRange("water_purple.chunk_loading.max_total_forced_chunks", 128, 4, 1024);
    public static final ForgeConfigSpec.BooleanValue GENERATE_CHUNKS = BUILDER
            .comment("Allow asynchronous generation of ungenerated chunks within the bounded loading window.",
                    "Enabled by default; when disabled, flight stops at ungenerated terrain.")
            .define("water_purple.chunk_loading.generate_new_chunks", true);
    public static final ForgeConfigSpec.IntValue CHUNK_REQUEST_BUDGET = BUILDER
            .comment("Global async disk probes and new ticket requests per server tick. No synchronous generation waits.")
            .defineInRange("water_purple.chunk_loading.max_chunk_requests_per_tick", 8, 1, 64);
    public static final ForgeConfigSpec.IntValue CHUNK_WAIT_TICKS = BUILDER
            .comment("Cancel a projectile after this many consecutive waiting ticks, releasing all its tickets.")
            .defineInRange("water_purple.chunk_loading.max_wait_ticks", 1200, 100, 12000);
    public static final ForgeConfigSpec SPEC = BUILDER.build();
    private WaterCurseConfig() {}
}
