package github.com.gengyoubo.sscfe.interaction;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.sscfe.Sscfe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.onixary.shapeShifterCurseForge.form.FormManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Sscfe.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AxolotlStackFakePlayerGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spawnedFakePlayersTickFollowAndSneakDismount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState());
        }
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        TickingFakePlayer base = spawn(level, "stack-base", origin);
        TickingFakePlayer middle = spawn(level, "stack-middle", origin.add(0.7D, 0, 0));
        TickingFakePlayer top = spawn(level, "stack-top", origin.add(0, 0, 0.7D));
        List<TickingFakePlayer> players = List.of(base, middle, top);
        helper.runAfterDelay(5, () -> checked(players, () -> {
            helper.assertTrue(level.getEntity(base.getId()) == base && base.onGround(),
                    "Fake players are spawned and settled by the real world tick");
            helper.assertTrue(AxolotlStackService.tryStack(middle, base)
                            && AxolotlStackService.tryStack(top, base), "Three spawned fake players stack");
            Vec3 before = base.position();
            base.setDeltaMovement(new Vec3(0.12D, 0, 0.06D));
            helper.runAfterDelay(20, () -> checked(players, () -> {
                helper.assertTrue(base.position().distanceToSqr(before) > 0.001D,
                        "Carrier actually moves during world simulation");
                for (TickingFakePlayer player : players) {
                    helper.assertTrue(player.tickCount >= 20 && player.getPose() == Pose.SWIMMING,
                            "Real player ticks preserve crawl without manual pose updates");
                }
                helper.assertTrue(middle.position().distanceTo(AxolotlStackService.passengerPosition(base)) < 1E-5D
                                && top.position().distanceTo(AxolotlStackService.passengerPosition(middle)) < 1E-5D,
                        "Native passenger ticking positions every layer without manual positionRider calls");
                helper.assertTrue(Math.abs(middle.position().subtract(base.position()).horizontalDistance() - AxolotlStackService.LAYER_BACK_OFFSET) < 1E-5D
                                && Math.abs(top.position().subtract(middle.position()).horizontalDistance() - AxolotlStackService.LAYER_BACK_OFFSET) < 1E-5D,
                        "Every simulated layer keeps its backward step while the carrier moves");
                helper.assertTrue(Math.abs(middle.getY() - base.getY() - AxolotlStackService.LAYER_HEIGHT) < 1E-5D
                                && Math.abs(top.getY() - middle.getY() - AxolotlStackService.LAYER_HEIGHT) < 1E-5D,
                        "Spawned riders maintain the lowered back height through native ticks");
                middle.setShiftKeyDown(true);
                helper.runAfterDelay(4, () -> checked(players, () -> {
                    helper.assertTrue(!middle.isPassenger() && !top.isPassenger() && !base.isVehicle(),
                            "Native Sneak dismount and event cleanup split both middle-layer links");
                    helper.assertTrue(!((AxolotlStackAccess) middle).sscfe$isStackRider()
                                    && !((AxolotlStackAccess) top).sscfe$isStackRider()
                                    && base.getPose() == Pose.STANDING,
                            "Dismount clears metadata and restores the base pose on subsequent ticks");
                    cleanup(players);
                    helper.succeed();
                }));
            }));
        }));
    }

    /** Eight ticking fake players: every rider must stay pressed onto the base while it walks. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void eightFakePlayersHugTheBasePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState());
        }
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        List<TickingFakePlayer> players = new ArrayList<>();
        for (int i = 0; i < AxolotlStackService.MAX_PLAYERS; i++) {
            double angle = Math.PI * 2 * i / AxolotlStackService.MAX_PLAYERS;
            Vec3 at = i == 0 ? origin : origin.add(Math.cos(angle) * 0.8D, 0, Math.sin(angle) * 0.8D);
            players.add(spawn(level, "hug-" + i, at));
        }
        TickingFakePlayer base = players.get(0);
        helper.runAfterDelay(5, () -> checked(players, () -> {
            for (int i = 1; i < players.size(); i++) {
                helper.assertTrue(AxolotlStackService.tryStack(players.get(i), base), "Fake player " + i + " joins");
            }
            base.setYBodyRot(40F);
            base.setDeltaMovement(new Vec3(0.1D, 0, 0.05D));
            helper.runAfterDelay(20, () -> checked(players, () -> {
                StringBuilder report = new StringBuilder("Axolotl stack layout (relative to base):");
                double hug = AxolotlStackService.LAYER_HEIGHT, back = AxolotlStackService.LAYER_BACK_OFFSET;
                for (int i = 1; i < players.size(); i++) {
                    TickingFakePlayer rider = players.get(i), below = players.get(i - 1);
                    Vec3 rel = rider.position().subtract(base.position());
                    report.append(String.format("%n  layer %d: dx=%.3f dy=%.3f dz=%.3f horiz=%.3f pose=%s",
                            i, rel.x, rel.y, rel.z, rel.horizontalDistance(), rider.getPose()));
                    helper.assertTrue(rider.getVehicle() == below, "Layer " + i + " rides the layer below");
                    helper.assertTrue(Math.abs(rel.y - i * hug) < 1E-5D
                                    && Math.abs(rel.horizontalDistance() - i * back) < 1E-5D,
                            "Layer " + i + " stays pressed onto the base");
                    helper.assertTrue(rider.getBoundingBox().intersects(below.getBoundingBox()),
                            "Layer " + i + " overlaps the layer below instead of floating above it");
                    helper.assertTrue(rider.getPose() == Pose.SWIMMING, "Layer " + i + " crawls");
                }
                LogUtils.getLogger().info(report.toString());
                cleanup(players);
                helper.succeed();
            }));
        }));
    }

    private static TickingFakePlayer spawn(ServerLevel level, String name, Vec3 position) {
        TickingFakePlayer player = new TickingFakePlayer(level, name);
        player.setPos(position);
        FormManager.setForm(player, ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "axolotl_3"), false);
        level.addNewPlayer(player);
        return player;
    }

    private static void checked(List<TickingFakePlayer> players, Runnable test) {
        try { test.run(); }
        catch (RuntimeException | Error failure) { cleanup(players); throw failure; }
    }

    private static void cleanup(List<TickingFakePlayer> players) {
        for (TickingFakePlayer player : players) AxolotlStackService.leave(player);
        for (TickingFakePlayer player : players) {
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }

    // Forge FakePlayer.tick() is intentionally empty. Run ServerPlayer's normal
    // player tick so physics, pose updates, Forge events and rider ticks are exercised.
    private static class TickingFakePlayer extends FakePlayer {
        TickingFakePlayer(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.randomUUID(), name));
        }
        @Override public void tick() { doTick(); }
    }
}
