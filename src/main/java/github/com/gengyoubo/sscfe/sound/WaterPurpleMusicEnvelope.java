package github.com.gengyoubo.sscfe.sound;

/** Music gain follows client ticks; release preserves the current gain before fading out. */
public final class WaterPurpleMusicEnvelope {
    private final long startedAt;
    private final int fadeInTicks, releaseHoldTicks, fadeOutTicks;
    private long releasedAt = -1L;
    private float releaseGain;

    public WaterPurpleMusicEnvelope(long startedAt, int fadeInTicks, int releaseHoldTicks, int fadeOutTicks) {
        this.startedAt = startedAt;
        this.fadeInTicks = Math.max(0, fadeInTicks);
        this.releaseHoldTicks = Math.max(0, releaseHoldTicks);
        this.fadeOutTicks = Math.max(0, fadeOutTicks);
    }

    public float gain(long now) {
        if (releasedAt < 0L) return fadeInTicks == 0 ? 1F : clamp((now - startedAt) / (float) fadeInTicks);
        long elapsed = Math.max(0L, now - releasedAt);
        if (elapsed < releaseHoldTicks) return releaseGain;
        if (fadeOutTicks == 0) return 0F;
        return releaseGain * (1F - clamp((elapsed - releaseHoldTicks) / (float) fadeOutTicks));
    }

    public void release(long now) {
        if (releasedAt >= 0L) return;
        releaseGain = gain(now);
        releasedAt = now;
    }

    public boolean finished(long now) {
        return releasedAt >= 0L && now - releasedAt >= (long) releaseHoldTicks + fadeOutTicks;
    }

    private static float clamp(float value) { return Math.max(0F, Math.min(1F, value)); }
}
