package github.com.gengyoubo.sscfe.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.sscfe.client.SpiderSaddleRenderer;
import net.onixary.shapeShifterCurseForge.client.render.FormGeoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;

/** Records the animated abdomen bone so the saddle can follow it after the form model is drawn. */
@Mixin(FormGeoRenderer.class)
public abstract class SpiderSaddleBoneMixin {
    @Inject(method = "renderCubesOfBone", at = @At("HEAD"), remap = false)
    private void sscfe$captureAbdomen(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight,
                                      int packedOverlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (SpiderSaddleRenderer.ABDOMEN_BONE.equals(bone.getName())) {
            SpiderSaddleRenderer.capture(((FormGeoRenderer) (Object) this).getAnimatable().getPlayer(), poseStack);
        }
    }
}
