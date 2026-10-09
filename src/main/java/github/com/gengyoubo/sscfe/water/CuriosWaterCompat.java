package github.com.gengyoubo.sscfe.water;

import github.com.gengyoubo.sscfe.init.ModWaterContent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.List;

/** Loaded only when Curios is present; fluid storage itself does not require Curios. */
public final class CuriosWaterCompat {
    private CuriosWaterCompat() {}

    public static void register() {
        ICurioItem accessory = new ICurioItem() {
            @Override
            public boolean canEquip(SlotContext context, ItemStack stack) {
                return context.entity() instanceof Player player && AxolotlWaterService.isAxolotl(player);
            }
        };
        CuriosApi.registerCurio(ModWaterContent.MOISTURIZER.get(), accessory);
        CuriosApi.registerCurio(ModWaterContent.LARGE_WATER_TANK.get(), accessory);
        CuriosApi.registerCurio(ModWaterContent.WATER_AS_FOOD.get(), accessory);
        CuriosApi.registerCurio(ModWaterContent.WATER_CURSE.get(), accessory);
    }

    public static List<ItemStack> equipped(Player player) {
        List<ItemStack> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.getCurios().values().forEach(slots -> {
            for (int i = 0; i < slots.getSlots(); i++) {
                ItemStack stack = slots.getStacks().getStackInSlot(i);
                if (!stack.isEmpty()) result.add(stack);
            }
        }));
        return result;
    }
}
