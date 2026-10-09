package github.com.gengyoubo.sscfe.water;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Settings live on the accessory, and survive unequipping and save/load. */
public final class WaterCurseItem extends Item {
    public enum Mode { FIST, WEAPON, SHIELD, PURPLE }

    public WaterCurseItem() { super(new Properties().stacksTo(1)); }

    public static Mode mode(ItemStack stack) {
        int value = stack.getOrCreateTag().getInt("WaterCurseMode");
        return Mode.values()[Math.floorMod(value, Mode.values().length)];
    }

    public static boolean enabled(ItemStack stack) { return enabled(stack, mode(stack)); }
    public static boolean enabled(ItemStack stack, Mode mode) {
        return (stack.getOrCreateTag().getInt("WaterCurseEnabledModes") & (1 << mode.ordinal())) != 0;
    }
    public static boolean special(ItemStack stack) { return stack.getOrCreateTag().getBoolean("WaterPurpleSpecial"); }

    public static void configure(Player player, ItemStack stack) {
        if (!WaterCurseService.canUse(player)) return;
        WaterCurseService.cancel(player);
        if (player.isShiftKeyDown()) {
            stack.getOrCreateTag().putInt("WaterCurseMode", (mode(stack).ordinal() + 1) % Mode.values().length);
        } else if (mode(stack) == Mode.PURPLE) {
            stack.getOrCreateTag().putBoolean("WaterPurpleSpecial", !special(stack));
        } else {
            stack.getOrCreateTag().putInt("WaterCurseEnabledModes",
                    stack.getOrCreateTag().getInt("WaterCurseEnabledModes") ^ (1 << mode(stack).ordinal()));
        }
        player.displayClientMessage(status(stack), true);
    }

    public static Component status(ItemStack stack) {
        return Component.translatable("message.sscfe.water_curse.mode",
                Component.translatable("mode.sscfe.water_curse." + mode(stack).name().toLowerCase(java.util.Locale.ROOT)),
                Component.translatable(mode(stack) == Mode.PURPLE
                        ? (special(stack) ? "state.sscfe.special" : "state.sscfe.normal")
                        : (enabled(stack) ? "state.sscfe.enabled" : "state.sscfe.disabled")));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WaterCurseService.canUse(player)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) configure(player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(status(stack));
        lines.add(Component.translatable("item.sscfe.water_curse.controls"));
        lines.add(Component.translatable("item.sscfe.water_curse.costs"));
        lines.add(Component.translatable("item.sscfe.water_curse.creative"));
    }
}
