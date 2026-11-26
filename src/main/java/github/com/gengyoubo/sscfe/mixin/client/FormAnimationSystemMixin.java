package github.com.gengyoubo.sscfe.mixin.client;

import github.com.gengyoubo.sscfe.client.SscfeClientConfig;
import github.com.gengyoubo.sscfe.interaction.AxolotlStackService;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.onixary.shapeShifterCurseForge.ShapeShifterCurseForge;
import net.onixary.shapeShifterCurseForge.client.render.FormAnimationSystem;
import net.onixary.shapeShifterCurseForge.form.FormManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FormAnimationSystem.class)
public abstract class FormAnimationSystemMixin {
    @Inject(method = "select", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sscfe$stackAnimation(Player player,
                                            CallbackInfoReturnable<FormAnimationSystem.Selection> cir) {
        if (!AxolotlStackService.isLinked(player)) return;
        String form = FormManager.current(player).id().getPath();
        boolean moving = !player.isPassenger() && player.walkAnimation.speed() > 0.01F;
        String animation = "axolotl_3".equals(form) ? (moving ? "axolotl_3_crawling" : "axolotl_3_crawling_idle")
                : "axolotl_2".equals(form) ? (moving ? "axolotl_2_crawling_new" : "axolotl_2_crawling_idle_new")
                : "axolotl_2_swimming_idle";
        cir.setReturnValue(FormAnimationSystem.Selection.of(animation));
    }

    @Inject(method = "preferredResource", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sscfe$preferExtensionAnimation(String animationFile, ResourceLocation legacy,
                                                       CallbackInfoReturnable<ResourceLocation> cir) {
        if (!SscfeClientConfig.PREFER_NEW_ANIMATIONS.get()) {
            return;
        }
        ResourceLocation modern = ResourceLocation.fromNamespaceAndPath(ShapeShifterCurseForge.RESOURCE_NAMESPACE,
                "player_animation/new/" + animationFile + ".json");
        if (Minecraft.getInstance().getResourceManager().getResource(modern).isPresent()) {
            cir.setReturnValue(modern);
        }
    }
}
