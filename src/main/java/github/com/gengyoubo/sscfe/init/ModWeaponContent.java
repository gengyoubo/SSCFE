package github.com.gengyoubo.sscfe.init;

import github.com.gengyoubo.sscfe.Sscfe;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.onixary.shapeShifterCurseForge.registry.ModCreativeModeTabs;

/** Extra ranged weapons built on top of the base mod's Avali gear. */
public final class ModWeaponContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Sscfe.MOD_ID);

    /**
     * Twice the vanilla crossbow's durability. Arrow damage +25%, 25% faster charging and 25% gravity
     * resistance for its arrows are applied in CompoundKineticCrossbowMixin / KineticArrowGravityMixin.
     */
    public static final RegistryObject<CrossbowItem> COMPOUND_KINETIC_CROSSBOW = ITEMS.register("compound_kinetic_crossbow",
            () -> new CrossbowItem(new Item.Properties().durability(930)));

    private ModWeaponContent() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(ModWeaponContent::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeModeTabs.SSC_ITEMS.getKey())) event.accept(COMPOUND_KINETIC_CROSSBOW);
    }
}
