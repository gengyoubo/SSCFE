package github.com.gengyoubo.sscfe.interaction;

import github.com.gengyoubo.sscfe.init.ModInteractionContent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Loaded only when Curios is present. */
public final class CuriosSpiderSaddleCompat {
    private CuriosSpiderSaddleCompat() {}

    public static void register() {
        CuriosApi.registerCurio(ModInteractionContent.SPIDER_SADDLE.get(), new ICurioItem() {
            @Override
            public boolean canEquip(SlotContext context, ItemStack stack) {
                return context.entity() instanceof Player player && SpiderSaddleService.isSpiderFinal(player);
            }

            @Override
            public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
                return true;
            }
        });
    }

    public static boolean hasSaddle(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.isEquipped(ModInteractionContent.SPIDER_SADDLE.get())).orElse(false);
    }
}
