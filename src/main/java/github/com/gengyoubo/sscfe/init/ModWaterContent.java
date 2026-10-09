package github.com.gengyoubo.sscfe.init;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.water.CuriosWaterCompat;
import github.com.gengyoubo.sscfe.water.LargeWaterTankItem;
import github.com.gengyoubo.sscfe.water.MoisturizerItem;
import github.com.gengyoubo.sscfe.water.WaterFoodItem;
import github.com.gengyoubo.sscfe.water.WaterTankBlock;
import github.com.gengyoubo.sscfe.water.WaterTankBlockEntity;
import github.com.gengyoubo.sscfe.water.WaterCurseItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.onixary.shapeShifterCurseForge.registry.ModCreativeModeTabs;

public final class ModWaterContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Sscfe.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Sscfe.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Sscfe.MOD_ID);

    public static final RegistryObject<Item> WET_MOONDUST = ITEMS.register("wet_moondust", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TANK_CORE = ITEMS.register("tank_core", () -> new Item(new Item.Properties()));
    public static final RegistryObject<MoisturizerItem> MOISTURIZER = ITEMS.register("moisturizer", MoisturizerItem::new);
    public static final RegistryObject<WaterFoodItem> WATER_AS_FOOD = ITEMS.register("water_as_food", WaterFoodItem::new);
    public static final RegistryObject<WaterCurseItem> WATER_CURSE = ITEMS.register("water_curse", WaterCurseItem::new);
    public static final RegistryObject<WaterTankBlock> LARGE_WATER_TANK_BLOCK = BLOCKS.register("large_water_tank",
            () -> new WaterTankBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.5F).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<LargeWaterTankItem> LARGE_WATER_TANK = ITEMS.register("large_water_tank",
            () -> new LargeWaterTankItem(LARGE_WATER_TANK_BLOCK.get()));
    public static final RegistryObject<BlockEntityType<WaterTankBlockEntity>> WATER_TANK_ENTITY =
            BLOCK_ENTITIES.register("water_tank", () -> BlockEntityType.Builder.of(
                    WaterTankBlockEntity::new, LARGE_WATER_TANK_BLOCK.get()).build(null));

    private ModWaterContent() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(ModWaterContent::setup);
        bus.addListener(ModWaterContent::creativeTabs);
    }

    private static void setup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("curios")) {
            event.enqueueWork(CuriosWaterCompat::register);
        }
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeModeTabs.SSC_ITEMS.getKey())) {
            event.accept(WET_MOONDUST);
            event.accept(TANK_CORE);
            event.accept(MOISTURIZER);
            event.accept(LARGE_WATER_TANK);
            event.accept(WATER_AS_FOOD);
            event.accept(WATER_CURSE);
        }
    }
}
