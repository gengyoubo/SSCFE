package github.com.gengyoubo.sscfe.water;

import com.mojang.authlib.GameProfile;
import github.com.gengyoubo.sscfe.Sscfe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Sscfe.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterPurpleExplosionGameTests {
    private static ServerPlayer player(GameTestHelper helper, Vec3 center) {
        var player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "water-burst-test"));
        player.setPos(center.x, center.y, center.z); return player;
    }

    @GameTest(template = "empty")
    public static void lavaCoolingPreservesProductsWaterContainersAndProtection(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).atY(230);
        var player = player(helper, Vec3.atCenterOf(origin));
        var terrain = new WaterPurpleTerrain(level, player);
        var fixtures = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        fixtures.put(origin, Blocks.LAVA.defaultBlockState());
        fixtures.put(origin.offset(2, 0, 0), Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 4));
        fixtures.put(origin.offset(4, 0, 0), Blocks.WATER.defaultBlockState());
        fixtures.put(origin.offset(6, 0, 0), Blocks.OBSIDIAN.defaultBlockState());
        fixtures.put(origin.offset(8, 0, 0), Blocks.CHEST.defaultBlockState());
        fixtures.put(origin.offset(10, 0, 0), Blocks.STONE.defaultBlockState());
        BlockPos protectedLava = origin.offset(12, 0, 0), replacedLava = origin.offset(14, 0, 0);
        fixtures.put(protectedLava, Blocks.LAVA.defaultBlockState()); fixtures.put(replacedLava, Blocks.LAVA.defaultBlockState());
        fixtures.forEach((pos, state) -> level.setBlock(pos, state, 2));
        Consumer<BlockEvent.BreakEvent> protection = event -> {
            if (event.getLevel() != level) return;
            if (event.getPos().equals(protectedLava)) event.setCanceled(true);
            if (event.getPos().equals(replacedLava)) level.setBlock(replacedLava, Blocks.CHEST.defaultBlockState(), 2);
        };
        MinecraftForge.EVENT_BUS.addListener(protection);
        try {
            fixtures.keySet().forEach(terrain::process);
            helper.assertTrue(level.getBlockState(origin).is(Blocks.OBSIDIAN)
                    && level.getBlockState(origin.offset(2, 0, 0)).is(Blocks.COBBLESTONE), "Source lava becomes obsidian; flowing lava becomes cobblestone");
            fixtures.keySet().forEach(terrain::process);
            helper.assertTrue(level.getBlockState(origin.offset(2, 0, 0)).is(Blocks.COBBLESTONE), "A later pass of this attack preserves cooled cobblestone");
            helper.assertTrue(level.getBlockState(origin.offset(4, 0, 0)).is(Blocks.WATER)
                    && level.getBlockState(origin.offset(6, 0, 0)).is(Blocks.OBSIDIAN)
                    && level.getBlockState(origin.offset(8, 0, 0)).is(Blocks.CHEST), "Water, existing obsidian and containers stay intact");
            helper.assertTrue(level.getBlockState(origin.offset(10, 0, 0)).isAir(), "Ordinary stone still breaks");
            helper.assertTrue(level.getBlockState(protectedLava).is(Blocks.LAVA), "Cancelled protection events prevent cooling too");
            helper.assertTrue(level.getBlockState(replacedLava).is(Blocks.CHEST), "State replacement during the event is revalidated before conversion");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(protection);
            fixtures.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sphereCoversIntersectingCubesWithoutRepeatingOrEscaping(GameTestHelper helper) {
        Vec3 center = new Vec3(-0.3D, 70.8D, 5.1D);
        double radius = 3.2D;
        var scan = new WaterPurpleTerminalExplosion.SphereScan(center, radius, -64, 319);
        Set<BlockPos> actual = new HashSet<>();
        while (!scan.finished()) {
            BlockPos pos = scan.next();
            helper.assertTrue(actual.add(pos), "Each world-grid candidate is processed once");
            double dx = Math.max(pos.getX() - center.x, Math.max(0D, center.x - pos.getX() - 1D));
            double dy = Math.max(pos.getY() - center.y, Math.max(0D, center.y - pos.getY() - 1D));
            double dz = Math.max(pos.getZ() - center.z, Math.max(0D, center.z - pos.getZ() - 1D));
            helper.assertTrue(scan.intersects(pos) == (dx * dx + dy * dy + dz * dz <= radius * radius + 1E-9D),
                    "Sphere collision matches an independent cube-face distance calculation");
        }
        for (BlockPos pos : BlockPos.betweenClosed(-5, 66, 0, 4, 75, 10)) {
            if (scan.intersects(pos)) helper.assertTrue(actual.contains(pos), "No intersecting boundary cube is missed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void burstUsesBudgetAndHitsOnceBeforeMistFinishes(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO).atY(220));
        var player = player(helper, center);
        var target = EntityType.HUSK.create(level);
        helper.assertTrue(target != null, "Burst target exists");
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D); target.setHealth(1000F);
        target.setNoAi(true); target.setNoGravity(true); target.setPos(center.x + 3D, center.y, center.z); level.addFreshEntity(target);
        BlockPos stone = BlockPos.containing(center).offset(2, 2, 0);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
        var burst = new WaterPurpleTerminalExplosion(level, player, center, 60, new WaterPurpleTerrain(level, player), null, false);
        int flashHits = 0;
        try {
            boolean done = false;
            for (int tick = 0; tick < 200 && !done; tick++) {
                int before = burst.scan.cursor;
                float health = target.getHealth();
                done = burst.tick(7);
                helper.assertTrue(burst.scan.cursor - before <= 7, "The burst never exceeds its shared candidate budget");
                if (tick < 3) helper.assertTrue(target.getHealth() == 1000F, "Damage waits for the 0.2-second flash");
                if (target.getHealth() < health) flashHits++;
            }
            helper.assertTrue(done && burst.scan.finished() && burst.age == 60, "Terrain drains before the burst's mist finishes");
            helper.assertTrue(flashHits == 1 && target.getHealth() == 950F, "The terminal sphere hits once at scaled damage rather than every tick");
            helper.assertTrue(level.getBlockState(stone).isAir(), "A cube whose face intersects is removed even with its center outside the radius");
            burst.tick(7);
            helper.assertTrue(target.getHealth() == 950F, "Repeated completion calls never apply another damage wave");
        } finally { target.discard(); level.setBlock(stone, Blocks.AIR.defaultBlockState(), 2); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flightCoolingAndTerminalBurstShareProductsAndRunOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 endpoint = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO).atY(240));
        var player = player(helper, endpoint.add(0, 10, 0));
        BlockPos lava = BlockPos.containing(endpoint);
        level.setBlock(lava, Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 5), 2);
        var beam = new WaterCurseService.Beam(player, level, endpoint.add(0, 300, 0), new Vec3(0, -1, 0), 60);
        beam.age = beam.flightTicks - 1;
        try {
            helper.assertTrue(!beam.tick(32768) && beam.terminalExplosion != null && !beam.terminalExplosion.started,
                    "A normal endpoint drains path terrain and creates exactly one waiting burst");
            helper.assertTrue(level.getBlockState(lava).is(Blocks.COBBLESTONE), "Flight cools the lava actually touched at the endpoint");
            var first = beam.terminalExplosion;
            boolean done = false;
            for (int tick = 0; tick < 200 && !done; tick++) done = beam.tick(64);
            helper.assertTrue(done && beam.terminalExplosion == first && first.started && !first.aborted,
                    "The same burst completes once and is retained through terrain processing");
            helper.assertTrue(level.getBlockState(lava).is(Blocks.COBBLESTONE), "Terminal overlap does not erase the flowing lava's cooled product");
        } finally { beam.close(); level.setBlock(lava, Blocks.AIR.defaultBlockState(), 2); }
        var cancelled = new WaterCurseService.Beam(player, level, endpoint, new Vec3(0, 1, 0), 60);
        cancelled.close();
        helper.assertTrue(cancelled.tick(8192) && cancelled.terminalExplosion == null, "Abnormal cleanup never creates an endpoint burst");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void unavailableTerminalChunksAbortBeforeDamage(GameTestHelper helper) {
        var level = helper.getLevel();
        ChunkPos absent = new ChunkPos(400000 + (UUID.randomUUID().hashCode() & 4095), 400000);
        Vec3 center = new Vec3(absent.getMiddleBlockX(), 200, absent.getMiddleBlockZ());
        var player = player(helper, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO).atY(200)));
        var loader = new WaterPurpleChunkLoader(level, false);
        var burst = new WaterPurpleTerminalExplosion(level, player, center, 60, new WaterPurpleTerrain(level, player), loader, false);
        class Poll implements Runnable {
            int attempts;
            public void run() {
                try {
                    if (!burst.tick(64) && attempts++ < 1900) {
                        helper.assertTrue(!burst.started && !burst.damaged && burst.scan.cursor == 0,
                                "Unready endpoint chunks cannot start visuals, entity damage or block work");
                        helper.runAfterDelay(1, () -> run()); return;
                    }
                    helper.assertTrue(burst.aborted && !burst.started && !burst.damaged,
                            "Missing endpoint terrain aborts without a fallback explosion");
                    loader.close(); helper.assertTrue(loader.heldChunks() == 0, "Abort releases the endpoint reservations"); helper.succeed();
                } catch (RuntimeException error) { loader.close(); throw error; }
            }
        }
        helper.runAfterDelay(1, new Poll());
    }

    @GameTest(template = "empty")
    public static void burstPacketsPreserveTimelineProgressAndEnd(GameTestHelper helper) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            for (boolean finished : new boolean[]{false, true}) {
                var original = new WaterCurseNetwork.Burst(UUID.randomUUID(), helper.getLevel().dimension().location(),
                        new Vec3(23.5D, 240D, -18D), 64D, 30, 0.37D, finished);
                WaterCurseNetwork.Burst.encode(original, buffer);
                helper.assertTrue(original.equals(WaterCurseNetwork.Burst.decode(buffer)) && buffer.readableBytes() == 0,
                        "Terminal snapshots preserve identity, exact origin, progress and cancellation");
                buffer.clear();
            }
        } finally { buffer.release(); }
        helper.succeed();
    }
}
