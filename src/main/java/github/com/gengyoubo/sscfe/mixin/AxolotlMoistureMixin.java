package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.water.AxolotlWaterService;
import net.minecraft.world.entity.player.Player;
import net.onixary.shapeShifterCurseForge.power.FormPowerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FormPowerEvents.class, remap = false)
public abstract class AxolotlMoistureMixin {
    @Inject(method = "tickCustomWaterBreathing", at = @At("HEAD"), cancellable = true)
    private static void sscfe$useStoredWater(Player player, CallbackInfo callback) {
        if (AxolotlWaterService.protectMoisture(player)) callback.cancel();
    }
}
