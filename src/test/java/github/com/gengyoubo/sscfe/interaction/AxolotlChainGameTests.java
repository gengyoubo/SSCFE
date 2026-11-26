package github.com.gengyoubo.sscfe.interaction;

import com.mojang.authlib.GameProfile;
import github.com.gengyoubo.sscfe.Sscfe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.onixary.shapeShifterCurseForge.api.SscApi;

import java.util.UUID;

@GameTestHolder(Sscfe.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AxolotlChainGameTests {
    @GameTest(template = "empty")
    public static void newPlayersGrabTheLastTailAndEveryoneCrawls(GameTestHelper helper) {
        TestPlayer head = axolotl(helper), second = axolotl(helper), third = axolotl(helper);
        helper.assertTrue(AxolotlStackService.tryChain(second, head), "A follower grabs the head's tail");
        helper.assertTrue(AxolotlStackService.tryChain(third, head) && third.getVehicle() == second,
                "Clicking the head still adds the newcomer behind the last follower");
        head.refreshPose(); second.refreshPose(); third.refreshPose();
        helper.assertTrue(head.getPose() == Pose.SWIMMING && second.getPose() == Pose.SWIMMING
                        && third.getPose() == Pose.SWIMMING, "Every chain member crawls");
        helper.assertTrue(((AxolotlStackAccess) (Player) second).sscfe$isChainRider()
                        && !((AxolotlStackAccess) (Player) second).sscfe$isStackRider()
                        && AxolotlStackService.isChained(head) && !AxolotlStackService.isStacked(head),
                "Chain metadata is distinct from the stack");
        helper.assertTrue(AxolotlStackService.chainFollower(head) == second
                        && AxolotlStackService.chainFollower(second) == third, "Followers are found in order");
        helper.assertTrue(!AxolotlStackService.tryStack(axolotl(helper), head), "A stack cannot be built on a chain");
        AxolotlStackService.leave(head);
        AxolotlStackService.leave(second);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void followerSettlesBehindTheHeadOnTheGround(GameTestHelper helper) {
        TestPlayer head = axolotl(helper), follower = axolotl(helper);
        head.yBodyRot = 0F; // facing +Z, so the follower settles on the -Z side
        helper.assertTrue(AxolotlStackService.tryChain(follower, head), "Join before settling");
        for (int i = 0; i < 60; i++) head.positionRider(follower);
        double rest = AxolotlChainPhysics.REST_DISTANCE * AxolotlChainPhysics.scale(head);
        helper.assertTrue(Math.abs(horizontal(head, follower) - rest) < 0.02D,
                "The spring settles at the rest distance, got " + horizontal(head, follower));
        helper.assertTrue(follower.getZ() < head.getZ() - rest + 0.05D,
                "The follower ends up behind the head's body");
        helper.assertTrue(Math.abs(follower.getY() - head.getY()) < 1E-3D, "The follower lies on the same floor");
        helper.assertTrue(Math.abs(follower.yBodyRot) < 1.0F, "The follower faces the head, got " + follower.yBodyRot);
        AxolotlStackService.leave(head);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void movingHeadStretchesTheTailThenItRelaxes(GameTestHelper helper) {
        TestPlayer head = axolotl(helper), follower = axolotl(helper);
        head.yBodyRot = 0F;
        helper.assertTrue(AxolotlStackService.tryChain(follower, head), "Join before dragging");
        for (int i = 0; i < 60; i++) head.positionRider(follower);
        double scale = AxolotlChainPhysics.scale(head);
        double rest = AxolotlChainPhysics.REST_DISTANCE * scale;
        double max = 0;
        for (int i = 0; i < 12; i++) {
            head.setPos(head.position().add(0, 0, 0.15D));
            head.positionRider(follower);
            max = Math.max(max, horizontal(head, follower));
        }
        helper.assertTrue(max > rest + 0.2D, "A moving head drags the follower behind it, max " + max);
        helper.assertTrue(max <= AxolotlChainPhysics.MAX_DISTANCE * scale + 1E-6D, "The stretch is bounded");
        for (int i = 0; i < 60; i++) head.positionRider(follower);
        helper.assertTrue(Math.abs(horizontal(head, follower) - rest) < 0.02D, "Stopping lets the tail relax");
        AxolotlStackService.leave(head);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void followerSwingsBackInsideTheTailAngle(GameTestHelper helper) {
        TestPlayer head = axolotl(helper), follower = axolotl(helper);
        head.yBodyRot = 0F; // facing +Z: straight back is -Z
        follower.setPos(head.position().add(2.0D, 0, 0)); // 90 degrees off, beside the head
        helper.assertTrue(AxolotlStackService.tryChain(follower, head), "Join from the side");
        double previous = offAxisDegrees(head, follower), largestStep = 0;
        for (int i = 0; i < 40; i++) {
            head.positionRider(follower);
            double now = offAxisDegrees(head, follower);
            largestStep = Math.max(largestStep, previous - now);
            previous = now;
        }
        helper.assertTrue(previous <= AxolotlChainPhysics.MAX_ANGLE_DEGREES + 0.5D,
                "The follower ends inside the tail cone, got " + previous);
        helper.assertTrue(previous >= AxolotlChainPhysics.MAX_ANGLE_DEGREES - 0.5D,
                "It stops at the edge rather than snapping to straight behind, got " + previous);
        helper.assertTrue(largestStep <= AxolotlChainPhysics.MAX_SWING_DEGREES + 0.5D,
                "It swings back gradually, largest step " + largestStep);
        AxolotlStackService.leave(head);
        helper.succeed();
    }

    /** Angle between straight behind the head's body and the follower, degrees. */
    private static double offAxisDegrees(Player head, Player follower) {
        double yaw = Math.toRadians(head.yBodyRot);
        double backX = Math.sin(yaw), backZ = -Math.cos(yaw);
        double dx = follower.getX() - head.getX(), dz = follower.getZ() - head.getZ();
        return Math.abs(Math.toDegrees(Math.atan2(backX * dz - backZ * dx, backX * dx + backZ * dz)));
    }

    @GameTest(template = "empty")
    public static void sneakingLetsGoOfTheTail(GameTestHelper helper) {
        TestPlayer head = axolotl(helper), follower = axolotl(helper);
        helper.assertTrue(AxolotlStackService.tryChain(follower, head), "Join before letting go");
        follower.setShiftKeyDown(true);
        AxolotlStackService.maintain(follower);
        head.refreshPose(); follower.refreshPose();
        helper.assertTrue(!follower.isPassenger() && !AxolotlStackService.isChained(head)
                        && !((AxolotlStackAccess) (Player) follower).sscfe$isLinkRider()
                        && head.getPose() == Pose.STANDING, "Sneak releases the tail and restores the pose");
        helper.succeed();
    }

    private static double horizontal(Player a, Player b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The template is empty air; the chain needs a real floor to lie on. */
    private static void floor(GameTestHelper helper) {
        for (int x = -4; x <= 12; x++) {
            for (int z = -4; z <= 12; z++) {
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, z)),
                        Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static TestPlayer axolotl(GameTestHelper helper) {
        if (helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(4, 1, 4))).isAir()) floor(helper);
        TestPlayer player = new TestPlayer(helper.getLevel());
        var data = SscApi.currentForm(player).orElseThrow();
        data.setFormId("shape-shifter-curse:axolotl_3");
        data.setFormGroupId("shape-shifter-curse:axolotl_form");
        data.setFormTier(3);
        data.setContentEnabled(true);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        player.setOnGround(true);
        return player;
    }

    private static final class TestPlayer extends Player {
        private TestPlayer(Level level) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "chain-test")); }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
        void refreshPose() { updatePlayerPose(); }
    }
}
