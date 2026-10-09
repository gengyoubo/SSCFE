package github.com.gengyoubo.sscfe.water;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LargeWaterTankItem extends BlockItem {
    public LargeWaterTankItem(Block block) {
        super(block, new Properties().stacksTo(1));
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new WaterItemStorage(stack, WaterItemStorage.LARGE_TANK_CAPACITY);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return WaterItemStorage.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        WaterItemStorage.tooltip(stack, WaterItemStorage.LARGE_TANK_CAPACITY, tooltip);
        tooltip.add(Component.translatable("item.sscfe.large_water_tank.tooltip").withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return WaterItemStorage.amount(stack) > 0; }

    @Override
    public int getBarWidth(ItemStack stack) { return WaterItemStorage.barWidth(stack, WaterItemStorage.LARGE_TANK_CAPACITY); }

    @Override
    public int getBarColor(ItemStack stack) { return 0x3498DB; }
}
