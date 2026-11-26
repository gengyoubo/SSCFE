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
    private static final String WORLD = "sscfe-" + StackShowcaseServer.SCENARIO + "-showcase";
    /** {degrees around the stack relative to the base's facing, camera distance, camera height, name}. */
    private static final Object[][] SHOTS = {
            {90.0, 3.2, 0.9, "side"},
            {135.0, 3.2, 1.3, "rear-quarter"},
            {40.0, 3.0, 1.1, "front-quarter"},
            {90.0, 1.8, 0.6, "side-close"},
            // Idle burst from directly behind, where the abdomen's sideways sway is visible; 6 ticks apart.
            {180.0, 1.4, 1.0, "back-0"}, {180.0, 1.4, 1.0, "back-1"}, {180.0, 1.4, 1.0, "back-2"},
            {180.0, 1.4, 1.0, "back-3"}, {180.0, 1.4, 1.0, "back-4"}, {180.0, 1.4, 1.0, "back-5"},
            {180.0, 1.4, 1.0, "back-6"}, {180.0, 1.4, 1.0, "back-7"},
    };
    /**
     * Chain scenario: {..., target} where target 0 frames the whole chain and 1 the first link.
     * Shots named "drag-" are taken while the head crawls forward (the client starts it).
     */
    private static final Object[][] CHAIN_SHOTS = {
            {90.0, 6.0, 1.6, "side", 0}, {150.0, 5.5, 2.2, "rear-quarter", 0}, {90.0, 2.4, 0.5, "link-side", 1},
            {35.0, 2.6, 1.0, "link-front", 1}, {90.0, 3.0, 4.0, "top", 0}, {90.0, 0.3, 2.6, "link-top", 1},
            {90.0, 6.0, 1.6, "drag-side", 0}, {150.0, 5.0, 2.0, "drag-rear-quarter", 0},
            {90.0, 2.6, 0.5, "drag-link-side", 1}, {90.0, 3.0, 4.0, "drag-top", 0},
            // The head keeps crawling and turns; seen from above, every joint should stay within the tail angle.
            {90.0, 0.5, 10.0, "turn-top-0", 0}, {90.0, 0.5, 10.0, "turn-top-1", 0},
            {90.0, 0.5, 10.0, "turn-top-2", 0}, {90.0, 0.5, 10.0, "turn-top-3", 0},
            {150.0, 6.0, 3.0, "turn-quarter", 0},
    };
    private static final Object[][] ACTIVE_SHOTS = StackShowcaseServer.CHAIN ? CHAIN_SHOTS : SHOTS;
    private static int shotDuration(int shot) {
        return ((String) ACTIVE_SHOTS[shot][3]).startsWith("back-") ? 6 : 20;
    }
    private static boolean worldRequested;
    private static int readyTicks = -1, worldTicks;
    /** Spider scenario: photograph the saddled spider alone first, then ask the server to mount the rider. */
    private static boolean soloPhase = StackShowcaseServer.SPIDER;

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
                && (soloPhase || stack.stream().skip(1).allMatch(Player::isPassenger));
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
        if (StackShowcaseServer.SPIDER && !soloPhase && readyTicks <= 120) {
            // Measure the idle sway: the animated saddle top, body yaws and the rider's logical offset.
            Player spider = stack.get(0), rider = stack.get(1);
            Vec3 seat = github.com.gengyoubo.sscfe.client.SpiderSaddleRenderer.animatedSeat(spider);
            Vec3 rel = rider.position().subtract(spider.position());
            if (seat != null) LOGGER.info("[stack-showcase] sway t={} seat=({}, {}, {}) riderRel=({}, {}, {}) spiderBody={} swayDeg={}",
                    readyTicks, f(seat.x), f(seat.y), f(seat.z), f(rel.x), f(rel.y), f(rel.z),
                    f(spider.yBodyRot), f(github.com.gengyoubo.sscfe.client.SpiderSaddleRenderer.swayDegrees(spider)));
        }
        // Let forms sync and animations settle, then frame, wait and capture each shot.
        int phase = readyTicks - 80;
        if (phase < 0) return;
        int shot = 0, step = phase;
        while (shot < ACTIVE_SHOTS.length && step >= shotDuration(shot)) step -= shotDuration(shot++);
        if (shot >= ACTIVE_SHOTS.length) {
            if (soloPhase) {
                soloPhase = false;
                readyTicks = -1;
                StackShowcaseServer.requestMount = true;
            } else {
                mc.stop();
            }
            return;
        }
        if (StackShowcaseServer.CHAIN && step == 0 && ((String) ACTIVE_SHOTS[shot][3]).startsWith("drag-")
                && StackShowcaseServer.headSpeed == 0) {
            StackShowcaseServer.headSpeed = 0.09D;
        }
        if (StackShowcaseServer.CHAIN && step == 0 && ((String) ACTIVE_SHOTS[shot][3]).startsWith("turn-")
                && StackShowcaseServer.headTurn == 0) {
            StackShowcaseServer.headTurn = 3.5F;
        }
        if (StackShowcaseServer.CHAIN && step == shotDuration(shot) - 5) logChain(stack, (String) ACTIVE_SHOTS[shot][3]);
        if (StackShowcaseServer.CHAIN) frameChain(mc, stack, ACTIVE_SHOTS[shot]);
        else if (step == 0) frame(mc, stack.get(0), ACTIVE_SHOTS[shot]);
        if (step == shotDuration(shot) - 5 + (shotDuration(shot) == 6 ? 4 : 0)) shoot(mc, (soloPhase ? "solo-" : "") + ACTIVE_SHOTS[shot][3]);
    }

    private static String f(double value) {
        return String.format("%.4f", value);
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

    /** Re-framed every tick so moving chains stay in view; aims at the chain or its first link. */
    private static void frameChain(Minecraft mc, List<Player> chain, Object[] shot) {
        Player head = chain.get(0);
        Player end = (int) shot[4] == 1 ? chain.get(1) : chain.get(chain.size() - 1);
        Vec3 target = head.position().add(end.position()).scale(0.5D).add(0, 0.3D, 0);
        // A fixed reference (the head's starting yaw) keeps turning shots comparable.
        double around = Math.toRadians(90.0D + (double) shot[0]);
        double distance = (double) shot[1], height = (double) shot[2];
        Vec3 eye = target.add(-Math.sin(around) * distance, height, Math.cos(around) * distance);
        Vec3 look = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        mc.player.moveTo(eye.x, eye.y - mc.player.getEyeHeight(), eye.z, yaw, pitch);
        mc.player.setYHeadRot(yaw);
    }

    private static void logChain(List<Player> chain, String shot) {
        StringBuilder line = new StringBuilder("[stack-showcase] chain ").append(shot);
        for (int i = 1; i < chain.size(); i++) {
            Player front = chain.get(i - 1), follower = chain.get(i);
            double dx = follower.getX() - front.getX(), dz = follower.getZ() - front.getZ();
            double yaw = Math.toRadians(front.yBodyRot), backX = Math.sin(yaw), backZ = -Math.cos(yaw);
            double angle = Math.toDegrees(Math.atan2(backX * dz - backZ * dx, backX * dx + backZ * dz));
            line.append(String.format(" link%d=%.3f dy=%.3f angle=%.1f", i, Math.sqrt(dx * dx + dz * dz),
                    follower.getY() - front.getY(), angle));
        }
        LOGGER.info(line.toString());
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "sscfe-" + StackShowcaseServer.SCENARIO + "-" + name + ".png", mc.getMainRenderTarget(),
                message -> LOGGER.info("[stack-showcase] {}", message.getString()));
    }

    private StackShowcaseClient() {}
}
