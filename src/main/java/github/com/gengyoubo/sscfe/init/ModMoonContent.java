package github.com.gengyoubo.sscfe.init;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.moon.EdibleMoondustRecipe;
import github.com.gengyoubo.sscfe.moon.EdibleMoondustItem;
import github.com.gengyoubo.sscfe.moon.FloatingMoondustRecipe;
import github.com.gengyoubo.sscfe.moon.MoonAdaptationBookRecipe;
import github.com.gengyoubo.sscfe.moon.MoonAdaptationEnchantment;
import github.com.gengyoubo.sscfe.moon.MoonLightweightEnchantment;
import github.com.gengyoubo.sscfe.moon.MoonLightweightBookRecipe;
import github.com.gengyoubo.sscfe.moon.LightweightUpgradeRecipe;
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

    public static final RegistryObject<Item> LIGHTWEIGHT_CORE = ITEMS.register("lightweight_core", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SUPER_LIGHTWEIGHT_CORE = ITEMS.register("super_lightweight_core", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FLOATING_MOONDUST = ITEMS.register("floating_moondust", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> EDIBLE_MOONDUST = ITEMS.register("edible_moondust", EdibleMoondustItem::new);
    public static final RegistryObject<Enchantment> MOON_LIGHTWEIGHT = ENCHANTMENTS.register("moon_lightweight", MoonLightweightEnchantment::new);
    public static final RegistryObject<Enchantment> MOON_ADAPTATION = ENCHANTMENTS.register("moon_adaptation", MoonAdaptationEnchantment::new);
    public static final RegistryObject<RecipeSerializer<MoonAdaptationBookRecipe>> MOON_ADAPTATION_BOOK = RECIPES.register(
            "moon_adaptation_book", () -> new SimpleCraftingRecipeSerializer<>(MoonAdaptationBookRecipe::new));
    public static final RegistryObject<RecipeSerializer<EdibleMoondustRecipe>> EDIBLE_MOONDUST_RECIPE = RECIPES.register(
            "edible_moondust", () -> new SimpleCraftingRecipeSerializer<>(EdibleMoondustRecipe::new));

    public static final RegistryObject<RecipeSerializer<FloatingMoondustRecipe>> FLOATING_MOONDUST_RECIPE = RECIPES.register(
            "floating_moondust", () -> new SimpleCraftingRecipeSerializer<>(FloatingMoondustRecipe::new));

    public static final RegistryObject<RecipeSerializer<LightweightUpgradeRecipe>> LIGHTWEIGHT_UPGRADE = RECIPES.register(
            "lightweight_upgrade", LightweightUpgradeRecipe.Serializer::new);
    public static final RegistryObject<RecipeSerializer<MoonLightweightBookRecipe>> MOON_LIGHTWEIGHT_BOOK = RECIPES.register(
            "moon_lightweight_book", () -> new SimpleCraftingRecipeSerializer<>(MoonLightweightBookRecipe::new));

    private ModMoonContent() {}

    public static ItemStack adaptationBook() {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(MOON_ADAPTATION.get(), 1));
    }

    public static ItemStack lightweightBook() {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(MOON_LIGHTWEIGHT.get(), 1));
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
            event.accept(FLOATING_MOONDUST);
            event.accept(LIGHTWEIGHT_CORE);
            event.accept(SUPER_LIGHTWEIGHT_CORE);
            event.accept(lightweightBook());
            event.accept(adaptationBook());
        }
    }
}
