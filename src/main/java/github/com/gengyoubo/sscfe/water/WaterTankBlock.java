package github.com.gengyoubo.sscfe.water;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

public final class WaterTankBlock extends BaseEntityBlock {
    public WaterTankBlock(Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WaterTankBlockEntity(pos, state); }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity owner, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof WaterTankBlockEntity tank) {
            var handler = WaterItemStorage.handler(stack);
            if (handler != null) tank.tank().fill(handler.getFluidInTank(0).copy(), FluidAction.EXECUTE);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WaterTankBlockEntity tank)) return InteractionResult.PASS;
        if (!level.isClientSide && !FluidUtil.interactWithFluidHandler(player, hand, tank.tank())) {
            player.displayClientMessage(Component.translatable("tooltip.sscfe.water_storage",
                    tank.tank().getFluidAmount(), tank.tank().getCapacity()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
