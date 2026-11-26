package github.com.gengyoubo.sscfe.client;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.interaction.AxolotlChainPhysics;
import github.com.gengyoubo.sscfe.interaction.AxolotlStackService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Block dust kicked up under chained axolotls while the chain is dragged along the ground. */
@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, value = Dist.CLIENT)
public final class AxolotlChainParticles {
    /** Horizontal movement per tick below which nobody is being dragged. */
    private static final double MIN_SPEED = 0.02D;
    private static final double RANGE = 48.0D;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.player == null || mc.isPaused()) return;
        for (Player player : mc.level.players()) {
            if (player.distanceToSqr(mc.player) > RANGE * RANGE || player.isInWater()
                    || !AxolotlStackService.isChained(player)) continue;
            double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
            double speed = Math.sqrt(dx * dx + dz * dz);
            if (speed < MIN_SPEED) continue;
            // Followers are dragged on their belly; the leader only crawls, so it raises less dust.
            boolean dragged = AxolotlStackService.isChainLink(player.getVehicle(), player);
            int count = (dragged ? 2 : 1) + (speed > 0.12D ? 1 : 0);
            spawn(player, dx, dz, count);
        }
    }

    private static void spawn(Player player, double dx, double dz, int count) {
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2D, player.getZ());
        BlockState state = player.level().getBlockState(below);
        if (state.getRenderShape() == RenderShape.INVISIBLE) return;
        RandomSource random = player.getRandom();
        double scale = AxolotlChainPhysics.scale(player);
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5D) * 0.6D * scale;
            double z = player.getZ() + (random.nextDouble() - 0.5D) * 0.6D * scale;
            player.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state).setPos(below),
                    x, player.getY() + 0.1D, z, -dx * 4.0D, 1.5D, -dz * 4.0D);
        }
    }

    private AxolotlChainParticles() {}
}
