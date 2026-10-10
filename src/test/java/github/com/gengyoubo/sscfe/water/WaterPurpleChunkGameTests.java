package github.com.gengyoubo.sscfe.water;

import com.mojang.authlib.GameProfile;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.init.ModWaterContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

@GameTestHolder(Sscfe.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterPurpleChunkGameTests {
    @GameTest(template = "empty")
    public static void ticketsAreSharedAndCriticalReservationsAreAtomic(GameTestHelper helper) {
        var level = helper.getLevel();
        ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        int baseline = WaterPurpleChunkLoader.totalHeldChunks();
        int perBeam = WaterCurseConfig.MAX_CHUNKS_PER_BEAM.get(), total = WaterCurseConfig.MAX_TOTAL_CHUNKS.get();
        try (var first = new WaterPurpleChunkLoader(level); var second = new WaterPurpleChunkLoader(level)) {
            WaterCurseConfig.MAX_CHUNKS_PER_BEAM.set(4); WaterCurseConfig.MAX_TOTAL_CHUNKS.set(baseline + 4);
            Set<ChunkPos> three = Set.of(origin, new ChunkPos(origin.x + 1, origin.z), new ChunkPos(origin.x + 2, origin.z));
            first.ensure(three);
            helper.assertTrue(first.heldChunks() == 3 && WaterPurpleChunkLoader.totalHeldChunks() == baseline + 3,
                    "Critical footprints reserve all needed chunks as one operation");
            second.ensure(Set.of(new ChunkPos(origin.x + 3, origin.z), new ChunkPos(origin.x + 4, origin.z)));
            helper.assertTrue(second.heldChunks() == 0 && WaterPurpleChunkLoader.totalHeldChunks() == baseline + 3,
                    "Global limits reject an entire footprint without partial reservations");
            second.ensure(Set.of(origin, new ChunkPos(origin.x + 3, origin.z)));
            helper.assertTrue(second.heldChunks() == 2 && WaterPurpleChunkLoader.totalHeldChunks() == baseline + 4,
                    "Two beams sharing a chunk consume only one global chunk slot");
            first.close();
            helper.assertTrue(second.heldChunks() == 2 && WaterPurpleChunkLoader.totalHeldChunks() == baseline + 2,
                    "Closing the first beam leaves the second beam's ticket ownership intact");
            second.close();
            helper.assertTrue(WaterPurpleChunkLoader.totalHeldChunks() == baseline, "Repeated close releases all reservations exactly once");
        } finally {
            WaterCurseConfig.MAX_CHUNKS_PER_BEAM.set(perBeam); WaterCurseConfig.MAX_TOTAL_CHUNKS.set(total);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void windowIsBoundedAndPendingTerrainKeepsRearChunks(GameTestHelper helper) {
        Vec3 origin = new Vec3(0.5D, 80, 0.5D);
        for (Vec3 direction : new Vec3[]{new Vec3(1, 0, 0), new Vec3(1, 0, 1).normalize(), new Vec3(-1, 0, -1).normalize()}) {
            Set<ChunkPos> window = WaterPurpleChunkLoader.footprint(origin.add(direction.scale(-32D)), origin.add(direction.scale(48D)), 25D);
            helper.assertTrue(window.size() <= 64, "A default moving window fits the per-beam limit even diagonally");
            helper.assertTrue(window.contains(new ChunkPos(BlockPos.containing(origin.add(direction.scale(48D)))))
                    && window.contains(new ChunkPos(BlockPos.containing(origin.add(direction.scale(-32D))))),
                    "The window includes the three-chunk lead and two-chunk rear");
        }
        var scan = new WaterPurpleBlockScan(origin, new Vec3(1, 0, 0), 80D, 1D, -64, 319);
        BlockPos pending = new BlockPos(8, 80, 0);
        scan.defer(pending, 8D);
        while (scan.hasCandidate(80D)) scan.nextCandidate();
        helper.assertTrue(scan.needsChunk(new ChunkPos(pending)), "Pending terrain holds its rear chunk after the cursor has passed it");
        scan.pollReached(80D);
        helper.assertTrue(!scan.needsChunk(new ChunkPos(pending)) && scan.finished(), "Completing the pending block releases rear retention");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void absentChunkDoesNotGenerateOrKeepTickets(GameTestHelper helper) {
        var level = helper.getLevel();
        ChunkPos absent = new ChunkPos(200000 + (UUID.randomUUID().hashCode() & 4095), 200000);
        var loader = new WaterPurpleChunkLoader(level, false);
        class Poll implements Runnable {
            int attempts;
            public void run() {
                try {
                    var state = loader.ensure(Set.of(absent));
                    if (state == WaterPurpleChunkLoader.State.WAIT && attempts++ < 1900) { helper.runAfterDelay(1, () -> run()); return; }
                    helper.assertTrue(state == WaterPurpleChunkLoader.State.MISSING, "An async disk probe identifies an ungenerated chunk");
                    helper.assertTrue(level.getChunkSource().getChunkNow(absent.x, absent.z) == null && loader.heldChunks() == 0,
                            "No loading ticket or generation is requested for the absent chunk");
                    loader.close(); helper.succeed();
                } catch (RuntimeException error) { loader.close(); throw error; }
            }
        }
        helper.runAfterDelay(1, new Poll());
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void savedFullChunkLoadsAsynchronously(GameTestHelper helper) {
        var level = helper.getLevel();
        ChunkPos saved = new ChunkPos(100000 + (UUID.randomUUID().hashCode() & 4095), 100000);
        LevelChunk fixture = new LevelChunk(level, saved);
        BlockPos stone = saved.getBlockAt(8, 80, 8);
        fixture.setBlockState(stone, Blocks.STONE.defaultBlockState(), false);
        level.getChunkSource().chunkMap.write(saved, ChunkSerializer.write(level, fixture));
        helper.assertTrue(level.getChunkSource().getChunkNow(saved.x, saved.z) == null, "The saved fixture starts unloaded");
        var loader = new WaterPurpleChunkLoader(level, false);
        class Poll implements Runnable {
            int attempts;
            public void run() {
                try {
                    var state = loader.ensure(Set.of(saved));
                    if (state == WaterPurpleChunkLoader.State.WAIT && attempts++ < 1900) { helper.runAfterDelay(1, () -> run()); return; }
                    helper.assertTrue(state == WaterPurpleChunkLoader.State.READY, "An existing full chunk and its entities become ready without a blocking getChunk");
                    helper.assertTrue(level.getChunkSource().getChunkNow(saved.x, saved.z).getBlockState(stone).is(Blocks.STONE),
                            "Loaded terrain preserves saved stone");
                    loader.close(); helper.assertTrue(loader.heldChunks() == 0, "Completion releases the async chunk's ticket"); helper.succeed();
                } catch (RuntimeException error) { loader.close(); throw error; }
            }
        }
        helper.runAfterDelay(1, new Poll());
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void ungeneratedChunkBecomesFullAndReleasesTickets(GameTestHelper helper) {
        var level = helper.getLevel();
        ChunkPos fresh = new ChunkPos(300000 + (UUID.randomUUID().hashCode() & 4095), 300000);
        var probe = level.getChunkSource().chunkMap.read(fresh);
        var loader = new WaterPurpleChunkLoader(level, true);
        class Poll implements Runnable {
            int attempts;
            boolean confirmedAbsent;
            public void run() {
                try {
                    if (!confirmedAbsent) {
                        if (!probe.isDone()) {
                            helper.assertTrue(attempts++ < 1900, "The fresh chunk probe finishes asynchronously");
                            helper.runAfterDelay(1, () -> run()); return;
                        }
                        helper.assertTrue(probe.getNow(java.util.Optional.empty()).isEmpty()
                                && level.getChunkSource().getChunkNow(fresh.x, fresh.z) == null,
                                "The target has neither saved terrain nor a loaded chunk");
                        confirmedAbsent = true;
                    }
                    var state = loader.ensure(Set.of(fresh));
                    if (state == WaterPurpleChunkLoader.State.WAIT && attempts++ < 1900) {
                        helper.runAfterDelay(1, () -> run()); return;
                    }
                    helper.assertTrue(state == WaterPurpleChunkLoader.State.READY,
                            "An ungenerated chunk reaches FULL and entity readiness through async region tickets");
                    var generated = level.getChunkSource().getChunkNow(fresh.x, fresh.z);
                    helper.assertTrue(generated != null && generated.getStatus() == net.minecraft.world.level.chunk.ChunkStatus.FULL,
                            "Terrain generation completes before the projectile is allowed to advance");
                    helper.assertTrue(loader.heldChunks() == 1, "Generation respects the requested chunk reservation");
                    loader.close();
                    helper.assertTrue(loader.heldChunks() == 0, "Closing after generation releases the ticket and reservation");
                    helper.succeed();
                } catch (RuntimeException error) { loader.close(); throw error; }
            }
        }
        helper.runAfterDelay(1, new Poll());
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void realBeamPausesAtLimitResumesAndCleansOnLogout(GameTestHelper helper) {
        ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "chunk-purple-test")) {
            @Override public boolean isCreative() { return true; }
        };
        var charm = new ItemStack(ModWaterContent.WATER_CURSE.get());
        charm.getOrCreateTag().putInt("WaterCurseMode", WaterCurseItem.Mode.PURPLE.ordinal());
        player.setItemInHand(InteractionHand.MAIN_HAND, charm);
        ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        player.setPos(chunk.getMiddleBlockX(), 200, chunk.getMiddleBlockZ()); player.setXRot(-90F);
        WaterCurseService.input(player, WaterCurseNetwork.Action.START, 60);
        helper.runAfterDelay(64, () -> {
            WaterCurseService.Beam beam = findBeam(player);
            helper.assertTrue(beam != null && beam.chunkLoader != null && beam.chunkLoader.heldChunks() > 0,
                    "A real released beam owns a temporary loading window");
            int before = beam.age, oldLimit = WaterCurseConfig.MAX_CHUNKS_PER_BEAM.get();
            WaterCurseConfig.MAX_CHUNKS_PER_BEAM.set(4);
            helper.runAfterDelay(2, () -> {
                try { helper.assertTrue(beam.age == before && !beam.moving, "Ticket capacity pauses server flight rather than crossing unloaded terrain"); }
                catch (RuntimeException error) { WaterCurseService.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player)); throw error; }
                finally { WaterCurseConfig.MAX_CHUNKS_PER_BEAM.set(oldLimit); }
                helper.runAfterDelay(2, () -> {
                    try { helper.assertTrue(beam.age > before, "Flight resumes when chunk capacity is available"); }
                    finally { WaterCurseService.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player)); }
                    helper.assertTrue(beam.closed && beam.chunkLoader.heldChunks() == 0, "Logout releases every ticket owned by the projectile");
                    helper.succeed();
                });
            });
        });
    }

    private static WaterCurseService.Beam findBeam(ServerPlayer player) {
        try {
            var field = WaterCurseService.class.getDeclaredField("BEAMS"); field.setAccessible(true);
            for (Object value : (java.util.List<?>) field.get(null)) {
                if (value instanceof WaterCurseService.Beam beam && beam.player == player) return beam;
            }
            return null;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
