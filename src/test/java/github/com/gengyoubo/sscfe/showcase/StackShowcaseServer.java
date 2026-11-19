package github.com.gengyoubo.sscfe.showcase;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.interaction.AxolotlStackService;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.onixary.shapeShifterCurseForge.form.FormManager;
import net.onixary.shapeShifterCurseForge.network.ModNetwork;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dev-only visual check (run with {@code gradlew runStackShowcase}): spawns ticking fake axolotl
 * players next to the real player on the integrated server and stacks them.
 */
@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID)
public final class StackShowcaseServer {
    static final boolean ENABLED = Boolean.getBoolean("sscfe.stackShowcase");
    static final int PLAYERS = 4;
    static final String NAME_PREFIX = "showcase-";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<ShowcasePlayer> FAKES = new ArrayList<>();
    private static int tick = -1;

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        ServerPlayer viewer = server.getPlayerList().getPlayers().stream()
                .filter(p -> !(p instanceof FakePlayer)).findFirst().orElse(null);
        if (viewer == null) return;
        tick++;
        if (tick == 40) spawn(server, viewer);
        if (tick == 60) stack();
        if (tick == 65 || tick == 120) {
            for (ShowcasePlayer fake : FAKES) {
                ModNetwork.sendFormSyncTo(fake, viewer);
                ModNetwork.sendSkinSyncTo(fake, viewer);
            }
        }
        if (tick > 60) for (ShowcasePlayer fake : FAKES) {
            // Keep the base facing a fixed direction so screenshots are reproducible.
            if (!fake.isPassenger()) { fake.setYRot(90F); fake.setYHeadRot(90F); fake.setYBodyRot(90F); }
        }
    }

    private static void spawn(MinecraftServer server, ServerPlayer viewer) {
        ServerLevel level = viewer.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        viewer.setGameMode(GameType.SPECTATOR);

        int bx = viewer.getBlockX() + 3, bz = viewer.getBlockZ();
        Vec3 base = new Vec3(bx + 0.5D, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz), bz + 0.5D);
        for (int i = 0; i < PLAYERS; i++) {
            ShowcasePlayer fake = new ShowcasePlayer(level, NAME_PREFIX + i);
            double angle = Math.PI * 2 * i / PLAYERS;
            Vec3 at = i == 0 ? base : base.add(Math.cos(angle) * 0.9D, 0, Math.sin(angle) * 0.9D);
            fake.moveTo(at.x, at.y, at.z, 90F, 0F);
            FormManager.setForm(fake, ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "axolotl_3"), false);
            // Clients only render players that are in their player-info list.
            server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fake)));
            level.addNewPlayer(fake);
            FAKES.add(fake);
        }
        LOGGER.info("[stack-showcase] spawned {} fake axolotls at {}", PLAYERS, base);
    }

    private static void stack() {
        ShowcasePlayer base = FAKES.get(0);
        for (int i = 1; i < FAKES.size(); i++) {
            boolean ok = AxolotlStackService.tryStack(FAKES.get(i), base);
            LOGGER.info("[stack-showcase] {} joins stack: {}", FAKES.get(i).getGameProfile().getName(), ok);
        }
        for (int i = 1; i < FAKES.size(); i++) {
            Vec3 rel = FAKES.get(i).position().subtract(base.position());
            LOGGER.info("[stack-showcase] layer {} offset dx={} dy={} dz={}", i,
                    String.format("%.3f", rel.x), String.format("%.3f", rel.y), String.format("%.3f", rel.z));
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (ShowcasePlayer fake : FAKES) AxolotlStackService.leave(fake);
        for (ShowcasePlayer fake : FAKES) fake.serverLevel().removePlayerImmediately(fake, Entity.RemovalReason.DISCARDED);
        FAKES.clear();
        tick = -1;
    }

    /** Forge FakePlayer.tick() is empty; run the normal player tick so physics and riding work. */
    private static final class ShowcasePlayer extends FakePlayer {
        ShowcasePlayer(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        }
        @Override public void tick() { doTick(); }
    }

    private StackShowcaseServer() {}
}
