package github.com.gengyoubo.sscfe.integration.jei;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.init.ModMoonContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.onixary.shapeShifterCurseForge.registry.ModItems;

import java.util.ArrayList;
import java.util.List;

/** Discovered by JEI only when it is installed; no common setup loads this class. */
@JeiPlugin
public final class SscfeJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Sscfe.MOD_ID, "moondust_armor_repair");
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        registration.addExtraIngredients(VanillaTypes.ITEM_STACK, List.of(ModMoonContent.adaptationBook()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Item> armorItems = List.of(
                ModItems.MORPHSCALE_HEADRING.get(), ModItems.MORPHSCALE_VEST.get(),
                ModItems.MORPHSCALE_CUISH.get(), ModItems.MORPHSCALE_ANKLET.get(),
                ModItems.NETHERITE_MORPHSCALE_HEADRING.get(), ModItems.NETHERITE_MORPHSCALE_VEST.get(),
                ModItems.NETHERITE_MORPHSCALE_CUISH.get(), ModItems.NETHERITE_MORPHSCALE_ANKLET.get());
        ItemStack moondust = new ItemStack(ModItems.UNTREATED_MOONDUST.get());
        List<IJeiAnvilRecipe> recipes = new ArrayList<>();
        List<ItemStack> infoItems = new ArrayList<>();
        infoItems.add(moondust.copy());
        var factory = registration.getVanillaRecipeFactory();
        for (int index = 0; index < armorItems.size(); index++) {
            ItemStack damaged = new ItemStack(armorItems.get(index));
            damaged.setDamageValue(damaged.getMaxDamage() / 2);
            ItemStack repaired = damaged.copy();
            repaired.setDamageValue(Math.max(0, damaged.getDamageValue() - damaged.getMaxDamage() / 4));
            recipes.add(factory.createAnvilRecipe(damaged, List.of(moondust.copy()), List.of(repaired),
                    ResourceLocation.fromNamespaceAndPath(Sscfe.MOD_ID, "moondust_armor_repair/" + index)));
            infoItems.add(new ItemStack(armorItems.get(index)));
        }
        addMoonAdaptationRecipes(registration, recipes);
        registration.addRecipes(RecipeTypes.ANVIL, recipes);
        registration.addIngredientInfo(infoItems, VanillaTypes.ITEM_STACK,
                Component.translatable("jei.sscfe.moondust_armor_repair"));
        registration.addIngredientInfo(ModMoonContent.adaptationBook(), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.sscfe.moon_adaptation"));
        registration.addIngredientInfo(new ItemStack(ModMoonContent.EDIBLE_MOONDUST.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.sscfe.edible_moondust"));
    }

    private static void addMoonAdaptationRecipes(IRecipeRegistration registration, List<IJeiAnvilRecipe> recipes) {
        var factory = registration.getVanillaRecipeFactory();
        var enchantment = ModMoonContent.MOON_ADAPTATION.get();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ItemStack plain = new ItemStack(item);
            if (!enchantment.canEnchant(plain)) continue;
            var itemId = ForgeRegistries.ITEMS.getKey(item);
            String path = "moon_adaptation/" + itemId.getNamespace() + "/" + itemId.getPath();
            ItemStack adapted = plain.copy();
            adapted.enchant(enchantment, 1);
            recipes.add(factory.createAnvilRecipe(plain, List.of(ModMoonContent.adaptationBook()), List.of(adapted.copy()),
                    ResourceLocation.fromNamespaceAndPath(Sscfe.MOD_ID, path + "/apply")));
            adapted.setDamageValue(adapted.getMaxDamage() / 2);
            ItemStack repaired = adapted.copy();
            repaired.setDamageValue(Math.max(0, adapted.getDamageValue() - adapted.getMaxDamage() / 4));
            recipes.add(factory.createAnvilRecipe(adapted, List.of(new ItemStack(ModItems.UNTREATED_MOONDUST.get())), List.of(repaired),
                    ResourceLocation.fromNamespaceAndPath(Sscfe.MOD_ID, path + "/repair")));
        }
    }
}
