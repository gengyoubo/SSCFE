package github.com.gengyoubo.sscfe.moon;

import com.mojang.authlib.GameProfile;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.init.ModMoonContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.onixary.shapeShifterCurseForge.api.SscApi;
import net.onixary.shapeShifterCurseForge.form.FormRegistry;
import net.onixary.shapeShifterCurseForge.power.FormPowerEvents;
import net.onixary.shapeShifterCurseForge.power.MissingPowerEvents;
import net.onixary.shapeShifterCurseForge.registry.ModItems;

import java.util.UUID;

@GameTestHolder(Sscfe.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MoonFeatureGameTests {
    @GameTest(template = "empty")
    public static void moonRecipes(GameTestHelper helper) {
        var grid = grid();
        // Put the book and core in different positions from the comment to verify shapelessness.
        for (int i = 0; i < 9; i++) grid.setItem(i, new ItemStack(ModItems.UNTREATED_MOONDUST.get()));
        grid.setItem(5, new ItemStack(Items.BOOK));
        grid.setItem(8, new ItemStack(ModItems.MORPHSCALE_CORE.get()));
        CraftingRecipe bookRecipe = recipe(helper, "moon_adaptation_book");
        helper.assertTrue(bookRecipe.matches(grid, helper.getLevel()), "Book recipe accepts any ingredient order");
        ItemStack result = bookRecipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(Items.ENCHANTED_BOOK)
                && EnchantmentHelper.getEnchantments(result).getOrDefault(ModMoonContent.MOON_ADAPTATION.get(), 0) == 1,
                "Crafted book contains Moon Adaptation I");
        grid.setItem(0, ItemStack.EMPTY);
        helper.assertTrue(!bookRecipe.matches(grid, helper.getLevel()), "Book requires all seven pieces of moondust");
        Item[] foods = {Items.APPLE, Items.BREAD, Items.COOKED_BEEF, Items.CARROT, Items.ROTTEN_FLESH};
        for (int i = 0; i < 9; i++) grid.setItem(i, i % 2 == 0 ? new ItemStack(foods[i / 2])
                : new ItemStack(ModItems.UNTREATED_MOONDUST.get()));
        CraftingRecipe foodRecipe = recipe(helper, "edible_moondust");
        helper.assertTrue(foodRecipe.matches(grid, helper.getLevel()), "Each food position accepts a different food");
        result = foodRecipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModMoonContent.EDIBLE_MOONDUST.get()) && result.getCount() == 5,
                "Food recipe makes five edible moondust");
        grid.setItem(0, new ItemStack(Items.COBBLESTONE));
        helper.assertTrue(!foodRecipe.matches(grid, helper.getLevel()), "Non-food cannot substitute for food");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void moonAnvilApplicationAndRepair(GameTestHelper helper) {
        var player = new TestPlayer(helper.getLevel());
        var enchantment = ModMoonContent.MOON_ADAPTATION.get();
        Item[] gear = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.ELYTRA};
        for (Item item : gear) {
            ItemStack armor = new ItemStack(item);
            armor.enchant(Enchantments.UNBREAKING, 2);
            armor.setDamageValue(armor.getMaxDamage() / 2);
            var menu = new AnvilMenu(0, player.getInventory());
            menu.getSlot(0).set(armor.copy());
            menu.getSlot(1).set(ModMoonContent.adaptationBook());
            menu.createResult();
            ItemStack adapted = menu.getSlot(2).getItem().copy();
            helper.assertTrue(!adapted.isEmpty() && MoonAdaptationEnchantment.isAdapted(adapted),
                    "Anvil applies the actual enchanted book: " + item);
            helper.assertTrue(adapted.getDamageValue() == armor.getDamageValue()
                    && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, adapted) == 2,
                    "Applying the book retains durability and existing enchantments");
            menu.getSlot(0).set(adapted.copy());
            menu.getSlot(1).set(new ItemStack(ModItems.UNTREATED_MOONDUST.get()));
            menu.createResult();
            ItemStack repaired = menu.getSlot(2).getItem();
            helper.assertTrue(!repaired.isEmpty()
                    && repaired.getDamageValue() == adapted.getDamageValue() - adapted.getMaxDamage() / 4
                    && MoonAdaptationEnchantment.isAdapted(repaired)
                    && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, repaired) == 2
                    && menu.repairItemCountCost == 1 && menu.getCost() > 0,
                    "One moondust repairs one quarter while retaining enchantments and charging experience: " + item);
        }
        ItemStack normal = new ItemStack(Items.DIAMOND_CHESTPLATE);
        helper.assertTrue(!normal.getItem().isValidRepairItem(normal, new ItemStack(ModItems.UNTREATED_MOONDUST.get())),
                "Unadapted normal armor does not gain moondust repairs");
        helper.assertTrue(!enchantment.canEnchant(new ItemStack(Items.DIAMOND_SWORD)), "Wearable enchantment rejects weapons");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void adaptedArmorBypassesFormRestrictions(GameTestHelper helper) throws Exception {
        var player = new TestPlayer(helper.getLevel());
        form(player, "shape-shifter-curse:spider_3");
        ItemStack plain = new ItemStack(Items.DIAMOND_CHESTPLATE);
        helper.assertTrue(MissingPowerEvents.isArmorRestricted(player, EquipmentSlot.CHEST, plain),
                "Control armor is forbidden by the spider form");
        ItemStack adapted = plain.copy();
        adapted.enchant(ModMoonContent.MOON_ADAPTATION.get(), 1);
        helper.assertTrue(!MissingPowerEvents.isArmorRestricted(player, EquipmentSlot.CHEST, adapted),
                "Adapted armor bypasses the central restriction check");
        helper.assertTrue(player.inventoryMenu.getSlot(6).mayPlace(adapted)
                && !player.inventoryMenu.getSlot(6).mayPlace(plain), "Inventory armor slot allows only adapted armor");
        player.setItemInHand(InteractionHand.MAIN_HAND, adapted.copy());
        var click = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        MissingPowerEvents.preventRightClickArmorEquip(click);
        helper.assertTrue(!click.isCanceled(), "Right-click equip is allowed");
        player.setItemSlot(EquipmentSlot.CHEST, adapted.copy());
        var maintain = MissingPowerEvents.class.getDeclaredMethod("maintainArmor", Player.class, boolean.class);
        maintain.setAccessible(true);
        maintain.invoke(null, player, false);
        helper.assertTrue(MoonAdaptationEnchantment.isAdapted(player.getItemBySlot(EquipmentSlot.CHEST)),
                "Server tick keeps adapted armor equipped");
        form(player, "shape-shifter-curse:allay_sp");
        maintain.invoke(null, player, true);
        helper.assertTrue(MoonAdaptationEnchantment.isAdapted(player.getItemBySlot(EquipmentSlot.CHEST)),
                "Changing form keeps adapted armor equipped");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void moondustFoodInEveryForm(GameTestHelper helper) throws Exception {
        var player = new TestPlayer(helper.getLevel());
        var prevents = FormPowerEvents.class.getDeclaredMethod("preventsItemUse", Player.class, ItemStack.class);
        prevents.setAccessible(true);
        for (var id : FormRegistry.forms().keySet()) {
            form(player, id.toString());
            player.getFoodData().setFoodLevel(10);
            player.getFoodData().setSaturation(0);
            ItemStack food = new ItemStack(ModMoonContent.EDIBLE_MOONDUST.get(), 2);
            helper.assertTrue(!(boolean) prevents.invoke(null, player, food), "Food is allowed in form " + id);
            food.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(player.getFoodData().getFoodLevel() == 13
                    && player.getFoodData().getSaturationLevel() == 3F && food.getCount() == 1,
                    "Food restores exactly three hunger and saturation in form " + id + ": hunger="
                            + player.getFoodData().getFoodLevel() + ", saturation="
                            + player.getFoodData().getSaturationLevel() + ", count=" + food.getCount());
        }
        helper.succeed();
    }

    private static CraftingRecipe recipe(GameTestHelper helper, String name) {
        return (CraftingRecipe) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(Sscfe.MOD_ID, name)).orElseThrow();
    }

    private static TransientCraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }

    private static void form(Player player, String id) {
        var data = SscApi.currentForm(player).orElseThrow();
        data.setFormId(id);
        data.setFormTier(3);
        data.setContentEnabled(true);
    }

    private static final class TestPlayer extends Player {
        private TestPlayer(Level level) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "moon-test")); }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
    }
}
