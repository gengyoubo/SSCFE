package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.sound.FormSoundResponseService;
import github.com.gengyoubo.sscfe.sound.FormSoundVolumePolicy;
import net.minecraft.server.level.ServerPlayer;
import net.onixary.shapeShifterCurseForge.power.FormActivePowerService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Connects the make_sound key to nearby vanilla creature responses. */
@Mixin(FormActivePowerService.class)
public abstract class FormActivePowerSoundResponseMixin {
    @Inject(method = "setKeyPressed", at = @At("HEAD"), remap = false)
    private static void sscfe$acceptSoundKeyPress(ServerPlayer player, String key, boolean pressed,
                                                   CallbackInfo ci) {
        if (pressed && "key.shape-shifter-curse.make_sound".equals(key)) {
            FormSoundResponseService.respond(player);
        }
    }

    // Since core 1.9.3.16, hissing is a data-driven make_sound action too.
    @Inject(method = "triggerActive", at = @At("HEAD"), remap = false)
    private static void sscfe$beginSoundCall(ServerPlayer player, String key,
                                              CallbackInfoReturnable<Boolean> cir) {
        if ("key.shape-shifter-curse.make_sound".equals(key)) {
            FormSoundVolumePolicy.beginPlayerCall();
        }
    }

    @Inject(method = "triggerActive", at = @At("RETURN"), remap = false)
    private static void sscfe$respondToMakeSound(ServerPlayer player, String key,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if ("key.shape-shifter-curse.make_sound".equals(key)) {
            FormSoundVolumePolicy.endPlayerCall();
        }
    }
}
