package github.com.gengyoubo.sscfe.moon;

import github.com.gengyoubo.sscfe.init.ModMoonContent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class MoonLightweightEnchantment extends Enchantment {
    public MoonLightweightEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.BREAKABLE, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    @Override public boolean canEnchant(ItemStack stack) { return stack.isDamageableItem() && stack.getItem() instanceof TieredItem; }
    @Override public boolean isTreasureOnly() { return true; }
    @Override public boolean isTradeable() { return false; }
    @Override public boolean isDiscoverable() { return false; }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack) { return false; }
    public static boolean isLightweight(ItemStack stack) {
        return !stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(ModMoonContent.MOON_LIGHTWEIGHT.get(), stack) > 0;
    }
}
