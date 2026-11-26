package github.com.gengyoubo.sscfe.client;

import com.mojang.blaze3d.platform.InputConstants;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.interaction.FormInteractionNetwork;
import github.com.gengyoubo.sscfe.water.AxolotlWaterService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, value = Dist.CLIENT)
public final class FormInteractionControls {
    private static final KeyMapping INTERACT = new KeyMapping("key.sscfe.form_interaction",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.sscfe");
    /** Hold and right-click a player to grab their tail (axolotl chain). */
    private static final KeyMapping CHAIN = new KeyMapping("key.sscfe.form_chain",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.sscfe");

    @Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            event.register(INTERACT);
            event.register(CHAIN);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightClick(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        boolean chain = CHAIN.isDown();
        if (!event.isUseItem() || !(INTERACT.isDown() || chain) || mc.player == null || mc.screen != null
                || !(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player target)
                || !AxolotlWaterService.isAxolotl(mc.player) || !AxolotlWaterService.isAxolotl(target)) return;
        // Cancel both hands so the same click cannot also use food or another item.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() == InteractionHand.MAIN_HAND) FormInteractionNetwork.interact(target.getId(), chain);
    }

    private FormInteractionControls() {}
}
