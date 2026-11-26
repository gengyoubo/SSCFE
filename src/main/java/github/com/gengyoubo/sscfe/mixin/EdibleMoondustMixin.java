package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.init.ModMoonContent;
import github.com.gengyoubo.sscfe.moon.MoonLightweightEnchantment;
import net.onixary.shapeShifterCurseForge.items.MorphScaleItems;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.onixary.shapeShifterCurseForge.power.FormPowerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FormPowerEvents.class)
public abstract class EdibleMoondustMixin {
    @Inject(method = "preventsItemUse", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sscfe$allowMoondustFood(Player player, ItemStack stack,
                                              CallbackInfoReturnable<Boolean> callback) {
        if (stack.is(ModMoonContent.EDIBLE_MOONDUST.get())
                || (stack.getItem() instanceof TieredItem && (MorphScaleItems.hasUpgradeMarker(stack)
                || MoonLightweightEnchantment.isLightweight(stack)))) callback.setReturnValue(false);
    }
}
