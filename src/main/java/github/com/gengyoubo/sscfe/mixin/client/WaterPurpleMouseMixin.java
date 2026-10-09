package github.com.gengyoubo.sscfe.mixin.client;

import github.com.gengyoubo.sscfe.client.WaterPurpleEffects;
import net.minecraft.client.MouseHandler;
import net.minecraft.util.SmoothDouble;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class WaterPurpleMouseMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow @Final private SmoothDouble smoothTurnX;
    @Shadow @Final private SmoothDouble smoothTurnY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void sscfe$lockWaterCast(CallbackInfo ci) {
        // Let MouseHandler consume deltas every frame to avoid a camera jump after unlocking.
        if (WaterPurpleEffects.localCasting()) {
            accumulatedDX = 0D; accumulatedDY = 0D;
            smoothTurnX.reset(); smoothTurnY.reset();
        }
    }
}
