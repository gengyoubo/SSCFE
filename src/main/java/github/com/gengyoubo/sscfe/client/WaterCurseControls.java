package github.com.gengyoubo.sscfe.client;

import com.mojang.blaze3d.platform.InputConstants;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.water.WaterCurseItem;
import github.com.gengyoubo.sscfe.water.WaterCurseNetwork;
import github.com.gengyoubo.sscfe.water.WaterCurseService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, value = Dist.CLIENT)
public final class WaterCurseControls {
    private static final KeyMapping CAST = new KeyMapping("key.sscfe.water_curse", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "key.categories.sscfe");

    @Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(CAST); }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        while (CAST.consumeClick()) {
            if (mc.player == null || mc.level == null || mc.screen != null) continue;
            var stack = WaterCurseService.accessory(mc.player);
            if (stack.isEmpty() || WaterCurseItem.mode(stack) != WaterCurseItem.Mode.PURPLE) continue;
            WaterCurseNetwork.input(WaterPurpleEffects.localCasting() ? WaterCurseNetwork.Action.CANCEL : WaterCurseNetwork.Action.START,
                    WaterCurseItem.special(stack) ? SscfeClientConfig.SPECIAL_CHARGE_TICKS.get() : SscfeClientConfig.WATER_PURPLE_CAST_TICKS.get());
        }
    }

    @SubscribeEvent public static void rightClick(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !event.isUseItem() || !mc.player.getMainHandItem().isEmpty()
                || !mc.player.getOffhandItem().isEmpty() || WaterCurseService.accessory(mc.player).isEmpty()) return;
        event.setCanceled(true); event.setSwingHand(false);
        WaterCurseNetwork.input(WaterCurseNetwork.Action.CONFIGURE, 0);
    }

    @SubscribeEvent public static void movement(MovementInputUpdateEvent event) {
        if (!WaterPurpleEffects.localCasting()) return;
        var input = event.getInput();
        input.forwardImpulse = 0F; input.leftImpulse = 0F;
        input.up = false; input.down = false; input.left = false; input.right = false;
        input.jumping = false; input.shiftKeyDown = false;
        event.getEntity().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    private WaterCurseControls() {}
}
