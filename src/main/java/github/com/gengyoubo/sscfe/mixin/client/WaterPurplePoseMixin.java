package github.com.gengyoubo.sscfe.mixin.client;

import github.com.gengyoubo.sscfe.client.WaterPurpleEffects;
import net.minecraft.resources.ResourceLocation;
import net.onixary.shapeShifterCurseForge.client.render.BedrockAnimationPlayer;
import net.onixary.shapeShifterCurseForge.client.render.FormGeoAnimatable;
import net.onixary.shapeShifterCurseForge.client.render.FormGeoModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.core.animation.AnimationState;

/** Use the existing author-provided cast clips without editing those resources. */
@Mixin(FormGeoModel.class)
public abstract class WaterPurplePoseMixin {
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath("shape-shifter-curse",
            "player_animation/new/mizu_mulasaki.animation.json");

    @Inject(method = "setCustomAnimations", at = @At("TAIL"), remap = false)
    private void sscfe$castPose(FormGeoAnimatable animatable, long instanceId,
                                AnimationState<FormGeoAnimatable> animationState, CallbackInfo ci) {
        if (animatable.getPlayer() == null || animatable.isInventoryPreview()) return;
        var id = animatable.getPlayer().getUUID();
        var selection = WaterPurpleEffects.animation(id, animationState.getPartialTick());
        if (selection == null) return;
        var model = (FormGeoModel) (Object) this;
        for (String name : new String[]{"bipedHead", "bipedBody", "bipedLeftArm", "bipedRightArm", "bipedLeftLeg", "bipedRightLeg"}) {
            var sample = BedrockAnimationPlayer.sampleBone(ANIMATION, selection.clip(), name, selection.seconds());
            if (sample == null) continue;
            model.getBone(name).ifPresent(bone -> {
                float degrees = (float) Math.PI / 180F;
                bone.setRotX(sample.rotX() * degrees);
                bone.setRotY(-sample.rotY() * degrees);
                bone.setRotZ(-sample.rotZ() * degrees);
                bone.setPosX(-sample.posX()); bone.setPosY(-sample.posY()); bone.setPosZ(-sample.posZ());
            });
        }
    }
}
