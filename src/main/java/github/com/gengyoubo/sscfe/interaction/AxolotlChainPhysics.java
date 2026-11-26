package github.com.gengyoubo.sscfe.interaction;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseForge.form.FormManager;

/**
 * Follow-the-leader movement of an axolotl chain. Runs from {@code positionRider} on the server
 * and on every client: the server never sends passenger positions, so each side simulates the
 * same rule from the front player's position.
 *
 * <p>The follower is pulled toward a rest distance behind the front player by a damped spring
 * along the line between them. While the front moves the follower lags behind, which is what
 * stretches the front player's tail; when it stops the follower slides back to rest. Because the
 * pull is radial, followers trail along the leader's path instead of copying its heading.</p>
 *
 * <p>Distances are blocks at form scale 1 and were measured from the axolotl_3 crawl pose:
 * hip 0.81 behind the feet, straightened tail 1.42 long, hands reaching 0.11 ahead.</p>
 */
public final class AxolotlChainPhysics {
    /** Front feet to follower feet when the tail is relaxed and lies in the follower's hands. */
    public static final double REST_DISTANCE = 2.25D;
    /** The tail compresses to at most this distance when the leader backs up. */
    public static final double MIN_DISTANCE = 1.6D;
    /** The tail stretches to at most this distance; the spring cannot lag further. */
    public static final double MAX_DISTANCE = 3.2D;
    /** Beyond this (stuck behind a wall) the follower is moved without collision. */
    public static final double SNAP_DISTANCE = 4.5D;
    /** Share of the distance error corrected per tick; lower means a stretchier tail. */
    public static final double STIFFNESS = 0.3D;
    /** How far a follower may sit off straight behind the front player's body, either side. */
    public static final double MAX_ANGLE_DEGREES = 5.0D;
    /** How fast (per tick) a follower outside that cone is swung back into it. */
    public static final double MAX_SWING_DEGREES = 15.0D;
    /** Follower feet to the point on the ground where its hands hold the tail. */
    public static final double GRAB_FORWARD = 0.11D;
    /** Height of the held tail above the ground (hand and tail thickness). */
    public static final double GRAB_LIFT = 0.03D;

    private static final float RAD_TO_DEG = 180.0F / (float) Math.PI;
    private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;

    public static double scale(Player player) {
        float scale = FormManager.current(player).widthScale();
        return scale > 0.0F ? scale : 1.0D;
    }

    public static void follow(Player front, Player follower, Entity.MoveFunction move) {
        front.setPose(Pose.SWIMMING);
        follower.setPose(Pose.SWIMMING);
        double scale = scale(front);
        Vec3 frontPos = front.position();
        Vec3 pos = follower.position();

        // Straight back from the front player's body; its tail leaves the hip in this direction.
        float frontYaw = front.yBodyRot * DEG_TO_RAD;
        double backX = Mth.sin(frontYaw), backZ = -Mth.cos(frontYaw);
        double dx = pos.x - frontPos.x, dz = pos.z - frontPos.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double angle = 0.0D;
        if (length < 1.0E-4D) {
            // Directly on top of each other: start behind the front player.
            length = 1.0E-4D;
        } else {
            // Signed angle from straight back to the follower.
            angle = Math.atan2(backX * dz - backZ * dx, backX * dx + backZ * dz);
        }
        // The tail only bends so far: swing a follower outside the cone back toward the
        // centre, a little per tick so a quick turn drags it round in an arc instead of a jump.
        double limit = Math.toRadians(MAX_ANGLE_DEGREES), swing = Math.toRadians(MAX_SWING_DEGREES);
        angle += Mth.clamp(Mth.clamp(angle, -limit, limit) - angle, -swing, swing);
        dx = backX * Math.cos(angle) - backZ * Math.sin(angle);
        dz = backX * Math.sin(angle) + backZ * Math.cos(angle);
        double distance = length + (REST_DISTANCE * scale - length) * STIFFNESS;
        distance = Mth.clamp(distance, MIN_DISTANCE * scale, MAX_DISTANCE * scale);
        double targetX = frontPos.x + dx * distance, targetZ = frontPos.z + dz * distance;

        double top = Math.max(frontPos.y, pos.y) + 1.0D, bottom = Math.min(frontPos.y, pos.y) - 2.0D;
        double ground = groundY(follower, targetX, targetZ, top, bottom, Double.NaN);
        double dy = Double.isNaN(ground) ? -0.5D : ground - pos.y;
        follower.move(MoverType.SELF, new Vec3(targetX - pos.x, dy, targetZ - pos.z));

        double gapX = follower.getX() - frontPos.x, gapZ = follower.getZ() - frontPos.z;
        if (gapX * gapX + gapZ * gapZ > SNAP_DISTANCE * SNAP_DISTANCE * scale * scale) {
            follower.setPos(targetX, Double.isNaN(ground) ? frontPos.y : ground, targetZ);
        }
        move.accept(follower, follower.getX(), follower.getY(), follower.getZ());

        // Face the front player; the head stays free so the follower can still look around.
        follower.yBodyRot = (float) Mth.atan2(frontPos.z - follower.getZ(), frontPos.x - follower.getX())
                * RAD_TO_DEG - 90.0F;
        follower.resetFallDistance();
    }

    /** World point on the ground in front of {@code follower} where its hands hold the tail. */
    public static Vec3 grabPoint(Player follower, float partialTick) {
        double scale = scale(follower);
        Vec3 pos = follower.getPosition(partialTick);
        float yaw = Mth.rotLerp(partialTick, follower.yBodyRotO, follower.yBodyRot) * DEG_TO_RAD;
        double x = pos.x - Mth.sin(yaw) * GRAB_FORWARD * scale;
        double z = pos.z + Mth.cos(yaw) * GRAB_FORWARD * scale;
        double y = groundY(follower, x, z, pos.y + 0.6D, pos.y - 1.0D, pos.y);
        return new Vec3(x, y + GRAB_LIFT * scale, z);
    }

    /** Height of the first collision surface below {@code top} at (x, z), or {@code fallback}. */
    public static double groundY(Entity entity, double x, double z, double top, double bottom, double fallback) {
        BlockHitResult hit = entity.level().clip(new ClipContext(new Vec3(x, top, z), new Vec3(x, bottom, z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return hit.getType() == HitResult.Type.MISS ? fallback : hit.getLocation().y;
    }

    private AxolotlChainPhysics() {}
}
