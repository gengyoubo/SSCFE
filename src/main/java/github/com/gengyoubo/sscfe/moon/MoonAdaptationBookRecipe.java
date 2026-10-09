package github.com.gengyoubo.sscfe.moon;

import github.com.gengyoubo.sscfe.init.ModMoonContent;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.onixary.shapeShifterCurseForge.registry.ModItems;

/** A standard shapeless recipe with an enchanted, rather than plain, book result. */
public final class MoonAdaptationBookRecipe extends ShapelessRecipe {
    public MoonAdaptationBookRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, "", category, ModMoonContent.adaptationBook(), ingredients());
    }

    private static NonNullList<Ingredient> ingredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(Items.BOOK));
        ingredients.add(Ingredient.of(ModItems.MORPHSCALE_CORE.get()));
        for (int i = 0; i < 7; i++) ingredients.add(Ingredient.of(ModItems.UNTREATED_MOONDUST.get()));
        return ingredients;
    }

    @Override public RecipeSerializer<?> getSerializer() { return ModMoonContent.MOON_ADAPTATION_BOOK.get(); }
}
