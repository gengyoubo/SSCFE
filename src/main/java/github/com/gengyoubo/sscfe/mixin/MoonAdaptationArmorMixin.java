package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.moon.MoonAdaptationEnchantment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.onixary.shapeShifterCurseForge.power.MissingPowerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers inventory slots, right-click equipping, form changes and the server armor fallback. */
@Mixin(MissingPowerEvents.class)
public abstract class MoonAdaptationArmorMixin {
    @Inject(method = "isArmorRestricted", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sscfe$allowAdaptedArmor(Player player, EquipmentSlot slot, ItemStack stack,
                                               CallbackInfoReturnable<Boolean> callback) {
        if (MoonAdaptationEnchantment.isAdapted(stack)) callback.setReturnValue(false);
    }
}
