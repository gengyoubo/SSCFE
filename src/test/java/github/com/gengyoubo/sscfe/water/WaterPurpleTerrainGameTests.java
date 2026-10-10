package github.com.gengyoubo.sscfe.water;

import com.mojang.authlib.GameProfile;
import github.com.gengyoubo.sscfe.Sscfe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Sscfe.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterPurpleTerrainGameTests {
    @GameTest(template = "empty")
    public static void cubeIntersectionIncludesEdgesAndCaps(GameTestHelper helper) {
        Vec3 origin = new Vec3(0, 0.5D, 0.5D), direction = new Vec3(1, 0, 0);
        helper.assertTrue(Math.abs(WaterPurpleRules.blockEntryDistance(new BlockPos(5, 0, 0), origin, direction, 10, 1) - 4D) < 1E-8D,
                "The front touches a full block cube before reaching its center");
        helper.assertTrue(!Double.isFinite(WaterPurpleRules.blockEntryDistance(new BlockPos(5, 0, 0), origin, direction, 3, 1)),
                "The finite end cap cannot reach a distant block");
        helper.assertTrue(WaterPurpleRules.blockEntryDistance(new BlockPos(-2, 0, 0), origin, direction, 10, 1) == 0D,
                "The initial sphere includes its back cap");
        helper.assertTrue(WaterPurpleRules.blockEntryDistance(new BlockPos(5, 1, 0), Vec3.ZERO, direction, 10, 1) == 5D,
                "A tangent cube intersects even when its center is outside the radius");
        helper.assertTrue(!Double.isFinite(WaterPurpleRules.blockEntryDistance(new BlockPos(5, 1, 1), Vec3.ZERO, direction, 10, 1)),
                "A cube outside the spherical corner stays intact");
        helper.assertTrue(WaterPurpleRules.blockEntryDistance(new BlockPos(-6, 1, 0), Vec3.ZERO, new Vec3(-1, 0, 0), 10, 1) == 5D,
                "Negative direction treats cube faces symmetrically");
        Vec3 diagonal = new Vec3(1, 1, 1).normalize();
        helper.assertTrue(Math.abs(WaterPurpleRules.blockEntryDistance(new BlockPos(7, 7, 7), Vec3.ZERO, diagonal, 20, 0.3D)
                - (7D * Math.sqrt(3D) - 0.3D)) < 1E-7D, "A three-dimensional diagonal hits the box corner at the correct distance");
        helper.assertTrue(Math.abs(WaterPurpleRules.blockEntryDistance(new BlockPos(-6, -21, -31),
                new Vec3(-10.25D, -20.5D, -30.75D), direction, 10, 0.25D) - 4D) < 1E-8D,
                "Negative world coordinates preserve the fractional cube boundary");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldSlabsCoverFullVolumeOnce(GameTestHelper helper) {
        Vec3 origin = new Vec3(-11.3D, 48.8D, -7.6D);
        for (Vec3 direction : new Vec3[]{new Vec3(1, 0, 0), new Vec3(1, 0, 1).normalize(),
                new Vec3(-1, 0.6D, 0.8D).normalize(), new Vec3(0, -1, 0)}) {
            int radius = 8;
            double range = 24D;
            var scan = new WaterPurpleBlockScan(origin, direction, range, radius, -64, 319);
            var candidates = new HashSet<BlockPos>();
            var hits = new HashSet<BlockPos>();
            // Drive the same persistent cursor one candidate at a time, including slice boundaries.
            while (scan.hasCandidate(range)) {
                BlockPos pos = scan.nextCandidate();
                if (pos == null) continue;
                helper.assertTrue(candidates.add(pos), "World-grid candidates cannot repeat across slabs");
                if (Double.isFinite(WaterPurpleRules.blockEntryDistance(pos, origin, direction, range, radius))) hits.add(pos);
            }
            Vec3 end = origin.add(direction.scale(range));
            BlockPos min = BlockPos.containing(Math.min(origin.x, end.x) - radius - 1E-7D,
                    Math.min(origin.y, end.y) - radius - 1E-7D, Math.min(origin.z, end.z) - radius - 1E-7D);
            BlockPos max = BlockPos.containing(Math.max(origin.x, end.x) + radius,
                    Math.max(origin.y, end.y) + radius, Math.max(origin.z, end.z) + radius);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                double distance = oracleBoxDistanceSquared(pos, origin, direction, range);
                if (distance < radius * radius - 1E-7D) helper.assertTrue(hits.contains(pos),
                        "No intersecting cube may be missed, including oblique and negative directions: " + pos);
                if (distance > radius * radius + 1E-7D) helper.assertTrue(!hits.contains(pos),
                        "No cube outside the radius may be selected: " + pos);
            }
            helper.assertTrue(scan.finished(), "The bounded scan completes after its last slab");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void axisAlignedStoneWallHasNoHoles(GameTestHelper helper) {
        stoneWall(helper, new Vec3(1, 0, 0), 120, 8192);
    }

    @GameTest(template = "empty")
    public static void diagonalStoneWallResumesWithSmallBudget(GameTestHelper helper) {
        stoneWall(helper, new Vec3(1, 0, 1).normalize(), 160, 256);
    }

    private static void stoneWall(GameTestHelper helper, Vec3 direction, int altitude, int budget) {
        var level = helper.getLevel();
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO).offset(64, 0, 64).atY(altitude)).add(0.17D, 0.13D, -0.21D);
        ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(level,
                new GameProfile(UUID.randomUUID(), "terrain-purple-test"));
        player.setPos(origin.x, origin.y, origin.z);
        var wall = new HashSet<BlockPos>();
        for (int x = -16; x <= 40; x++) for (int y = -11; y <= 11; y++) for (int z = -16; z <= 40; z++) {
            BlockPos pos = BlockPos.containing(origin).offset(x, y, z);
            Vec3 delta = Vec3.atCenterOf(pos).subtract(origin);
            double along = delta.dot(direction), side = -delta.x * direction.z + delta.z * direction.x;
            if (along < 22D || along > 32D || Math.abs(side) > 11D) continue;
            wall.add(pos); level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        BlockPos obsidian = BlockPos.containing(origin.add(direction.scale(24D)));
        BlockPos cancelled = BlockPos.containing(origin.add(direction.scale(28D)));
        BlockPos chest = BlockPos.containing(origin.add(direction.scale(30D)));
        level.setBlock(obsidian, Blocks.OBSIDIAN.defaultBlockState(), 2);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
        int[] cancelCount = {0};
        Consumer<BlockEvent.BreakEvent> listener = event -> {
            if (event.getLevel() == level && event.getPos().equals(cancelled)) {
                cancelCount[0]++; event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            var beam = new WaterCurseService.Beam(player, level, origin, direction, 600);
            helper.assertTrue(!beam.tick(budget), "A stone wall does not end the projectile");
            for (BlockPos pos : wall) helper.assertTrue(!level.getBlockState(pos).isAir(), "The front cannot clear an unreached wall");
            boolean finished = false;
            for (int tick = 2; tick <= 16000 && !finished; tick++) finished = beam.tick(budget);
            helper.assertTrue(finished, "Pending terrain work drains after flight even with a small budget");
            int cleared = 0, preserved = 0;
            for (BlockPos pos : wall) {
                if (pos.equals(obsidian) || pos.equals(cancelled) || pos.equals(chest)) continue;
                double distance = oracleBoxDistanceSquared(pos, origin, direction, beam.range);
                if (distance < 64D - 1E-7D) {
                    helper.assertTrue(level.getBlockState(pos).isAir(), "Intersecting stone must be cleared without holes: " + pos);
                    cleared++;
                } else if (distance > 64D + 1E-7D) {
                    helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "Stone outside the capsule stays intact");
                    preserved++;
                }
            }
            helper.assertTrue(cleared > 100 && preserved > 100, "The wall exercises both interior and exterior cubes");
            helper.assertTrue(level.getBlockState(obsidian).is(Blocks.OBSIDIAN)
                    && level.getBlockState(chest).is(Blocks.CHEST) && level.getBlockState(cancelled).is(Blocks.STONE),
                    "Hardness, containers and cancelled break events remain protected");
            helper.assertTrue(cancelCount[0] == 1, "Even a cancelled block is checked exactly once");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            wall.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2));
        }
        helper.succeed();
    }

    /** Independent convex minimization oracle, rather than reusing the production intersection solver. */
    private static double oracleBoxDistanceSquared(BlockPos block, Vec3 origin, Vec3 direction, double range) {
        double low = 0D, high = range;
        for (int i = 0; i < 70; i++) {
            double a = low + (high - low) / 3D, b = high - (high - low) / 3D;
            if (pointBoxDistanceSquared(block, origin, direction, a) <= pointBoxDistanceSquared(block, origin, direction, b)) high = b;
            else low = a;
        }
        return Math.min(pointBoxDistanceSquared(block, origin, direction, 0D),
                Math.min(pointBoxDistanceSquared(block, origin, direction, range), pointBoxDistanceSquared(block, origin, direction, (low + high) * 0.5D)));
    }

    private static double pointBoxDistanceSquared(BlockPos block, Vec3 origin, Vec3 direction, double t) {
        double x = origin.x + direction.x * t - block.getX();
        double y = origin.y + direction.y * t - block.getY();
        double z = origin.z + direction.z * t - block.getZ();
        double dx = x < 0 ? x : Math.max(0D, x - 1D);
        double dy = y < 0 ? y : Math.max(0D, y - 1D);
        double dz = z < 0 ? z : Math.max(0D, z - 1D);
        return dx * dx + dy * dy + dz * dz;
    }
}
