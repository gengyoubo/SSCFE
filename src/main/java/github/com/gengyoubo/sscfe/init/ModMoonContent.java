package github.com.gengyoubo.sscfe.init;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.moon.EdibleMoondustRecipe;
import github.com.gengyoubo.sscfe.moon.EdibleMoondustItem;
import github.com.gengyoubo.sscfe.moon.MoonAdaptationBookRecipe;
import github.com.gengyoubo.sscfe.moon.MoonAdaptationEnchantment;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.onixary.shapeShifterCurseForge.registry.ModCreativeModeTabs;

public final class ModMoonContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Sscfe.MOD_ID);
    private static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Sscfe.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Sscfe.MOD_ID);

    public static final RegistryObject<Item> EDIBLE_MOONDUST = ITEMS.register("edible_moondust", EdibleMoondustItem::new);
    public static final RegistryObject<Enchantment> MOON_ADAPTATION = ENCHANTMENTS.register("moon_adaptation", MoonAdaptationEnchantment::new);
    public static final RegistryObject<RecipeSerializer<MoonAdaptationBookRecipe>> MOON_ADAPTATION_BOOK = RECIPES.register(
            "moon_adaptation_book", () -> new SimpleCraftingRecipeSerializer<>(MoonAdaptationBookRecipe::new));
    public static final RegistryObject<RecipeSerializer<EdibleMoondustRecipe>> EDIBLE_MOONDUST_RECIPE = RECIPES.register(
            "edible_moondust", () -> new SimpleCraftingRecipeSerializer<>(EdibleMoondustRecipe::new));

    private ModMoonContent() {}

    public static ItemStack adaptationBook() {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(MOON_ADAPTATION.get(), 1));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ENCHANTMENTS.register(bus);
        RECIPES.register(bus);
        bus.addListener(ModMoonContent::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeModeTabs.SSC_ITEMS.getKey())) {
            event.accept(EDIBLE_MOONDUST);
            event.accept(adaptationBook());
        }
    }
}
