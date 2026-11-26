package github.com.gengyoubo.sscfe.client;

import github.com.gengyoubo.sscfe.interaction.AxolotlChainPhysics;
import github.com.gengyoubo.sscfe.interaction.AxolotlStackService;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseForge.client.render.BedrockAnimationPlayer;
import net.onixary.shapeShifterCurseForge.client.render.FormGeoAnimatable;
import net.onixary.shapeShifterCurseForge.client.render.FormGeoModel;
import net.onixary.shapeShifterCurseForge.client.render.GeoBonePose;
import net.onixary.shapeShifterCurseForge.form.FormDefinition;
import net.onixary.shapeShifterCurseForge.form.FormManager;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.ArrayList;
import java.util.List;

/**
 * Chain pose on the Geo form model, applied after the regular pose each render.
 *
 * <ul>
 *     <li>A follower reaches both hands down to {@link AxolotlChainPhysics#grabPoint}, a point on
 *     the ground just in front of it.</li>
 *     <li>A player with a follower straightens its tail, aims it at that same point and
 *     stretches it (one continuous scale) so the tip lies in the follower's hands at any distance.</li>
 * </ul>
 * Both sides derive the grab point from the follower's position alone, so the tail and the hands
 * agree even when only one of the two players is drawn (first person, culling).
 *
 * <p>World and Geo space are related by {@link #geoToWorld}, a copy of the transforms
 * {@code FormClientRenderEvents} and {@code GeoObjectRenderer} apply before the root bones.</p>
 */
public final class AxolotlChainPose {
    private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;
    private static final float PLAYER_SCALE = 0.9375F;
    /** Shoulder pivot to the hand along the rest arm (-Y), Geo pixels. */
    private static final float ARM_LENGTH = 8.3F;
    /** At most this far (Geo pixels) is an arm slid out of the shoulder to keep the hand on the ground. */
    private static final float MAX_ARM_SLIDE = 3.0F;
    /** Each hand holds the tail this far from the centre line, blocks at form scale 1. */
    private static final double HAND_SPREAD = 0.07D;
    /** The tail tip passes the hands by this much, blocks at form scale 1. */
    private static final double TIP_OVERHANG = 0.04D;
    /** The last tail cube extends this many times the last segment beyond the last pivot. */
    private static final float TIP_EXTENSION = 1.82F;
    private static final float MIN_STRETCH = 0.6F, MAX_STRETCH = 2.0F;

    public static void apply(FormGeoModel model, FormGeoAnimatable animatable, float partialTick) {
        Player player = animatable.getPlayer();
        // Only the world (third-person) pass: first-person arms and inventory previews keep their pose.
        if (player == null || animatable.isInventoryPreview() || !GeoBonePose.isFramePlayer(player)
                || !AxolotlStackService.isChained(player)) return;
        Matrix4f toWorld = geoToWorld(player, animatable, partialTick);
        Matrix4f toGeo = new Matrix4f(toWorld).invert();

        if (AxolotlStackService.isChainLink(player.getVehicle(), player)) {
            reachForGround(model, player, toGeo, partialTick);
        }
        Player follower = AxolotlStackService.chainFollower(player);
        if (follower != null) {
            Vec3 grab = AxolotlChainPhysics.grabPoint(follower, partialTick);
            Vec3 toFollower = follower.getPosition(partialTick).subtract(grab).multiply(1.0D, 0.0D, 1.0D);
            if (toFollower.lengthSqr() > 1.0E-6D) {
                grab = grab.add(toFollower.normalize().scale(TIP_OVERHANG * AxolotlChainPhysics.scale(follower)));
            }
            aimTail(model, toGeo.transformPosition(new Vector3f((float) grab.x, (float) grab.y, (float) grab.z)));
        }
    }

    // ---- follower: hands on the ground ------------------------------------------------------

