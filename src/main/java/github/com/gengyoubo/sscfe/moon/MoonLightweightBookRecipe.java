package github.com.gengyoubo.sscfe.moon;

import github.com.gengyoubo.sscfe.init.ModMoonContent;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public final class MoonLightweightBookRecipe extends ShapelessRecipe {
    public MoonLightweightBookRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, "", category, ModMoonContent.lightweightBook(), ingredients());
    }
    private static NonNullList<Ingredient> ingredients() {
        NonNullList<Ingredient> result = NonNullList.create();
        result.add(Ingredient.of(Items.BOOK));
        result.add(Ingredient.of(ModMoonContent.LIGHTWEIGHT_CORE.get()));
        for (int i = 0; i < 7; i++) result.add(Ingredient.of(ModMoonContent.FLOATING_MOONDUST.get()));
        return result;
    }
    @Override public RecipeSerializer<?> getSerializer() { return ModMoonContent.MOON_LIGHTWEIGHT_BOOK.get(); }
}
