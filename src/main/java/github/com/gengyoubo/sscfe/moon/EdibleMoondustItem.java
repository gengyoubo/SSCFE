package github.com.gengyoubo.sscfe.moon;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Fixed nutrition also bypasses older core versions' form food modifiers. */
public final class EdibleMoondustItem extends Item {
    public EdibleMoondustItem() {
        super(new Properties().food(new FoodProperties.Builder().nutrition(3).saturationMod(0.5F).build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity eater) {
        if (!(eater instanceof Player player)) return super.finishUsingItem(stack, level, eater);
        player.getFoodData().eat(3, 0.5F);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }
}