    private static void reachForGround(FormGeoModel model, Player player, Matrix4f toGeo, float partialTick) {
        Vec3 grab = AxolotlChainPhysics.grabPoint(player, partialTick);
        float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * DEG_TO_RAD;
        // Player's right in world space for body yaw: (-cos, 0, -sin).
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0D, -Mth.sin(yaw))
                .scale(HAND_SPREAD * AxolotlChainPhysics.scale(player));
        reachArm(model, "bipedRightArm", toGeo, grab.add(right));
        reachArm(model, "bipedLeftArm", toGeo, grab.subtract(right));
    }

    /** Points an arm (a root bone hanging along -Y at rest) at a world point. */
    private static void reachArm(FormGeoModel model, String boneName, Matrix4f toGeo, Vec3 world) {
        model.getBone(boneName).ifPresent(bone -> {
            if (bone.getParent() != null) return;
            Vector3f shoulder = new Vector3f(bone.getPivotX() - bone.getPosX(), bone.getPivotY() + bone.getPosY(),
                    bone.getPivotZ() + bone.getPosZ());
            Vector3f target = toGeo.transformPosition(new Vector3f((float) world.x, (float) world.y, (float) world.z));
            Vector3f direction = target.sub(shoulder, new Vector3f());
            float distance = direction.length();
            if (distance < 1.0E-3F) return;
            direction.div(distance);
            setRotation(bone, new Quaternionf().rotationTo(new Vector3f(0.0F, -1.0F, 0.0F), direction));
            // The arm cannot bend, so a target closer than the arm would push the hand into the
            // ground; slide the arm back a little instead so the hand rests on the target.
            float slack = Math.min(ARM_LENGTH - distance, MAX_ARM_SLIDE);
            if (slack > 0.0F) {
                Vector3f back = new Vector3f(direction).mul(-slack);
                bone.setPosX(bone.getPosX() - back.x);
                bone.setPosY(bone.getPosY() + back.y);
                bone.setPosZ(bone.getPosZ() + back.z);
            }
        });
    }

    // ---- front player: tail to the follower's hands -----------------------------------------

    private static void aimTail(FormGeoModel model, Vector3f targetGeo) {
        List<GeoBone> tail = tailChain(model);
        if (tail.size() < 2) return;
        GeoBone root = tail.get(0);
        if (root.getParent() == null) return;

        int count = tail.size();
        Vector3f[] segments = new Vector3f[count];
        float stretchable = 0.0F;
        for (int i = 0; i < count - 1; i++) {
            segments[i] = pivot(tail.get(i + 1)).sub(pivot(tail.get(i)));
            stretchable += segments[i].length();
        }
        segments[count - 1] = new Vector3f(segments[count - 2]).mul(TIP_EXTENSION);
        float tip = segments[count - 1].length();
        if (stretchable < 1.0E-3F) return;

        // Rotate every segment onto the direction of the one before it: a straight tail.
        for (int i = 1; i < count; i++) {
            setRotation(tail.get(i), new Quaternionf().rotationTo(
                    new Vector3f(segments[i]).normalize(), new Vector3f(segments[i - 1]).normalize()));
        }

        // Aim the straight tail from the root pivot at the target, in the parent's rest frame.
        Vector3f target = new Matrix4f(restToPosed(root.getParent())).invert().transformPosition(new Vector3f(targetGeo));
        Vector3f reach = target.sub(pivot(root));
        float distance = reach.length();
        if (distance < 1.0E-3F) return;
        setRotation(root, new Quaternionf().rotationTo(new Vector3f(segments[0]).normalize(), reach.normalize()));
        for (GeoBone bone : tail) {
            bone.setPosX(0.0F);
            bone.setPosY(0.0F);
            bone.setPosZ(0.0F);
            bone.setScaleX(1.0F);
            bone.setScaleY(1.0F);
            bone.setScaleZ(1.0F);
        }

        // Stretch the whole tail as one piece. The tail and its fin lie in the root bone's Y-Z
        // plane, so scaling Y and Z together lengthens it along the aim line without shearing any
        // cube, and the children inherit the scale; the tail keeps its width (X).
        float stretch = Mth.clamp(distance / (stretchable + tip), MIN_STRETCH, MAX_STRETCH);
        root.setScaleY(stretch);
        root.setScaleZ(stretch);
    }

    /** {@code tail_base} (if present) followed by {@code tail_0, tail_1, ...} while each is a child of the last. */
    private static List<GeoBone> tailChain(FormGeoModel model) {
        List<GeoBone> chain = new ArrayList<>();
        model.getBone("tail_base").ifPresent(chain::add);
        for (int i = 0; ; i++) {
            GeoBone bone = model.getBone("tail_" + i).orElse(null);
            if (bone == null || (!chain.isEmpty() && bone.getParent() != chain.get(chain.size() - 1))) break;
            chain.add(bone);
        }
        return chain;
    }

    private static Vector3f pivot(GeoBone bone) {
        return new Vector3f(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
    }

    private static void setRotation(GeoBone bone, Quaternionf rotation) {
        // GeckoLib draws a bone with Rz * Ry * Rx; getEulerAnglesZYX returns exactly those angles.
        Vector3f euler = new Matrix3f().rotation(rotation).getEulerAnglesZYX(new Vector3f());
        bone.setRotX(euler.x);
        bone.setRotY(euler.y);
        bone.setRotZ(euler.z);
    }

    /**
     * Maps rest Geo pixels of a bone to posed Geo pixels, as GeckoLib's {@code prepMatrixForBone}
     * does for the bone and all its parents.
     */
    private static Matrix4f restToPosed(GeoBone bone) {
        List<GeoBone> chain = new ArrayList<>();
        for (GeoBone b = bone; b != null; b = b.getParent()) chain.add(0, b);
        Matrix4f matrix = new Matrix4f();
        for (GeoBone b : chain) {
            matrix.translate(-b.getPosX(), b.getPosY(), b.getPosZ())
                    .translate(b.getPivotX(), b.getPivotY(), b.getPivotZ())
                    .rotateZ(b.getRotZ()).rotateY(b.getRotY()).rotateX(b.getRotX())
                    .scale(b.getScaleX(), b.getScaleY(), b.getScaleZ())
                    .translate(-b.getPivotX(), -b.getPivotY(), -b.getPivotZ());
        }
        return matrix;
    }

    /**
     * Geo pixels (root bone space) to world coordinates: entity position, form scale, body yaw,
     * PAL body transforms, the vanilla player scale and the Geo object renderer offsets. Swim
     * rotation is not included: the crawl clips every chain member plays suppress it.
     */
    static Matrix4f geoToWorld(Player player, FormGeoAnimatable animatable, float partialTick) {
        FormDefinition form = FormManager.current(player);
        Vec3 pos = player.getPosition(partialTick);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        Matrix4f matrix = new Matrix4f()
                .translation((float) pos.x, (float) pos.y, (float) pos.z)
                .scale(form.widthScale(), form.heightScale(), form.widthScale())
                .rotateY((180.0F - bodyYaw) * DEG_TO_RAD);
        applyBodyTransform(matrix, animatable.getExternalBodyTransform());
        applyBodyTransform(matrix, animatable.getBodyTransform());
        return matrix.scale(-1.0F, -1.0F, 1.0F)
                .scale(PLAYER_SCALE)
                .translate(0.0F, -1.501F, 0.0F)
                .rotateX((float) Math.PI)
                .translate(0.0F, -1.51F, 0.0F)
                .translate(-0.5F, -0.5F, -0.5F)
                .translate(0.5F, 0.51F, 0.5F)
                .scale(1.0F / 16.0F);
    }

    private static void applyBodyTransform(Matrix4f matrix, BedrockAnimationPlayer.BodyTransform transform) {
        if (transform == null || transform.isIdentity()) return;
        matrix.translate(transform.x(), transform.y() + 0.7F, transform.z())
                .rotateZ(transform.roll())
                .rotateY(transform.yaw())
                .rotateX(transform.pitch())
                .translate(0.0F, -0.7F, 0.0F);
    }

    private AxolotlChainPose() {}
}
