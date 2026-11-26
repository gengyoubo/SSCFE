package github.com.gengyoubo.sscfe.moon;

import github.com.gengyoubo.sscfe.init.ModMoonContent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseForge.registry.ModItems;

public final class FloatingMoondustRecipe extends CustomRecipe {
    public FloatingMoondustRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private int resultCount(CraftingContainer grid) {
        int dust = 0;
        int minutes = 0;
        boolean foundPotion = false;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(ModItems.UNTREATED_MOONDUST.get())) {
                dust++;
            } else if (stack.is(Items.POTION) && !foundPotion) {
                foundPotion = true;
                minutes = PotionUtils.getMobEffects(stack).stream()
                        .filter(effect -> effect.getEffect() == MobEffects.LEVITATION)
                        .mapToInt(effect -> effect.getDuration() / 1200)
                        .max().orElse(0);
            } else {
                return 0;
            }
        }
        return foundPotion && minutes > 0 && dust == minutes ? minutes : 0;
    }

    @Override public boolean matches(CraftingContainer grid, Level level) { return resultCount(grid) > 0; }

    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess registries) {
        int count = resultCount(grid);
        return count == 0 ? ItemStack.EMPTY : new ItemStack(ModMoonContent.FLOATING_MOONDUST.get(), count);
    }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(grid);
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (grid.getItem(i).is(Items.POTION)) remaining.set(i, new ItemStack(Items.GLASS_BOTTLE));
        }
        return remaining;
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return ModMoonContent.FLOATING_MOONDUST_RECIPE.get(); }
}
