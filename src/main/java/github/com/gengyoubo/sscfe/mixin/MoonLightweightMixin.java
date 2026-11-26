package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.moon.MoonLightweightEnchantment;
import net.minecraft.world.item.ItemStack;
import net.onixary.shapeShifterCurseForge.items.MorphScaleItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MorphScaleItems.class)
public abstract class MoonLightweightMixin {
    @Inject(method = "isMorphScaleItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sscfe$recognizeLightweight(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (MoonLightweightEnchantment.isLightweight(stack)) callback.setReturnValue(true);
    }
}
