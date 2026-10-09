package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.moon.MoonAdaptationEnchantment;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.onixary.shapeShifterCurseForge.items.armors.MorphScaleArmor;
import net.onixary.shapeShifterCurseForge.items.armors.NetheriteMorphScaleArmor;
import net.onixary.shapeShifterCurseForge.registry.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds moondust to the repair materials accepted by morphscale armor. */
@Mixin({Item.class, ArmorItem.class, ElytraItem.class})
public abstract class MorphscaleArmorRepairMixin {
    @Inject(method = "isValidRepairItem", at = @At("HEAD"), cancellable = true)
    private void sscfe$allowUntreatedMoondust(ItemStack armor, ItemStack material,
                                            CallbackInfoReturnable<Boolean> callback) {
        if (((Object) this instanceof MorphScaleArmor || (Object) this instanceof NetheriteMorphScaleArmor
                || MoonAdaptationEnchantment.isAdapted(armor))
                && material.is(ModItems.UNTREATED_MOONDUST.get())) {
            callback.setReturnValue(true);
        }
    }
}
