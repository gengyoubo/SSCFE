package github.com.gengyoubo.sscfe.moon;

import github.com.gengyoubo.sscfe.init.ModMoonContent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class MoonAdaptationEnchantment extends Enchantment {
    public MoonAdaptationEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR,
                new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET});
    }

    @Override public boolean canEnchant(ItemStack stack) {
        return stack.isDamageableItem() && stack.getItem() instanceof Equipable;
    }

    @Override public boolean isTreasureOnly() { return true; }
    @Override public boolean isTradeable() { return false; }
    @Override public boolean isDiscoverable() { return false; }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack) { return false; }

    public static boolean isAdapted(ItemStack stack) {
        return !stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(ModMoonContent.MOON_ADAPTATION.get(), stack) > 0;
    }
}
