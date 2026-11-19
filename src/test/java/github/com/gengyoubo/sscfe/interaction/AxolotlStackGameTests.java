package github.com.gengyoubo.sscfe.interaction;

import com.mojang.authlib.GameProfile;
import github.com.gengyoubo.sscfe.Sscfe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.onixary.shapeShifterCurseForge.api.SscApi;
import net.onixary.shapeShifterCurseForge.form.FormRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Sscfe.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AxolotlStackGameTests {
    @GameTest(template = "empty")
    public static void stackForcesCrawlAndFollowsTheCarriersBack(GameTestHelper helper) {
        TestPlayer base = axolotl(helper), rider = axolotl(helper);
        helper.assertTrue(AxolotlStackService.tryStack(rider, base), "Nearby axolotls can stack");
        base.refreshPose(); rider.refreshPose();
        helper.assertTrue(base.getPose() == Pose.SWIMMING && rider.getPose() == Pose.SWIMMING,
                "Both participants crawl without holding Sneak");
        helper.assertTrue(((AxolotlStackAccess) (Player) rider).sscfe$isStackRider(),
                "Rider metadata identifies the stack for tracking clients");
        helper.assertTrue(!base.shouldRiderSit(), "The upper axolotl does not use a seated riding pose");
        base.setPos(base.position().add(0.5D, 0, 0.5D));
        base.yBodyRot = 75F;
        base.positionRider(rider);
        helper.assertTrue(rider.position().distanceTo(AxolotlStackService.passengerPosition(base)) < 1E-6D,
                "Rider follows the back rather than vanilla player riding offsets");
        helper.assertTrue(rider.yBodyRot == base.yBodyRot, "Bodies face the same direction");
        AxolotlStackService.leave(rider);
        base.refreshPose(); rider.refreshPose();
        helper.assertTrue(!base.isVehicle() && !rider.isPassenger() && base.getPose() == Pose.STANDING
                        && rider.getPose() == Pose.STANDING && base.shouldRiderSit(),
                "Leaving restores normal pose and ordinary riding behavior");
        helper.assertTrue(!((AxolotlStackAccess) (Player) rider).sscfe$isStackRider(),
                "Leaving clears synchronized stack metadata");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clickingAnyLayerAddsToTopAndRejectsCycles(GameTestHelper helper) {
        TestPlayer base = axolotl(helper), middle = axolotl(helper), upper = axolotl(helper);
        helper.assertTrue(AxolotlStackService.tryStack(middle, base), "First rider mounts");
        helper.assertTrue(AxolotlStackService.tryStack(upper, base) && upper.getVehicle() == middle,
                "Clicking the occupied base adds the next rider above the first");
        helper.assertTrue(!AxolotlStackService.tryStack(base, upper)
                        && !AxolotlStackService.tryStack(middle, upper)
                        && !AxolotlStackService.tryStack(upper, upper),
                "Existing participants cannot create cycles, move stacks or mount themselves");
        AxolotlStackService.leave(middle);
        helper.assertTrue(!base.isVehicle() && !middle.isPassenger() && !upper.isPassenger(),
                "A middle player can leave without dangling links");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void layersStepBackWithBodyDirection(GameTestHelper helper) {
        TestPlayer base = axolotl(helper), middle = axolotl(helper), top = axolotl(helper);
        helper.assertTrue(AxolotlStackService.tryStack(middle, base)
                        && AxolotlStackService.tryStack(top, base), "Three players join before turning");
        double h = AxolotlStackService.LAYER_HEIGHT, b = AxolotlStackService.LAYER_BACK_OFFSET;
        helper.assertTrue(Math.abs(middle.getY() - base.getY() - h) < 1E-6D
                        && Math.abs(top.getY() - middle.getY() - h) < 1E-6D,
                "Each upper layer hugs the layer below instead of sitting on its full hitbox");
        double[][] directions = {{0, 0, -b}, {90, b, 0}, {180, 0, b}, {270, -b, 0}};
        for (double[] direction : directions) {
            base.yBodyRot = (float) direction[0];
            base.setYHeadRot(base.yBodyRot + 90F);
            base.setXRot(85F);
            base.positionRider(middle);
            middle.positionRider(top);
            helper.assertTrue(Math.abs(middle.getX() - base.getX() - direction[1]) < 1E-6D
                            && Math.abs(middle.getZ() - base.getZ() - direction[2]) < 1E-6D,
                    "Each rider stays behind the body even when the head looks elsewhere");
            helper.assertTrue(Math.abs(top.getX() - base.getX() - 2 * direction[1]) < 1E-6D
                            && Math.abs(top.getZ() - base.getZ() - 2 * direction[2]) < 1E-6D
                            && top.getY() > middle.getY() && middle.getY() > base.getY(),
                    "Backward offsets accumulate into a staircase and rotate with the carrier");
        }
        AxolotlStackService.leave(middle);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stackHasABoundedHeight(GameTestHelper helper) {
        List<TestPlayer> players = new ArrayList<>();
        TestPlayer base = axolotl(helper);
        players.add(base);
        for (int i = 1; i < AxolotlStackService.MAX_PLAYERS; i++) {
            TestPlayer rider = axolotl(helper);
            helper.assertTrue(AxolotlStackService.tryStack(rider, base), "Valid layer " + i + " joins");
            players.add(rider);
        }
        helper.assertTrue(!AxolotlStackService.tryStack(axolotl(helper), base), "Oversized stacks are rejected");
        players.forEach(AxolotlStackService::leave);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void serverRejectsDistanceWallsAndInvalidForms(GameTestHelper helper) {
        TestPlayer base = axolotl(helper), rider = axolotl(helper);
        rider.setPos(base.position().add(20, 0, 0));
        helper.assertTrue(!AxolotlStackService.tryStack(rider, base), "Remote interaction packets cannot mount");
        rider.setPos(base.position());
        SscApi.currentForm(rider).orElseThrow().setFormId(FormRegistry.ORIGINAL_BEFORE_ENABLE.toString());
        helper.assertTrue(!AxolotlStackService.tryStack(rider, base), "A non-axolotl cannot join");
        makeAxolotl(rider);
        base.setSpectator(true);
        helper.assertTrue(!AxolotlStackService.tryStack(rider, base), "Spectator targets cannot be stacked");
        base.setSpectator(false);
        base.getAbilities().flying = true;
        helper.assertTrue(!AxolotlStackService.tryStack(rider, base), "Flying targets cannot be stacked");
        base.getAbilities().flying = false;
        rider.setPos(base.position().add(2, 0, 0));
        BlockPos wall = BlockPos.containing(base.position().add(1, 0, 0));
        for (int y = 0; y < 3; y++) base.level().setBlockAndUpdate(wall.above(y), Blocks.STONE.defaultBlockState());
        helper.assertTrue(!AxolotlStackService.tryStack(rider, base), "Line of sight is checked on the server");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noHeadroomRestoresTheOriginalPose(GameTestHelper helper) {
        TestPlayer base = axolotl(helper), rider = axolotl(helper);
        base.setPos(base.position().add(0, 0.3D, 0));
        rider.setPos(base.position());
        base.level().setBlockAndUpdate(BlockPos.containing(base.position()).above(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(!AxolotlStackService.tryStack(rider, base), "A rider cannot be placed into a ceiling");
        helper.assertTrue(base.getPose() == Pose.STANDING && rider.getPose() == Pose.STANDING
                        && !base.isVehicle() && !rider.isPassenger(),
                "Failed interaction does not force the target to crawl");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sneakFormChangeAndLogoutReleaseParticipants(GameTestHelper helper) {
        TestPlayer base = axolotl(helper), rider = axolotl(helper);
        helper.assertTrue(AxolotlStackService.tryStack(rider, base), "Mount before testing Sneak");
        base.setShiftKeyDown(true);
        AxolotlStackService.maintain(base);
        helper.assertTrue(!rider.isPassenger() && !AxolotlStackService.isStacked(base), "Base Sneak releases its rider");
        base.setShiftKeyDown(false);

        rider = axolotl(helper);
        helper.assertTrue(AxolotlStackService.tryStack(rider, base), "Mount before testing transformation");
        SscApi.currentForm(base).orElseThrow().setFormId(FormRegistry.ORIGINAL_BEFORE_ENABLE.toString());
        AxolotlStackService.maintain(rider);
        helper.assertTrue(!rider.isPassenger(), "Transforming the carrier releases the rider");
        makeAxolotl(base);
        rider = axolotl(helper);
        helper.assertTrue(AxolotlStackService.tryStack(rider, base), "Mount before testing logout");
        AxolotlStackService.logout(new PlayerEvent.PlayerLoggedOutEvent(base));
        helper.assertTrue(!rider.isPassenger() && !base.isVehicle(), "Logging out leaves no passenger link");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryPlayerRidingIsPreserved(GameTestHelper helper) {
        TestPlayer base = axolotl(helper), rider = axolotl(helper);
        helper.assertTrue(rider.startRiding(base), "A foreign player riding relationship starts normally");
        base.setShiftKeyDown(true);
        AxolotlStackService.maintain(base);
        helper.assertTrue(rider.getVehicle() == base && !AxolotlStackService.isStacked(base)
                        && base.shouldRiderSit(),
                "Unrelated player rides are not dismantled or given a stack pose");
        rider.stopRiding();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void existingVehiclesAndDeadPlayersAreNotHijacked(GameTestHelper helper) {
        TestPlayer base = axolotl(helper), rider = axolotl(helper);
        Boat boat = new Boat(helper.getLevel(), base.getX(), base.getY(), base.getZ());
        helper.assertTrue(base.startRiding(boat), "Base starts in a normal boat");
        helper.assertTrue(!AxolotlStackService.tryStack(rider, base) && base.getVehicle() == boat,
                "Interaction preserves an existing boat ride");
        base.stopRiding();
        helper.assertTrue(AxolotlStackService.tryStack(rider, base), "Mount after leaving the boat");
        base.setHealth(0);
        AxolotlStackService.maintain(rider);
        helper.assertTrue(!rider.isPassenger(), "A dead carrier cannot retain riders");
        helper.succeed();
    }

    private static TestPlayer axolotl(GameTestHelper helper) {
        TestPlayer player = new TestPlayer(helper.getLevel());
        makeAxolotl(player);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        player.setOnGround(true);
        return player;
    }

    private static void makeAxolotl(Player player) {
        var data = SscApi.currentForm(player).orElseThrow();
        data.setFormId("shape-shifter-curse:axolotl_3");
        data.setFormGroupId("shape-shifter-curse:axolotl_form");
        data.setFormTier(3);
        data.setContentEnabled(true);
    }

    private static final class TestPlayer extends Player {
        private boolean spectator;
        private TestPlayer(Level level) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "stack-test")); }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return spectator; }
        void setSpectator(boolean value) { spectator = value; }
        void refreshPose() { updatePlayerPose(); }
    }
}
