package github.com.gengyoubo.sscfe.showcase;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.sscfe.Sscfe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** Client half of the dev-only stack showcase: opens a flat world, frames the stack and takes screenshots. */
@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, value = Dist.CLIENT)
public final class StackShowcaseClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "sscfe-stack-showcase";
    /** {degrees around the stack relative to the base's facing, camera distance, camera height, name}. */
    private static final Object[][] SHOTS = {
            {90.0, 3.2, 0.9, "side"},
            {135.0, 3.2, 1.3, "rear-quarter"},
            {40.0, 3.0, 1.1, "front-quarter"},
            {90.0, 1.8, 0.6, "side-close"},
    };
    private static boolean worldRequested;
    private static int readyTicks = -1, worldTicks;

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!StackShowcaseServer.ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) openWorld(mc);
            return;
        }
        worldTicks++;
        if (mc.screen != null) mc.setScreen(null); // e.g. a form-selection screen on first join
        mc.options.hideGui = true;

        List<Player> stack = new ArrayList<>();
        for (int i = 0; i < StackShowcaseServer.PLAYERS; i++) {
            String name = StackShowcaseServer.NAME_PREFIX + i;
            mc.level.players().stream().filter(p -> p.getGameProfile().getName().equals(name)).findFirst()
                    .ifPresent(stack::add);
        }
        boolean ready = stack.size() == StackShowcaseServer.PLAYERS
                && stack.stream().skip(1).allMatch(Player::isPassenger);
        if (!ready) {
            if (worldTicks > 1200) {
                LOGGER.error("[stack-showcase] stack never became visible ({} players found)", stack.size());
                shoot(mc, "timeout");
                mc.stop();
            }
            return;
        }
        if (readyTicks < 0) {
            readyTicks = 0;
            for (int i = 1; i < stack.size(); i++) {
                Vec3 rel = stack.get(i).position().subtract(stack.get(0).position());
                LOGGER.info("[stack-showcase] client layer {} dx={} dy={} dz={} pose={}", i,
                        String.format("%.3f", rel.x), String.format("%.3f", rel.y), String.format("%.3f", rel.z),
                        stack.get(i).getPose());
            }
        }
        readyTicks++;
        // Let forms sync and animations settle, then spend 20 ticks per shot: frame, wait, capture.
        int phase = readyTicks - 80;
        if (phase < 0) return;
        int shot = phase / 20, step = phase % 20;
        if (shot >= SHOTS.length) { mc.stop(); return; }
        if (step == 0) frame(mc, stack.get(0), SHOTS[shot]);
        if (step == 15) shoot(mc, (String) SHOTS[shot][3]);
    }

    private static void openWorld(Minecraft mc) {
        worldRequested = true;
        try {
            if (mc.getLevelSource().levelExists(WORLD)) {
                try (var access = mc.getLevelSource().createAccess(WORLD)) { access.deleteLevel(); }
            }
        } catch (Exception e) {
            LOGGER.warn("[stack-showcase] could not delete old showcase world", e);
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void frame(Minecraft mc, Player base, Object[] shot) {
        double around = Math.toRadians(base.yBodyRot + (double) shot[0]);
        double distance = (double) shot[1], height = (double) shot[2];
        // Aim at the middle of the stack, slightly behind the base where the layers lean.
        Vec3 target = base.position().add(0, 0.5D, 0);
        Vec3 eye = target.add(-Math.sin(around) * distance, height - 0.5D, Math.cos(around) * distance);
        Vec3 look = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        mc.player.moveTo(eye.x, eye.y - mc.player.getEyeHeight(), eye.z, yaw, pitch);
        mc.player.setYHeadRot(yaw);
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "sscfe-stack-" + name + ".png", mc.getMainRenderTarget(),
                message -> LOGGER.info("[stack-showcase] {}", message.getString()));
    }

    private StackShowcaseClient() {}
}
