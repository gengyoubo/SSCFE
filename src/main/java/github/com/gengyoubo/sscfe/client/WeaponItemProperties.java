package github.com.gengyoubo.sscfe.client;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.init.ModWeaponContent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@SuppressWarnings("removal")
@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WeaponItemProperties {
    private WeaponItemProperties() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> registerCrossbow(ModWeaponContent.COMPOUND_KINETIC_CROSSBOW.get()));
    }

    /** Mirrors the vanilla crossbow item properties, which are only registered for Items.CROSSBOW. */
    private static void registerCrossbow(Item crossbow) {
        ItemProperties.register(crossbow, new ResourceLocation("pull"),
                (stack, level, entity, seed) -> entity == null || CrossbowItem.isCharged(stack) ? 0.0F
                        : (float) (stack.getUseDuration() - entity.getUseItemRemainingTicks())
                        / (float) CrossbowItem.getChargeDuration(stack));
        ItemProperties.register(crossbow, new ResourceLocation("pulling"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                        && !CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
        ItemProperties.register(crossbow, new ResourceLocation("charged"),
                (stack, level, entity, seed) -> CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
        ItemProperties.register(crossbow, new ResourceLocation("firework"),
                (stack, level, entity, seed) -> CrossbowItem.isCharged(stack)
                        && CrossbowItem.containsChargedProjectile(stack, Items.FIREWORK_ROCKET) ? 1.0F : 0.0F);
    }
}
