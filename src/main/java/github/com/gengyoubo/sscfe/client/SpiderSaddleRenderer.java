package github.com.gengyoubo.sscfe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.interaction.SpiderSaddleService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the vanilla pig saddle on a spider final form's abdomen while a Spider Saddle is worn.
 * The abdomen pose is captured while SSC renders the form model and replayed in RenderPlayerEvent.Post,
 * where a separate texture can be drawn without interrupting the form model's vertex buffer.
 * The same pose gives the animated top of the abdomen, and the rider is drawn there so it sways with the back.
 */
@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, value = Dist.CLIENT)
public final class SpiderSaddleRenderer {
    public static final String ABDOMEN_BONE = "tail_0";
    private static final ResourceLocation SADDLE = ResourceLocation.withDefaultNamespace("textures/entity/pig/pig_saddle.png");
    // form_spider_3 abdomen cube (model pixels, x already mirrored like GeckoLib does): rotated -80 degrees
    // (Bedrock sign) about its pivot; 10 wide, 11 along local y, 9 along local z, with local -z facing up.
    private static final float PIVOT_X = 0.5F, PIVOT_Y = 14.34337F, PIVOT_Z = -7.65039F, CUBE_PITCH = 80.0F;
    private static final float CENTER_X = 0.0F, CENTER_Y = 13.34337F, CENTER_Z = -4.65039F;
    // Pig saddle body is 10 x 8 x 16 pixels (width, thickness, length).
    private static final float SCALE_WIDTH = 1.0F, SCALE_THICKNESS = 9.0F / 8.0F, SCALE_LENGTH = 11.0F / 16.0F;
    private static final Map<Player, Matrix4f[]> POSES = new WeakHashMap<>();
    private static final Map<Player, Matrix3f> NORMALS = new WeakHashMap<>();
    /** Rider feet below the saddle top; riders sink into the saddle a little on purpose. */
    private static final double RIDER_DROP = 0.72D;
    /** Animated saddle top relative to the spider's position (world axes), refreshed every rendered frame. */
    private static final Map<Player, Seat> SEATS = new WeakHashMap<>();
    private static ModelPart saddle;

    /** Seat point plus the abdomen's rotation away from its rest orientation (both relative to the spider). */
    private record Seat(Vec3 offset, Quaternionf sway, long time) {}

    public static void capture(Player player, PoseStack poseStack) {
        if (player == null || !SpiderSaddleService.isSpiderFinal(player) || !SpiderSaddleService.hasSaddle(player)) return;
        POSES.put(player, new Matrix4f[]{new Matrix4f(poseStack.last().pose())});
        NORMALS.put(player, new Matrix3f(poseStack.last().normal()));
    }

    @SubscribeEvent
    public static void render(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        Matrix4f[] pose = POSES.remove(player);
        Matrix3f normal = NORMALS.remove(player);
        if (pose == null || normal == null) return;
        if (saddle == null) saddle = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PIG_SADDLE).getChild("body");

