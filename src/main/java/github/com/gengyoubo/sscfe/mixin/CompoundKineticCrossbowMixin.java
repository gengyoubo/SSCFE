package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.init.ModWeaponContent;
import github.com.gengyoubo.sscfe.weapon.LowGravityArrow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compound kinetic crossbow:
 * - arrows deal 25% more base damage, matching the compound kinetic bow;
 * - arrows resist 25% of gravity (see KineticArrowGravityMixin);
 * - charges 25% faster (charge duration divided by 1.25, e.g. 25 -> 20 ticks).
 */
@Mixin(CrossbowItem.class)
public abstract class CompoundKineticCrossbowMixin {
    @Unique
    private static final float SSCFE_CHARGE_SPEED = 1.25F;

    @Inject(method = "getArrow", at = @At("RETURN"))
    private static void sscfe$kineticArrow(Level level, LivingEntity shooter, ItemStack crossbow, ItemStack ammo,
                                           CallbackInfoReturnable<AbstractArrow> cir) {
        if (crossbow.is(ModWeaponContent.COMPOUND_KINETIC_CROSSBOW.get())) {
            AbstractArrow arrow = cir.getReturnValue();
            arrow.setBaseDamage(arrow.getBaseDamage() * 1.25D);
            ((LowGravityArrow) arrow).sscfe$setLowGravity(true);
        }
    }

    /** Also drives the use duration, the charge power check and the "pull" model property. */
    @Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
    private static void sscfe$fasterCharge(ItemStack crossbow, CallbackInfoReturnable<Integer> cir) {
        if (crossbow.is(ModWeaponContent.COMPOUND_KINETIC_CROSSBOW.get())) {
            cir.setReturnValue(Math.max(1, Math.round(cir.getReturnValueI() / SSCFE_CHARGE_SPEED)));
        }
    }
}
