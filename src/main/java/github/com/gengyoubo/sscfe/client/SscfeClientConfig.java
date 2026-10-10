package github.com.gengyoubo.sscfe.client;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SscfeClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue PREFER_NEW_ANIMATIONS = BUILDER
            .comment("Use SSCFE's player_animation/new resources when present.",
                    "When disabled, Shape Shifter Curse uses its bundled legacy animations.")
            .define("animations.prefer_new_animations", true);

    public static final ForgeConfigSpec.IntValue WATER_PURPLE_CAST_TICKS = BUILDER
            .comment("Click the water skill key once to cast. Server limits apply. Full power at 600 ticks.")
            .defineInRange("water_purple.cast_ticks", 600, 20, 12000);
    public static final ForgeConfigSpec.IntValue SPECIAL_CHARGE_TICKS = BUILDER
            .comment("Player-selected special presentation duration. 1140 ticks = 57 seconds is only an example; server limits apply.")
            .defineInRange("water_purple_special.special_charge_ticks", 600, 20, 12000);
    public static final ForgeConfigSpec.BooleanValue SPECIAL_MUSIC_ENABLED = BUILDER
            .define("water_purple_special.enabled", true);
    public static final ForgeConfigSpec.ConfigValue<String> SPECIAL_SOUND_ID = BUILDER
            .comment("Sound event from an installed resource pack; audio is never downloaded or sent by the server.")
            .define("water_purple_special.sound_id", "sscfe:music.water_purple_special",
                    value -> value instanceof String id && net.minecraft.resources.ResourceLocation.tryParse(id) != null);
    public static final ForgeConfigSpec.DoubleValue SPECIAL_MUSIC_VOLUME = BUILDER
            .defineInRange("water_purple_special.volume", 1.0D, 0.0D, 1.0D);
    public static final ForgeConfigSpec.IntValue MUSIC_START_DELAY_TICKS = BUILDER
            .comment("Delay music after cast begins. This delays playback; it does not seek inside an OGG stream.",
                    "Trim the OGG externally to start at a particular part of the song.")
            .defineInRange("water_purple_special.music_start_delay_ticks", 0, 0, 12000);
    public static final ForgeConfigSpec.IntValue MUSIC_FADE_IN_TICKS = BUILDER
            .comment("Fade music in when chanting starts. 40 ticks = 2 seconds; 0 disables fade-in.")
            .defineInRange("water_purple_special.music_fade_in_ticks", 40, 0, 1200);
    public static final ForgeConfigSpec.IntValue MUSIC_RELEASE_HOLD_TICKS = BUILDER
            .comment("Continue the same music after firing, before starting fade-out. 40 ticks = 2 seconds.")
            .defineInRange("water_purple_special.music_release_hold_ticks", 40, 0, 1200);
    public static final ForgeConfigSpec.IntValue MUSIC_FADE_OUT_TICKS = BUILDER
            .comment("Fade music out after the release hold. 60 ticks = 3 seconds; interruptions still stop immediately.")
            .defineInRange("water_purple_special.music_fade_out_ticks", 60, 0, 1200);
    public static final ForgeConfigSpec.IntValue EFFECT_PARTICLES = BUILDER
            .comment("Particle budget per visible cast per tick. Blue cores and water vortices use built-in particles.")
            .defineInRange("water_purple.particles_per_tick", 64, 8, 192);
    public static final ForgeConfigSpec.IntValue CHARGE_AURA_HEIGHT = BUILDER
            .comment("Maximum height of the irregular charging aura in blocks. Aura particles share the existing particle budget.")
            .defineInRange("water_purple.charge_aura_height", 32, 20, 40);
    public static final ForgeConfigSpec.IntValue EFFECT_RENDER_DISTANCE = BUILDER
            .comment("Maximum core geometry distance in blocks. The game's view distance and projection still apply.",
                    "Distance culling only skips drawing; the projectile keeps its flight state.")
            .defineInRange("water_purple.effect_render_distance", 1024, 64, 4096);
    public static final ForgeConfigSpec.IntValue PARTICLE_RENDER_DISTANCE = BUILDER
            .comment("Maximum particle spawn distance in blocks. Geometry has a separate distance limit.")
            .defineInRange("water_purple.particle_render_distance", 512, 32, 1024);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private SscfeClientConfig() {
    }
}
