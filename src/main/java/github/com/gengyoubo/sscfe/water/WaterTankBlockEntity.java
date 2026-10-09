package github.com.gengyoubo.sscfe.water;

import github.com.gengyoubo.sscfe.init.ModWaterContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public final class WaterTankBlockEntity extends BlockEntity {
    private final FluidTank tank = new FluidTank(WaterItemStorage.LARGE_TANK_CAPACITY,
            fluid -> fluid.getFluid() == Fluids.WATER) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };
    private LazyOptional<IFluidHandler> capability = LazyOptional.of(() -> tank);

    public WaterTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModWaterContent.WATER_TANK_ENTITY.get(), pos, state);
    }

    public FluidTank tank() { return tank; }

    /** The client receives the same tank contents through the update packet and chunk update tag. */
    public float fillFraction() {
        return Math.max(0.0F, Math.min(1.0F, (float) tank.getFluidAmount() / tank.getCapacity()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag.getCompound("Tank"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Tank", tank.writeToNBT(new CompoundTag()));
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) return capability.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        capability = LazyOptional.of(() -> tank);
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