        PoseStack stack = event.getPoseStack();
        Matrix4f entityFrame = new Matrix4f(stack.last().pose());
        stack.pushPose();
        stack.last().pose().set(pose[0]);
        stack.last().normal().set(normal);
        // Replay the cube's own rotation about its pivot, exactly as GeckoLib renders it.
        stack.translate(PIVOT_X / 16.0F, PIVOT_Y / 16.0F, PIVOT_Z / 16.0F);
        stack.mulPose(Axis.XP.rotationDegrees(CUBE_PITCH));
        stack.translate((CENTER_X - PIVOT_X) / 16.0F, (CENTER_Y - PIVOT_Y) / 16.0F, (CENTER_Z - PIVOT_Z) / 16.0F);
        // Centre of the saddle seat: the cube's up-facing -z face. Express it relative to the spider.
        Matrix4f toSpider = entityFrame.invert().mul(stack.last().pose(), new Matrix4f());
        Vector4f top = toSpider.transform(new Vector4f(0, 0, -5.0F / 16.0F, 1));
        float bodyYaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        SEATS.put(player, new Seat(new Vec3(top.x, top.y, top.z), sway(toSpider, bodyYaw), System.nanoTime()));
        // Pig axes -> cube axes: saddle top (pig -y) to cube -z, pig length to cube y, head end towards cube +y.
        stack.mulPose(Axis.XP.rotationDegrees(90));
        stack.scale(SCALE_WIDTH, SCALE_THICKNESS, SCALE_LENGTH);
        // Centre the pig body (y 10..18, z -8..8 after its own 90 degree pitch) on the origin.
        saddle.setPos(0, -3, 2);
        saddle.xRot = (float) (Math.PI / 2);
        saddle.render(stack, event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(SADDLE)),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        stack.popPose();
    }

    /**
     * Rotation of the abdomen cube away from its rest orientation. At rest the (left-right symmetric) cube has
     * its local x axis level and sideways, local y tilted 10 degrees up from the body's long axis and local -z
     * pointing almost straight up; the idle animation wags it from there.
     */
    private static Quaternionf sway(Matrix4f cubeToSpider, float bodyYaw) {
        Matrix3f current = new Matrix3f();
        cubeToSpider.get3x3(current);
        Vector3f cx = current.getColumn(0, new Vector3f()).normalize();
        Vector3f cy = current.getColumn(1, new Vector3f()).normalize();
        Vector3f cz = current.getColumn(2, new Vector3f()).normalize();
        Vector3f up = new Vector3f(0, 1, 0);
        double yaw = Math.toRadians(bodyYaw);
        Vector3f forward = new Vector3f((float) -Math.sin(yaw), 0, (float) Math.cos(yaw));
        // The model's long axis may point forward or backward in this frame: use whichever matches.
        float along = Math.signum(new Vector3f(cy.x, 0, cy.z).dot(forward));
        if (along == 0) along = 1;
        float pitch = (float) Math.toRadians(CUBE_PITCH);
        Vector3f ry = new Vector3f(up).mul((float) Math.cos(pitch)).add(new Vector3f(forward).mul(along * (float) Math.sin(pitch)));
        Vector3f rz = new Vector3f(up).mul((float) -Math.sin(pitch)).add(new Vector3f(forward).mul(along * (float) Math.cos(pitch)));
        Vector3f rx = new Vector3f(ry).cross(rz);
        if (new Vector3f(cy).cross(cz).dot(cx) < 0) rx.negate(); // keep the same handedness as the render frame
        Matrix3f now = new Matrix3f().setColumn(0, cx).setColumn(1, cy).setColumn(2, cz);
        Matrix3f rest = new Matrix3f().setColumn(0, rx).setColumn(1, ry).setColumn(2, rz);
        Quaternionf delta = now.mul(rest.transpose()).getNormalizedRotation(new Quaternionf());
        // Anything far from a small wag means an unexpected model pose: fall back to translation only.
        return delta.angle() < Math.toRadians(35) ? delta : new Quaternionf();
    }

    /** Latest animated saddle top relative to the spider, or null when it is not being rendered. */
    public static Vec3 animatedSeat(Player spider) {
        Seat seat = SEATS.get(spider);
        return seat == null ? null : seat.offset();
    }

    /** Latest abdomen sway angle in degrees (for diagnostics), or NaN when it is not being rendered. */
    public static double swayDegrees(Player spider) {
        Seat seat = SEATS.get(spider);
        return seat == null ? Double.NaN : Math.toDegrees(seat.sway().angle());
    }

    /** Runs before SSC draws a humanoid form rider, so both the form model and vanilla parts move together. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void moveRider(RenderPlayerEvent.Pre event) {
        Player rider = event.getEntity();
        if (!(rider.getVehicle() instanceof Player spider) || !SpiderSaddleService.isSaddleLink(spider, rider)) return;
        Seat seat = SEATS.get(spider);
        if (seat == null || System.nanoTime() - seat.time() > 250_000_000L) return;
        float partial = event.getPartialTick();
        Vec3 target = spider.getPosition(partial).add(seat.offset()).subtract(0, RIDER_DROP, 0);
        Vec3 delta = target.subtract(rider.getPosition(partial));
        // EntityRenderDispatcher pops this entity's pose afterwards, so the shift never leaks.
        PoseStack stack = event.getPoseStack();
        stack.translate(delta.x, delta.y, delta.z);
        // Turn the rider with the abdomen about the seat point, so it moves rigidly with the saddle.
        stack.translate(0, RIDER_DROP, 0);
        stack.mulPose(seat.sway());
        stack.translate(0, -RIDER_DROP, 0);
    }

    private SpiderSaddleRenderer() {}
}
