package github.com.gengyoubo.sscfe.moon;

import com.google.gson.JsonObject;
import github.com.gengyoubo.sscfe.init.ModMoonContent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseForge.items.MorphScaleItems;

public final class LightweightUpgradeRecipe implements SmithingRecipe {
    private final ResourceLocation id;
    private final Ingredient template, addition;
    public LightweightUpgradeRecipe(ResourceLocation id, Ingredient template, Ingredient addition) {
        this.id = id; this.template = template; this.addition = addition;
    }
    @Override public boolean isTemplateIngredient(ItemStack stack) { return template.test(stack); }
    @Override public boolean isAdditionIngredient(ItemStack stack) { return addition.test(stack); }
    @Override public boolean isBaseIngredient(ItemStack stack) {
        return stack.getItem() instanceof TieredItem item && (item.getTier() == Tiers.DIAMOND || item.getTier() == Tiers.NETHERITE)
                && !MorphScaleItems.isMorphScaleItem(stack);
    }
    @Override public boolean matches(Container input, Level level) {
        return isTemplateIngredient(input.getItem(0)) && isBaseIngredient(input.getItem(1)) && isAdditionIngredient(input.getItem(2));
    }
    @Override public ItemStack assemble(Container input, RegistryAccess access) {
        ItemStack result = input.getItem(1).copy();
        result.setCount(1);
        result.getOrCreateTag().putBoolean(MorphScaleItems.MARKER_KEY, true);
        return result;
    }
    @Override public ItemStack getResultItem(RegistryAccess access) { return new ItemStack(Items.DIAMOND_PICKAXE); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 3; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return ModMoonContent.LIGHTWEIGHT_UPGRADE.get(); }

    public static final class Serializer implements RecipeSerializer<LightweightUpgradeRecipe> {
        @Override public LightweightUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new LightweightUpgradeRecipe(id, Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "template")),
                    Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "addition")));
        }
        @Override public LightweightUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new LightweightUpgradeRecipe(id, Ingredient.fromNetwork(buffer), Ingredient.fromNetwork(buffer));
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, LightweightUpgradeRecipe recipe) {
            recipe.template.toNetwork(buffer); recipe.addition.toNetwork(buffer);
        }
    }
}
