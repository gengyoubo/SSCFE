package github.com.gengyoubo.sscfe.moon;

import github.com.gengyoubo.sscfe.init.ModMoonContent;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.registries.ForgeRegistries;
import net.onixary.shapeShifterCurseForge.registry.ModItems;

/** Resolves edible items after item registration, including other mods' normal foods. */
public final class EdibleMoondustRecipe extends ShapedRecipe {
    public EdibleMoondustRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, "", category, 3, 3, ingredients(), new ItemStack(ModMoonContent.EDIBLE_MOONDUST.get(), 5));
    }

    private static NonNullList<Ingredient> ingredients() {
        Ingredient food = Ingredient.of(ForgeRegistries.ITEMS.getValues().stream()
                .filter(item -> item.isEdible()).map(ItemStack::new));
        Ingredient dust = Ingredient.of(ModItems.UNTREATED_MOONDUST.get());
        return NonNullList.of(Ingredient.EMPTY, food, dust, food, dust, food, dust, food, dust, food);
    }

    @Override public RecipeSerializer<?> getSerializer() { return ModMoonContent.EDIBLE_MOONDUST_RECIPE.get(); }
}
