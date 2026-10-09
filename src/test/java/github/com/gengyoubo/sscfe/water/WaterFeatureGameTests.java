package github.com.gengyoubo.sscfe.water;

import com.mojang.authlib.GameProfile;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.init.ModWaterContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.onixary.shapeShifterCurseForge.api.SscApi;
import net.onixary.shapeShifterCurseForge.form.FormRegistry;
import net.onixary.shapeShifterCurseForge.power.FormPowerEvents;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;

@GameTestHolder(Sscfe.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterFeatureGameTests {
    @GameTest(template = "empty")
    public static void waterCapabilities(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModWaterContent.LARGE_WATER_TANK.get());
        var handler = WaterItemStorage.handler(stack);
        helper.assertTrue(handler != null && handler.getTankCapacity(0) == 32_000, "32 B item capability");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0, "Reject lava");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 40_000), FluidAction.SIMULATE) == 32_000,
                "Simulated fill clamps capacity");
        helper.assertTrue(WaterItemStorage.amount(stack) == 0, "Simulation cannot mutate storage");
        helper.assertTrue(WaterItemStorage.fill(stack, 40_000, FluidAction.EXECUTE) == 32_000, "Fill clamps capacity");
        helper.assertTrue(WaterItemStorage.drain(stack, 500, FluidAction.SIMULATE) == 500
                && WaterItemStorage.amount(stack) == 32_000, "Drain simulation cannot mutate storage");
        ItemStack reloaded = ItemStack.of(stack.save(new CompoundTag()));
        helper.assertTrue(WaterItemStorage.amount(reloaded) == 32_000, "Item water persists through save/load");
        helper.assertTrue(WaterItemStorage.amount(stack.copy()) == 32_000, "Item copies preserve water");
        TestPlayer player = axolotl(helper);
        ItemStack emptyTank = new ItemStack(ModWaterContent.LARGE_WATER_TANK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, emptyTank);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WATER_BUCKET));
        WaterItemStorage.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(WaterItemStorage.amount(emptyTank) == 1000
                && player.getOffhandItem().is(Items.BUCKET), "Other-hand water bucket fills the held tank");
        WaterItemStorage.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(WaterItemStorage.amount(emptyTank) == 0
                && player.getOffhandItem().is(Items.WATER_BUCKET), "Other-hand empty bucket drains the held tank");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placedTankAndBuckets(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var block = ModWaterContent.LARGE_WATER_TANK_BLOCK.get();
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        var entity = (WaterTankBlockEntity) helper.getLevel().getBlockEntity(pos);
        ItemStack stack = new ItemStack(ModWaterContent.LARGE_WATER_TANK.get());
        WaterItemStorage.fill(stack, 12_345, FluidAction.EXECUTE);
        block.setPlacedBy(helper.getLevel(), pos, block.defaultBlockState(), null, stack);
        helper.assertTrue(entity.tank().getFluidAmount() == 12_345, "Placement preserves partial water amount");
        for (Direction side : Direction.values()) {
            var cap = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
            helper.assertTrue(cap.getTankCapacity(0) == 32_000, "Every side exposes Forge fluid storage");
            helper.assertTrue(cap.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0, "Block rejects lava");
        }
        var saved = entity.saveWithoutMetadata();
        var restored = new WaterTankBlockEntity(pos, block.defaultBlockState());
        restored.load(saved);
        helper.assertTrue(restored.tank().getFluidAmount() == 12_345, "Block water survives reload");
        var filledBucket = FluidUtil.tryFillContainer(new ItemStack(Items.BUCKET), entity.tank(), 1000, null, true);
        helper.assertTrue(filledBucket.isSuccess() && filledBucket.getResult().is(Items.WATER_BUCKET)
                && entity.tank().getFluidAmount() == 11_345, "Bucket removes exactly 1 B");
        var emptyBucket = FluidUtil.tryEmptyContainer(filledBucket.getResult(), entity.tank(), 1000, null, true);
        helper.assertTrue(emptyBucket.isSuccess() && emptyBucket.getResult().is(Items.BUCKET)
                && entity.tank().getFluidAmount() == 12_345, "Bucket returns exactly 1 B");
        var drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(), pos, entity);
        helper.assertTrue(drops.size() == 1 && WaterItemStorage.amount(drops.get(0)) == 12_345,
                "Breaking the tank preserves water in its drop");
        entity.invalidateCaps();
        helper.assertTrue(!entity.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(), "Unloaded capability invalidates");
        entity.reviveCaps();
        helper.assertTrue(entity.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(), "Capability revives");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tankLevelSync(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var state = ModWaterContent.LARGE_WATER_TANK_BLOCK.get().defaultBlockState();
        helper.getLevel().setBlock(pos, state, 3);
        var serverTank = (WaterTankBlockEntity) helper.getLevel().getBlockEntity(pos);
        var clientTank = new WaterTankBlockEntity(pos, state);
        var pipe = serverTank.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP)
                .orElseThrow(AssertionError::new);
        helper.assertTrue(serverTank.fillFraction() == 0, "Empty tank has no rendered water");
        pipe.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        clientTank.handleUpdateTag(serverTank.getUpdateTag());
        helper.assertTrue(clientTank.tank().getFluidAmount() == 1000 && clientTank.fillFraction() == 1F / 32F,
                "Chunk sync preserves the one-bucket water level");
        pipe.fill(new FluidStack(Fluids.WATER, 15_000), FluidAction.EXECUTE);
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        helper.assertTrue(clientTank.fillFraction() == 0.5F, "Update packet raises water level to half full");
        pipe.fill(new FluidStack(Fluids.WATER, 20_000), FluidAction.EXECUTE);
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        helper.assertTrue(clientTank.fillFraction() == 1F, "Water level clamps at full capacity");
        pipe.drain(24_000, FluidAction.EXECUTE);
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        helper.assertTrue(clientTank.fillFraction() == 0.25F, "Pipe extraction lowers the synchronized water level");
        pipe.drain(32_000, FluidAction.EXECUTE);
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        helper.assertTrue(clientTank.tank().isEmpty() && clientTank.fillFraction() == 0F,
                "Draining all water clears the client fluid volume");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void moisturizerAndCoreMixin(GameTestHelper helper) throws Exception {
        TestPlayer player = axolotl(helper);
        ItemStack moisturizer = new ItemStack(ModWaterContent.MOISTURIZER.get());
        equip(player, "belt", moisturizer);
        WaterItemStorage.fill(moisturizer, 100, FluidAction.EXECUTE);
        player.setAirSupply(150);
        var moistureTick = FormPowerEvents.class.getDeclaredMethod("tickCustomWaterBreathing", Player.class);
        moistureTick.setAccessible(true);
        player.tickCount = 1;
        for (int i = 0; i < 200; i++) moistureTick.invoke(null, player);
        helper.assertTrue(player.getAirSupply() == 150 && WaterItemStorage.amount(moisturizer) == 100,
                "Mixin prevents land moisture loss between payment ticks");
        player.tickCount = 20;
        moistureTick.invoke(null, player);
        helper.assertTrue(player.getAirSupply() == 150 && WaterItemStorage.amount(moisturizer) == 98,
                "Moisturizer spends exactly 2 mB per second");
        WaterItemStorage.drain(moisturizer, 98, FluidAction.EXECUTE);
        player.tickCount = 21;
        player.getRandom().setSeed(1);
        for (int i = 0; i < 200; i++) moistureTick.invoke(null, player);
        helper.assertTrue(player.getAirSupply() < 150, "Empty moisturizer resumes the core's moisture drain");
        player.inWater = true;
        int moistureBeforeWater = player.getAirSupply();
        player.tickCount = 40;
        moistureTick.invoke(null, player);
        helper.assertTrue(WaterItemStorage.amount(moisturizer) == 3000, "Water automatically refills the moisturizer");
        helper.assertTrue(player.getAirSupply() == moistureBeforeWater,
                "Partial immersion protects moisture without spending stored water");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedSupplyFoodAndWeight(GameTestHelper helper) {
        TestPlayer player = axolotl(helper);
        ItemStack tank = new ItemStack(ModWaterContent.LARGE_WATER_TANK.get());
        ItemStack moisturizer = new ItemStack(ModWaterContent.MOISTURIZER.get());
        equip(player, "back", tank);
        equip(player, "belt", moisturizer);
        equip(player, "charm", new ItemStack(ModWaterContent.WATER_AS_FOOD.get()));
        WaterItemStorage.fill(tank, 32_000, FluidAction.EXECUTE);
        player.tickCount = 20;
        helper.assertTrue(AxolotlWaterService.protectMoisture(player), "Large tank supplies an empty moisturizer");
        helper.assertTrue(WaterItemStorage.amount(tank) == 29_000 && WaterItemStorage.amount(moisturizer) == 2998,
                "Transfer conserves water and only pays the regular 2 mB cost");
        helper.assertTrue(!AxolotlWaterService.consumeWater(player, 30_000)
                && WaterItemStorage.amount(tank) == 29_000, "Insufficient shared water never partially drains");
        helper.assertTrue(AxolotlWaterService.consumeWater(player, 1000)
                && WaterItemStorage.amount(tank) == 28_000, "Shared supply drains the requested amount");
        player.setAirSupply(100);
        player.getFoodData().setFoodLevel(19);
        player.getFoodData().setSaturation(0);
        double initialSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        AxolotlWaterService.updateEquipment(player);
        helper.assertTrue(player.getAirSupply() == 99 && player.getFoodData().getFoodLevel() == 20
                && player.getFoodData().getSaturationLevel() == 1.0F, "1 moisture becomes 1 hunger and 1 saturation");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - initialSpeed * 0.9D) < 1E-6,
                "Equipped tank reduces total speed by ten percent");
        AxolotlWaterService.updateEquipment(player);
        helper.assertTrue(player.getAirSupply() == 99, "Full hunger consumes no moisture");
        player.setAirSupply(0);
        player.getFoodData().setFoodLevel(19);
        AxolotlWaterService.updateEquipment(player);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 19 && player.getAirSupply() == 0,
                "Zero moisture cannot provide free food");
        SscApi.currentForm(player).orElseThrow().setFormId(FormRegistry.ORIGINAL_BEFORE_ENABLE.toString());
        AxolotlWaterService.updateEquipment(player);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - initialSpeed) < 1E-6,
                "Leaving axolotl form removes the tank's speed penalty");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 19, "Other forms cannot use the food accessory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wetMoondustCrafting(GameTestHelper helper) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        var grid = new TransientCraftingContainer(menu, 3, 3);
        for (int slot : new int[]{1, 3, 5, 7}) grid.setItem(slot, new ItemStack(Items.WATER_BUCKET));
        grid.setItem(4, new ItemStack(net.onixary.shapeShifterCurseForge.registry.ModItems.UNTREATED_MOONDUST.get()));
        var recipe = helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("sscfe", "wet_moondust"))
                .orElseThrow();
        @SuppressWarnings("unchecked")
        var crafting = (net.minecraft.world.item.crafting.CraftingRecipe) recipe;
        helper.assertTrue(crafting.matches(grid, helper.getLevel()), "Wet moondust matches the issue's pattern");
        helper.assertTrue(crafting.assemble(grid, helper.getLevel().registryAccess()).is(ModWaterContent.WET_MOONDUST.get()),
                "Recipe creates wet moondust");
        var remains = crafting.getRemainingItems(grid);
        for (int slot : new int[]{1, 3, 5, 7}) {
            helper.assertTrue(remains.get(slot).is(Items.BUCKET), "Water buckets leave empty buckets");
        }
        for (String id : new String[]{"moisturizer", "tank_core", "large_water_tank", "water_as_food"}) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("sscfe", id)).isPresent(),
                    "Recipe loads: " + id);
        }
        helper.succeed();
    }

    private static TestPlayer axolotl(GameTestHelper helper) {
        TestPlayer player = new TestPlayer(helper.getLevel());
        var data = SscApi.currentForm(player).orElseThrow();
        data.setFormId("shape-shifter-curse:axolotl_2");
        data.setFormGroupId("shape-shifter-curse:axolotl_form");
        data.setFormTier(2);
        data.setContentEnabled(true);
        CuriosApi.getCuriosInventory(player).orElseThrow(AssertionError::new).reset();
        return player;
    }

    private static void equip(Player player, String slot, ItemStack stack) {
        CuriosApi.getCuriosInventory(player).orElseThrow(AssertionError::new)
                .getStacksHandler(slot).orElseThrow().getStacks().setStackInSlot(0, stack);
    }

    private static final class TestPlayer extends Player {
        private boolean inWater;
        private TestPlayer(Level level) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "water-test")); }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isInWater() { return inWater; }
    }
}
