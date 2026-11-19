package github.com.gengyoubo.sscfe.interaction;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Worn in the Curios back slot by a spider final form so other players can ride it. */
public final class SpiderSaddleItem extends Item {
    public SpiderSaddleItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sscfe.spider_saddle.tooltip").withStyle(ChatFormatting.YELLOW));
    }
}
