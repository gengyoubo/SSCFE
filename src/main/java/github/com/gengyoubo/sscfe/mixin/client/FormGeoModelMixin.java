package github.com.gengyoubo.sscfe.mixin.client;

import github.com.gengyoubo.sscfe.client.SurfaceSprintOverlayAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.onixary.shapeShifterCurseForge.ShapeShifterCurseForge;
import net.onixary.shapeShifterCurseForge.client.render.BedrockAnimationPlayer;
import net.onixary.shapeShifterCurseForge.client.render.FormGeoAnimatable;
import net.onixary.shapeShifterCurseForge.client.render.FormGeoModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.core.animation.AnimationState;

@Mixin(FormGeoModel.class)
public abstract class FormGeoModelMixin {
    private static final ResourceLocation SSCFE_SURFACE_SPRINT_ANIMATION =
            ResourceLocation.fromNamespaceAndPath(ShapeShifterCurseForge.RESOURCE_NAMESPACE,
                    "player_animation/new/form_axolotl_3_new.animation.json");
    private static final String SSCFE_SURFACE_SPRINT_ID = "the_surface_sprint_begins";

    @Inject(method = "setCustomAnimations", at = @At("TAIL"), remap = false)
    private void sscfe$applySurfaceSprint(FormGeoAnimatable animatable, long instanceId,
                                          AnimationState<FormGeoAnimatable> animationState, CallbackInfo ci) {
        float time = ((SurfaceSprintOverlayAccess) (Object) animatable)
                .sscfe$surfaceSprintOverlayTime(animationState.getPartialTick());
        if (time >= 0.0F) {
            BedrockAnimationPlayer.applyAdditiveGeoRotation((FormGeoModel) (Object) this,
                    SSCFE_SURFACE_SPRINT_ANIMATION, SSCFE_SURFACE_SPRINT_ID, time);
        }
    }

    @Inject(method = "applyAxolotlElytraTail", at = @At("TAIL"), remap = false)
    private void sscfe$applyElytraSurfaceSprint(Player player, float partialTick, boolean inventoryPreview,
                                                CallbackInfo ci) {
        BedrockAnimationPlayer.applyAdditiveGeoRotation((FormGeoModel) (Object) this,
                SSCFE_SURFACE_SPRINT_ANIMATION, SSCFE_SURFACE_SPRINT_ID, 0.75F);
    }
}
