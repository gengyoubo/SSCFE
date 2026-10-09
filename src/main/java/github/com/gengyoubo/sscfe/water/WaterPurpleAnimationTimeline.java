package github.com.gengyoubo.sscfe.water;

/** Clip 2 finishes at the firing instant: a 30-second cast starts it at second 28. */
public final class WaterPurpleAnimationTimeline {
    public record Sample(String clip, float seconds) {}
    public static Sample sample(double elapsedTicks, int castTicks, boolean released) {
        if (released) return elapsedTicks <= 4D ? new Sample("mizu_mulasaki2", 2F) : null;
        int finalPhaseTicks = Math.min(40, Math.max(1, castTicks));
        int finalPhaseStart = castTicks - finalPhaseTicks;
        if (elapsedTicks >= finalPhaseStart) {
            // Shorter-than-two-second server configurations compress the clip to finish on time.
            float time = (float) Math.min(2D, Math.max(0D, elapsedTicks - finalPhaseStart) * 2D / finalPhaseTicks);
            return new Sample("mizu_mulasaki2", time);
        }
        return new Sample("mizu_mulasaki1", (float) Math.min(2D, Math.max(0D, elapsedTicks) / 20D));
    }
    private WaterPurpleAnimationTimeline() {}
}
