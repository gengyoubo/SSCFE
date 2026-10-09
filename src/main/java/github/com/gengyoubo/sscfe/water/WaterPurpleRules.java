package github.com.gengyoubo.sscfe.water;

import net.minecraft.world.phys.Vec3;

/** Shared numerical rules; presentation timing never grants extra power above 30 seconds. */
public final class WaterPurpleRules {
    public static final int FULL_POWER_TICKS = 600;
    public static final double SPEED_BLOCKS_PER_SECOND = 100D;
    public static final double SPEED_BLOCKS_PER_TICK = SPEED_BLOCKS_PER_SECOND / 20D;
    public static Vec3 chargeOrigin(Vec3 eyes, Vec3 direction, double distance) {
        return eyes.add(direction.scale(distance));
    }
    public static double power(int duration) { return Math.min(1D, Math.max(0D, duration / 600D)); }
    private static int powerTicks(int duration) { return Math.min(FULL_POWER_TICKS, Math.max(0, duration)); }
    public static int waterCost(int duration) { return (32_000 * powerTicks(duration) + 599) / 600; }
    public static int moistureCost(int duration, int maximum) { return (int) (((long) maximum * powerTicks(duration) + 1199) / 1200); }
    public static double range(int duration) { return 3000D * power(duration); }
    public static double travelDistance(int duration, double elapsedTicks) {
        return Math.min(range(duration), Math.max(0D, elapsedTicks) * SPEED_BLOCKS_PER_TICK);
    }
    public static int flightTicks(int duration) { return (int) Math.ceil(range(duration) / SPEED_BLOCKS_PER_TICK); }
    public static float damagePerTick(int duration) { return (float) (25D * power(duration)); }
    public static double damageRadius(int duration) { return 25D * power(duration); }
    public static int breakRadius(int duration) { return Math.max(1, (int) Math.ceil(8D * power(duration))); }
    public static float shieldDamage(float amount) { return Math.max(0F, amount - 5F) * 0.9F; }

    public static double distanceToRaySquared(Vec3 point, Vec3 origin, Vec3 direction, double range) {
        double along = Math.max(0D, Math.min(range, point.subtract(origin).dot(direction)));
        return point.distanceToSqr(origin.add(direction.scale(along)));
    }
    private WaterPurpleRules() {}
}
