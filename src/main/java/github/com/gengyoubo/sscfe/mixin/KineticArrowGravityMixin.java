package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.weapon.LowGravityArrow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets arrows carry a synced "low gravity" flag. Flagged arrows get back 25% of the 0.05 downward
 * velocity vanilla subtracts each tick; the flag is synced so the client-side flight prediction matches.
 */
@Mixin(AbstractArrow.class)
public abstract class KineticArrowGravityMixin implements LowGravityArrow {
    @Unique
    private static final EntityDataAccessor<Boolean> SSCFE_LOW_GRAVITY =
            SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final String SSCFE_LOW_GRAVITY_TAG = "sscfe_low_gravity";
    @Unique
    private static final double SSCFE_VANILLA_GRAVITY = 0.05F;

    @Unique
    private SynchedEntityData sscfe$data() {
        return ((Entity) (Object) this).getEntityData();
    }

    @Override
    public boolean sscfe$isLowGravity() {
        return sscfe$data().get(SSCFE_LOW_GRAVITY);
    }

    @Override
    public void sscfe$setLowGravity(boolean lowGravity) {
        sscfe$data().set(SSCFE_LOW_GRAVITY, lowGravity);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void sscfe$defineLowGravity(CallbackInfo ci) {
        sscfe$data().define(SSCFE_LOW_GRAVITY, false);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void sscfe$saveLowGravity(CompoundTag tag, CallbackInfo ci) {
        if (sscfe$isLowGravity()) tag.putBoolean(SSCFE_LOW_GRAVITY_TAG, true);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void sscfe$readLowGravity(CompoundTag tag, CallbackInfo ci) {
        sscfe$setLowGravity(tag.getBoolean(SSCFE_LOW_GRAVITY_TAG));
    }

    /** The only setDeltaMovement(DDD) call in tick() is the gravity step: y - 0.05. */
    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;setDeltaMovement(DDD)V"), index = 1)
    private double sscfe$resistGravity(double y) {
        return sscfe$isLowGravity() ? y + SSCFE_VANILLA_GRAVITY * GRAVITY_RESISTANCE : y;
    }
}
