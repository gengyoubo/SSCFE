package github.com.gengyoubo.sscfe.init;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.interaction.CuriosSpiderSaddleCompat;
import github.com.gengyoubo.sscfe.interaction.SpiderSaddleItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.onixary.shapeShifterCurseForge.registry.ModCreativeModeTabs;

/** Items used by multiplayer form interactions. */
public final class ModInteractionContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Sscfe.MOD_ID);

    public static final RegistryObject<SpiderSaddleItem> SPIDER_SADDLE = ITEMS.register("spider_saddle", SpiderSaddleItem::new);

    private ModInteractionContent() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(ModInteractionContent::setup);
        bus.addListener(ModInteractionContent::creativeTabs);
    }

    private static void setup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("curios")) event.enqueueWork(CuriosSpiderSaddleCompat::register);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeModeTabs.SSC_ITEMS.getKey())) event.accept(SPIDER_SADDLE);
    }
}
