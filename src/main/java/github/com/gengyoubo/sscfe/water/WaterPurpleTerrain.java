package github.com.gengyoubo.sscfe.water;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

/** Shared by flight and terminal processing; cooled products survive this attack's later passes. */
final class WaterPurpleTerrain {
    private final ServerLevel level;
    private final ServerPlayer player;
    private final LongOpenHashSet cooled = new LongOpenHashSet();
    private long lastSteam = Long.MIN_VALUE;

    WaterPurpleTerrain(ServerLevel level, ServerPlayer player) { this.level = level; this.player = player; }

    void process(BlockPos pos) {
        if (!player.mayBuild() || !level.isInWorldBounds(pos) || !level.hasChunkAt(pos) || cooled.contains(pos.asLong())) return;
        var state = level.getBlockState(pos);
        var fluid = state.getFluidState();
        boolean cooling = WaterCurseConfig.COOL_LAVA.get() && state.getBlock() instanceof LiquidBlock
                && fluid.is(FluidTags.LAVA) && !state.hasBlockEntity()
                && !state.is(BlockTags.DRAGON_IMMUNE) && !state.is(BlockTags.WITHER_IMMUNE);
        if ((!cooling && !WaterCurseService.breakable(state, level, pos)) || !level.mayInteract(player, pos)
                || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player))) return;
        if (level.getBlockState(pos) != state || level.getBlockEntity(pos) != null) return;
        if (cooling) {
            var result = fluid.isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            if (level.setBlock(pos, result, 3)) {
                cooled.add(pos.asLong());
                long now = level.getGameTime();
                // One steam burst per attack per five ticks, even when an entire lava lake is scanned.
                if (lastSteam == Long.MIN_VALUE || now - lastSteam >= 5) {
                    lastSteam = now;
                    level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D,
                            10, 0.5D, 0.3D, 0.5D, 0.04D);
                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.2F);
                }
            }
            return;
        }
        if (WaterCurseService.ore(state) && level.random.nextFloat() < 0.1F)
            Block.dropResources(state, level, pos, null, player, player.getMainHandItem());
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }
}
