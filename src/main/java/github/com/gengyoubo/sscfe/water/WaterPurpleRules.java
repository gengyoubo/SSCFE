package github.com.gengyoubo.sscfe.water;

import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;

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

    /** First distance where a moving sphere touches the block's full unit cube; infinity means no intersection. */
    static double blockEntryDistance(BlockPos block, Vec3 origin, Vec3 direction, double range, double radius) {
        return boxEntryDistance(new AABB(block), origin, direction, range, radius);
    }

    static double boxEntryDistance(AABB box, Vec3 origin, Vec3 direction, double range, double radius) {
        double[] starts = {origin.x - box.minX, origin.y - box.minY, origin.z - box.minZ};
        double[] sizes = {box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ};
        double[] velocities = {direction.x, direction.y, direction.z};
        double[] cuts = new double[8];
        cuts[0] = 0D; cuts[1] = range;
        int count = 2;
        // Box distance is quadratic between crossings of its six face planes.
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(velocities[axis]) < 1E-12D) continue;
            for (int face = 0; face <= 1; face++) {
                double t = (face * sizes[axis] - starts[axis]) / velocities[axis];
                if (t > 0D && t < range) cuts[count++] = t;
            }
        }
        Arrays.sort(cuts, 0, count);
        for (int interval = 0; interval < count - 1; interval++) {
            double low = cuts[interval], high = cuts[interval + 1];
            double middle = (low + high) * 0.5D;
            double a = 0D, b = 0D, c = -radius * radius;
            for (int axis = 0; axis < 3; axis++) {
                double coordinate = starts[axis] + velocities[axis] * middle;
                if (coordinate >= 0D && coordinate <= sizes[axis]) continue;
                double delta = starts[axis] + velocities[axis] * low - (coordinate < 0D ? 0D : sizes[axis]);
                a += velocities[axis] * velocities[axis];
                b += delta * velocities[axis];
                c += delta * delta;
            }
            if (c <= 1E-9D) return low;
            if (a < 1E-24D) continue;
            double discriminant = b * b - a * c;
            if (discriminant < -1E-9D) continue;
            double root = (-b - Math.sqrt(Math.max(0D, discriminant))) / a;
            if (root >= -1E-9D && root <= high - low + 1E-9D) return Math.min(high, low + Math.max(0D, root));
        }
        return Double.POSITIVE_INFINITY;
    }
    private WaterPurpleRules() {}
}
