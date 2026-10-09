package github.com.gengyoubo.sscfe.water;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WaterFoodItem extends Item {
    public WaterFoodItem() { super(new Properties().stacksTo(1)); }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sscfe.water_as_food.tooltip").withStyle(ChatFormatting.YELLOW));
    }
}
