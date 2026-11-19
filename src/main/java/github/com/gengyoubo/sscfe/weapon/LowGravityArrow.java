package github.com.gengyoubo.sscfe.weapon;

/**
 * Implemented on every {@link net.minecraft.world.entity.projectile.AbstractArrow} by KineticArrowGravityMixin.
 * Arrows fired from the compound kinetic crossbow resist part of the per-tick gravity.
 */
public interface LowGravityArrow {
    /** Fraction of vanilla arrow gravity that is cancelled for flagged arrows. */
    double GRAVITY_RESISTANCE = 0.25D;

    boolean sscfe$isLowGravity();

    void sscfe$setLowGravity(boolean lowGravity);
}
