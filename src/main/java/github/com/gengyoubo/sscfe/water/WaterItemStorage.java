package github.com.gengyoubo.sscfe.water;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;

import java.util.List;

/** Water is stored in Forge's standard item Fluid NBT and accessed through its capability. */
public final class WaterItemStorage extends FluidHandlerItemStack {
    public static final int MOISTURIZER_CAPACITY = 3_000;
    public static final int LARGE_TANK_CAPACITY = 32_000;

    public WaterItemStorage(ItemStack container, int capacity) {
        super(container, capacity);
    }

    @Override
    public boolean canFillFluidType(FluidStack fluid) {
        return fluid.getFluid() == Fluids.WATER;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack fluid) {
        return canFillFluidType(fluid);
    }

    public static IFluidHandlerItem handler(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
    }

    public static int amount(ItemStack stack) {
        IFluidHandlerItem handler = handler(stack);
        if (handler == null || handler.getFluidInTank(0).getFluid() != Fluids.WATER) return 0;
        return handler.getFluidInTank(0).getAmount();
    }

    public static int fill(ItemStack stack, int amount, IFluidHandler.FluidAction action) {
        IFluidHandlerItem handler = handler(stack);
        return handler == null ? 0 : handler.fill(new FluidStack(Fluids.WATER, amount), action);
    }

    public static int drain(ItemStack stack, int amount, IFluidHandler.FluidAction action) {
        IFluidHandlerItem handler = handler(stack);
        return handler == null ? 0 : handler.drain(new FluidStack(Fluids.WATER, amount), action).getAmount();
    }

    public static void tooltip(ItemStack stack, int capacity, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.sscfe.water_storage", amount(stack), capacity)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.sscfe.water_storage.fill").withStyle(ChatFormatting.GRAY));
    }

    public static int barWidth(ItemStack stack, int capacity) {
        return Math.round(13.0F * amount(stack) / capacity);
    }

    /** Hold a fluid container in the other hand to fill or empty this item. */
    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        IFluidHandlerItem handler = handler(stack);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (handler == null || !FluidUtil.getFluidHandler(player.getItemInHand(otherHand)).isPresent()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        return FluidUtil.interactWithFluidHandler(player, otherHand, handler)
                ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
    }
}
