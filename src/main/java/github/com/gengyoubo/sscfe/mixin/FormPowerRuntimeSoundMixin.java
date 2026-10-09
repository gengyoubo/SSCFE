package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.sound.FormSoundVolumePolicy;
import net.onixary.shapeShifterCurseForge.power.FormPowerRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Applies the SSCFE sound-volume policy to all data-driven play_sound actions. */
@Mixin(FormPowerRuntime.class)
public abstract class FormPowerRuntimeSoundMixin {
    @ModifyArg(
            method = "playSound",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;"
                            + "DDDLnet/minecraft/sounds/SoundEvent;"
                            + "Lnet/minecraft/sounds/SoundSource;FF)V",
                    remap = true
            ),
            index = 6,
            remap = false
    )
    private static float sscfe$increasePowerSoundVolume(float volume) {
        return FormSoundVolumePolicy.scale(volume);
    }
}
