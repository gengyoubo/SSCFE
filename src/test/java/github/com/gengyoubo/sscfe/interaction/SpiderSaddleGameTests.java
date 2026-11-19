package github.com.gengyoubo.sscfe.interaction;

import com.mojang.authlib.GameProfile;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.init.ModInteractionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.onixary.shapeShifterCurseForge.form.FormManager;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Sscfe.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiderSaddleGameTests {
    private static final String SPIDER_3 = "spider_3";

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rightClickRidesSaddledSpiderAndSneakDismounts(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        Rider spider = scene.spawn("spider", SPIDER_3, 0), rider = scene.spawn("rider", null, 1);
        saddle(spider, true);
        helper.runAfterDelay(5, () -> scene.check(() -> {
            helper.assertTrue(SpiderSaddleService.hasSaddle(spider), "The saddle is worn in the Curios back slot");
            helper.assertTrue(rider.interactOn(spider, InteractionHand.MAIN_HAND).consumesAction(),
                    "An ordinary player's right click is consumed by the saddle");
            helper.assertTrue(rider.getVehicle() == spider && SpiderSaddleService.isRider(rider),
                    "The ordinary player rides the spider");
            Vec3 before = spider.position();
            spider.setDeltaMovement(0.12D, 0, 0.05D);
            helper.runAfterDelay(15, () -> scene.check(() -> {
                helper.assertTrue(spider.position().distanceToSqr(before) > 0.001D, "The spider keeps moving with a rider");
                helper.assertTrue(rider.position().distanceTo(SpiderSaddleService.seatPosition(spider)) < 1E-5D,
                        "Native rider ticks keep the rider on the abdomen seat");
                helper.assertTrue(spider.shouldRiderSit(), "The rider uses the seated riding pose");
                rider.setShiftKeyDown(true);
                helper.runAfterDelay(3, () -> scene.check(() -> {
                    helper.assertTrue(!rider.isPassenger() && !spider.isVehicle(), "Sneak dismounts the rider");
                    AxolotlStackService.maintain(rider);
                    SpiderSaddleService.maintain(rider);
                    helper.assertTrue(!SpiderSaddleService.isRider(rider), "Dismounting clears the ride record");
                    scene.finish();
                }));
            }));
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void onlySaddledFinalSpidersCarryHumanoidRiders(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        Rider spider = scene.spawn("spider", SPIDER_3, 0), rider = scene.spawn("rider", "catgirl_0", 1);
        helper.runAfterDelay(5, () -> scene.check(() -> {
            helper.assertTrue(!rider.interactOn(spider, InteractionHand.MAIN_HAND).consumesAction() && !rider.isPassenger(),
                    "A spider without the saddle cannot be ridden");
            saddle(spider, true);
            for (String form : new String[]{"axolotl_3", SPIDER_3, "ocelot_3", "snow_fox_3"}) {
                setForm(rider, form);
                helper.assertTrue(!SpiderSaddleService.tryMount(rider, spider) && !rider.isPassenger(),
                        form + " is not a humanoid rider");
            }
            setForm(spider, "spider_2");
            setForm(rider, "catgirl_0");
            helper.assertTrue(!SpiderSaddleService.tryMount(rider, spider), "Only the final spider form can carry");
            helper.assertTrue(!SpiderSaddleService.canCarry(spider), "A saddle forced onto spider_2 does nothing");
            setForm(spider, SPIDER_3);
            helper.assertTrue(SpiderSaddleService.tryMount(rider, spider), "A humanoid form rides the saddled spider");
            Rider second = scene.spawn("second", null, 2);
            helper.assertTrue(!SpiderSaddleService.tryMount(second, spider) && !second.isPassenger(),
                    "The saddle carries a single rider");
            helper.assertTrue(!SpiderSaddleService.tryMount(spider, rider), "Riders cannot be turned into carriers");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void losingSaddleOrFormDropsTheRider(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        Rider spider = scene.spawn("spider", SPIDER_3, 0), rider = scene.spawn("rider", null, 1);
        saddle(spider, true);
        helper.runAfterDelay(5, () -> scene.check(() -> {
            helper.assertTrue(SpiderSaddleService.tryMount(rider, spider), "Mount before removing the saddle");
            saddle(spider, false);
            helper.runAfterDelay(2, () -> scene.check(() -> {
                helper.assertTrue(!rider.isPassenger(), "Unequipping the saddle drops the rider");
                saddle(spider, true);
                helper.assertTrue(SpiderSaddleService.tryMount(rider, spider), "Mount before transforming");
                setForm(spider, "spider_2");
                helper.runAfterDelay(2, () -> scene.check(() -> {
                    helper.assertTrue(!rider.isPassenger(), "Leaving the final form drops the rider");
                    scene.finish();
                }));
            }));
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void logoutAndUnrelatedRidesAreHandledSafely(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        Rider spider = scene.spawn("spider", SPIDER_3, 0), rider = scene.spawn("rider", null, 1);
        saddle(spider, true);
        helper.runAfterDelay(5, () -> scene.check(() -> {
            helper.assertTrue(SpiderSaddleService.tryMount(rider, spider), "Mount before logging out");
            SpiderSaddleService.logout(new PlayerEvent.PlayerLoggedOutEvent(rider));
            helper.assertTrue(!rider.isPassenger() && !spider.isRemoved(),
                    "A rider logging out dismounts first, so the spider player is not unloaded with it");
            helper.assertTrue(SpiderSaddleService.tryMount(rider, spider), "Mount before the spider logs out");
            SpiderSaddleService.logout(new PlayerEvent.PlayerLoggedOutEvent(spider));
            helper.assertTrue(!spider.isVehicle(), "A spider logging out drops its rider");

            saddle(spider, false);
            helper.assertTrue(rider.startRiding(spider), "A foreign player ride starts normally");
            helper.runAfterDelay(2, () -> scene.check(() -> {
                helper.assertTrue(rider.getVehicle() == spider, "Rides created elsewhere are not dismantled");
                rider.stopRiding();
                scene.finish();
            }));
        }));
    }

    private static void saddle(Rider player, boolean worn) {
        ItemStack stack = worn ? new ItemStack(ModInteractionContent.SPIDER_SADDLE.get()) : ItemStack.EMPTY;
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.setEquippedCurio("back", 0, stack));
    }

    private static void setForm(Rider player, String form) {
        String namespace = form.startsWith("catgirl") ? Sscfe.MOD_ID : "shape-shifter-curse";
        FormManager.setForm(player, ResourceLocation.fromNamespaceAndPath(namespace, form), false);
    }

    /** Ticking fake players on a stone floor, removed even when an assertion fails. */
    private static final class Scene {
        private final GameTestHelper helper;
        private final List<Rider> players = new ArrayList<>();

        Scene(GameTestHelper helper) {
            this.helper = helper;
            for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState());
            }
        }

        Rider spawn(String name, String form, int index) {
            ServerLevel level = helper.getLevel();
            Rider player = new Rider(level, "saddle-" + name);
            Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1 + index % 2, 2, 1 + index / 2)));
            player.setPos(at);
            if (form != null) setForm(player, form);
            level.addNewPlayer(player);
            players.add(player);
            return player;
        }

        void check(Runnable test) {
            try { test.run(); }
            catch (RuntimeException | Error failure) { cleanup(); throw failure; }
        }

        void finish() {
            cleanup();
            helper.succeed();
        }

        private void cleanup() {
            for (Rider player : players) SpiderSaddleService.leave(player);
            for (Rider player : players) {
                player.stopRiding();
                player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
            players.clear();
        }
    }

    /** Forge FakePlayer.tick() is empty; run the normal player tick so riding and dismounting are exercised. */
    private static final class Rider extends FakePlayer {
        Rider(ServerLevel level, String name) { super(level, new GameProfile(UUID.randomUUID(), name)); }
        @Override public void tick() { doTick(); }
    }
}
