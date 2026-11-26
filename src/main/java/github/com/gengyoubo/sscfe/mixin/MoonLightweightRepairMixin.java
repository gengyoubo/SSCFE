package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.init.ModMoonContent;
import github.com.gengyoubo.sscfe.moon.MoonLightweightEnchantment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TieredItem.class)
public abstract class MoonLightweightRepairMixin {
    @Inject(method = "isValidRepairItem", at = @At("HEAD"), cancellable = true)
    private void sscfe$allowFloatingMoondust(ItemStack tool, ItemStack material, CallbackInfoReturnable<Boolean> callback) {
        if (MoonLightweightEnchantment.isLightweight(tool) && material.is(ModMoonContent.FLOATING_MOONDUST.get())) {
            callback.setReturnValue(true);
        }
    }
}
