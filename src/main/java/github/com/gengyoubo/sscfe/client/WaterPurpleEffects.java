package github.com.gengyoubo.sscfe.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.water.WaterCurseNetwork;
import github.com.gengyoubo.sscfe.water.WaterPurpleRules;
import github.com.gengyoubo.sscfe.water.WaterPurpleAnimationTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded local geometry and particles; global packets carry timing, never audio files. */
@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, value = Dist.CLIENT)
public final class WaterPurpleEffects {
    private static final Map<UUID, Presentation> ACTIVE = new HashMap<>();
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.05F, 0.45F, 1F), 2F);
    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.1F, 0.9F, 1F), 1.5F);
    private static ResourceLocation currentDimension;
    private static long clientTicks;

    public static void accept(WaterCurseNetwork.Effect packet) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Presentation previous = ACTIVE.get(packet.caster());
        if (packet.stage() == WaterCurseNetwork.Stage.CANCEL) {
            if (previous != null && previous.packet.startedAt() == packet.startedAt()) {
                previous.stop(); ACTIVE.remove(packet.caster());
            }
            return;
        }
        boolean sameCast = previous != null && previous.packet.startedAt() == packet.startedAt()
                && previous.packet.dimension().equals(packet.dimension());
        if (sameCast && previous.packet.stage() == WaterCurseNetwork.Stage.RELEASE
                && packet.stage() == WaterCurseNetwork.Stage.CHARGE) return;
        if (sameCast && previous.packet.stage() == packet.stage()) {
            previous.packet = packet; previous.receivedAt = clientTicks; return;
        }
        if (previous != null) previous.stop();
        Presentation next = new Presentation(packet);
        // A late join cannot seek streaming OGG. Keep its visuals synchronized without replaying the song from zero.
        next.musicEligible = packet.special() && packet.stage() == WaterCurseNetwork.Stage.CHARGE
                && packet.serverNow() - packet.startedAt() <= 5;
        ACTIVE.put(packet.caster(), next);
        if (localCasting()) lockView(next);
    }

    public static boolean localCasting() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        Presentation p = ACTIVE.get(mc.player.getUUID());
        return p != null && p.packet.stage() == WaterCurseNetwork.Stage.CHARGE
                && p.packet.dimension().equals(mc.level.dimension().location())
                && clientTicks - p.receivedAt <= 60 && mc.player.isAlive();
    }

    /** Animation time is tied to the server's casting timeline. */
    public static WaterPurpleAnimationTimeline.Sample animation(UUID player, float partialTick) {
        var mc = Minecraft.getInstance();
        Presentation p = ACTIVE.get(player);
        return p == null || mc.level == null || !p.packet.dimension().equals(mc.level.dimension().location())
                ? null : WaterPurpleAnimationTimeline.sample(p.elapsed() + partialTick, p.packet.duration(),
                p.packet.stage() == WaterCurseNetwork.Stage.RELEASE);
    }

    private static void lockView(Presentation p) {
        var player = Minecraft.getInstance().player;
        if (player == null || !p.packet.caster().equals(player.getUUID())) return;
        Vec3 direction = p.packet.direction();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1D, 1D)));
        player.setYRot(yaw); player.setXRot(pitch); player.setYHeadRot(yaw); player.setYBodyRot(yaw);
        player.yRotO = yaw; player.xRotO = pitch;
        if (p.lockedPosition != null) player.setPos(p.lockedPosition.x, p.lockedPosition.y, p.lockedPosition.z);
        player.setDeltaMovement(Vec3.ZERO);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        ResourceLocation dimension = mc.level.dimension().location();
        if (currentDimension != null && !currentDimension.equals(dimension)) clear();
        currentDimension = dimension;
        if (mc.isPaused()) return;
        clientTicks++;
        if (!mc.player.isAlive()) { clear(); return; }
        ACTIVE.values().removeIf(p -> {
            long since = clientTicks - p.receivedAt;
            if ((p.packet.stage() == WaterCurseNetwork.Stage.CHARGE && since > 60)
                    || (p.packet.stage() == WaterCurseNetwork.Stage.RELEASE && since > 40)) { p.stop(); return true; }
            p.music();
            if (localCasting() && p.packet.caster().equals(mc.player.getUUID())) lockView(p);
            if (p.packet.dimension().equals(dimension)) p.particles();
            return false;
        });
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    private static void clear() { ACTIVE.values().forEach(Presentation::stop); ACTIVE.clear(); currentDimension = null; }

    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().getPath().equals("hotbar") || !localCasting()) return;
        var mc = Minecraft.getInstance();
        Presentation p = ACTIVE.get(mc.player.getUUID());
        double seconds = Math.max(0, p.packet.duration() - p.elapsed()) / 20D;
        event.getGuiGraphics().drawCenteredString(mc.font,
                Component.translatable("hud.sscfe.water_purple", String.format(java.util.Locale.ROOT, "%.1f", seconds)),
                event.getWindow().getGuiScaledWidth() / 2, event.getWindow().getGuiScaledHeight() / 2 + 28, 0x66CCFF);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;
        var poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poses.pushPose(); poses.translate(-camera.x, -camera.y, -camera.z);
        var buffer = mc.renderBuffers().bufferSource();
        var vertices = buffer.getBuffer(RenderType.lightning());
        for (Presentation p : ACTIVE.values()) {
            if (!p.packet.dimension().equals(mc.level.dimension().location())) continue;
            double elapsed = p.elapsed() + event.getPartialTick();
            Vec3 core = p.core(elapsed);
            if (core.distanceToSqr(camera) > 512D * 512D) continue;
            double growth = Math.min(1D, elapsed / 600D);
            double size = p.packet.stage() == WaterCurseNetwork.Stage.RELEASE
                    ? 2D + 6D * WaterPurpleRules.power(p.packet.duration()) : 0.4D + 5.6D * growth;
            sphere(vertices, poses.last().pose(), core, size, 25, 120, 255, 100);
            sphere(vertices, poses.last().pose(), core, size * 0.55D, 135, 235, 255, 200);
            ring(vertices, poses.last().pose(), core, p.side, p.up, size * 1.3D, elapsed * 0.04D);
            ring(vertices, poses.last().pose(), core, p.side, p.packet.direction(), size * 1.6D, -elapsed * 0.03D);
            if (p.packet.stage() == WaterCurseNetwork.Stage.CHARGE && elapsed < 400) {
                double separation = 3D * Math.max(0D, 1D - elapsed / 400D);
                sphere(vertices, poses.last().pose(), core.add(p.side.scale(separation)), 0.6D, 10, 200, 255, 180);
                sphere(vertices, poses.last().pose(), core.add(p.side.scale(-separation)), 0.6D, 30, 75, 255, 180);
            }
        }
        buffer.endBatch(RenderType.lightning()); poses.popPose();
    }

    private static void sphere(VertexConsumer out, Matrix4f matrix, Vec3 center, double radius, int red, int green, int blue, int alpha) {
        for (int latitude = 0; latitude < 8; latitude++) for (int longitude = 0; longitude < 16; longitude++) {
            double a = Math.PI * latitude / 8 - Math.PI / 2;
            double b = Math.PI * (latitude + 1) / 8 - Math.PI / 2;
            double c = Math.PI * 2 * longitude / 16, d = Math.PI * 2 * (longitude + 1) / 16;
            vertex(out, matrix, center.add(spherical(a, c).scale(radius)), red, green, blue, alpha);
            vertex(out, matrix, center.add(spherical(b, c).scale(radius)), red, green, blue, alpha);
            vertex(out, matrix, center.add(spherical(b, d).scale(radius)), red, green, blue, alpha);
            vertex(out, matrix, center.add(spherical(a, d).scale(radius)), red, green, blue, alpha);
        }
    }
    private static Vec3 spherical(double latitude, double longitude) {
        return new Vec3(Math.cos(latitude) * Math.cos(longitude), Math.sin(latitude), Math.cos(latitude) * Math.sin(longitude));
    }
    private static void ring(VertexConsumer out, Matrix4f matrix, Vec3 core, Vec3 side, Vec3 up, double radius, double rotation) {
        for (int i = 0; i < 48; i++) {
            double a = rotation + i * Math.PI * 2 / 48, b = rotation + (i + 1) * Math.PI * 2 / 48;
            Vec3 first = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 second = side.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
            vertex(out, matrix, core.add(first.scale(radius)), 70, 210, 255, 190);
            vertex(out, matrix, core.add(first.scale(radius + 0.12D)), 70, 210, 255, 190);
            vertex(out, matrix, core.add(second.scale(radius + 0.12D)), 70, 210, 255, 190);
            vertex(out, matrix, core.add(second.scale(radius)), 70, 210, 255, 190);
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 v, int red, int green, int blue, int alpha) {
        out.vertex(matrix, (float) v.x, (float) v.y, (float) v.z).color(red, green, blue, alpha).endVertex();
    }

    private static final class Presentation {
        WaterCurseNetwork.Effect packet;
        long receivedAt = clientTicks;
        final Vec3 side, up;
        final Vec3 lockedPosition;
        boolean musicEligible, musicAttempted;
        SoundInstance music;
        Presentation(WaterCurseNetwork.Effect packet) {
            this.packet = packet;
            var player = Minecraft.getInstance().player;
            lockedPosition = player != null && packet.caster().equals(player.getUUID()) ? player.position() : null;
            side = packet.direction().cross(Math.abs(packet.direction().y) > 0.95D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            up = side.cross(packet.direction()).normalize();
        }
        long elapsed() {
            return packet.stage() == WaterCurseNetwork.Stage.CHARGE
                    ? packet.serverNow() - packet.startedAt() + clientTicks - receivedAt : clientTicks - receivedAt;
        }
        Vec3 core(double elapsed) {
            if (packet.stage() == WaterCurseNetwork.Stage.CHARGE) return packet.origin().add(packet.direction().scale(3D));
            double range = WaterPurpleRules.range(packet.duration());
            return packet.origin().add(packet.direction().scale(Math.min(range, elapsed * 100D)));
        }
        void music() {
            if (music != null && !SscfeClientConfig.SPECIAL_MUSIC_ENABLED.get()) { stop(); return; }
            if (!musicEligible || musicAttempted || packet.stage() != WaterCurseNetwork.Stage.CHARGE
                    || elapsed() < SscfeClientConfig.MUSIC_START_DELAY_TICKS.get()) return;
            musicAttempted = true;
            if (!SscfeClientConfig.SPECIAL_MUSIC_ENABLED.get() || elapsed() >= packet.duration()) return;
            ResourceLocation id = ResourceLocation.tryParse(SscfeClientConfig.SPECIAL_SOUND_ID.get());
            var manager = Minecraft.getInstance().getSoundManager();
            var event = id == null ? null : manager.getSoundEvent(id);
            // The empty default sound definition deliberately provides a silent, clean fallback.
            if (event == null || event.getWeight() <= 0) return;
            music = new SimpleSoundInstance(id, SoundSource.MUSIC, SscfeClientConfig.SPECIAL_MUSIC_VOLUME.get().floatValue(),
                    1F, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0D, 0D, 0D, true);
            manager.play(music);
        }
        void stop() { if (music != null) { Minecraft.getInstance().getSoundManager().stop(music); music = null; } }
        void particles() {
            var mc = Minecraft.getInstance();
            double elapsed = elapsed();
            Vec3 core = core(elapsed);
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            if (core.distanceToSqr(camera) > 256D * 256D) return;
            double growth = Math.min(1D, elapsed / 600D);
            double radius = packet.stage() == WaterCurseNetwork.Stage.RELEASE
                    ? 2D + 6D * WaterPurpleRules.power(packet.duration()) : 1D + 10D * growth;
            int count = SscfeClientConfig.EFFECT_PARTICLES.get();
            for (int i = 0; i < count; i++) {
                double angle = elapsed * 0.08D + i * Math.PI * 2 / count;
                double height = (i % 8) * 0.65D;
                Vec3 radial = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                Vec3 point = core.add(radial.scale(radius)).add(0, height, 0);
                Vec3 velocity = radial.scale(-0.12D).add(packet.direction().scale(0.04D));
                mc.level.addParticle(i % 3 == 0 ? ParticleTypes.SPLASH : i % 2 == 0 ? BLUE : CYAN,
                        true, point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
            }
            // A vertical water column exposes the caster without transmitting their coordinates in chat.
            if (packet.stage() == WaterCurseNetwork.Stage.CHARGE && growth > 0.3D) {
                for (int i = 0; i < 8; i++) {
                    double angle = elapsed * 0.06D + i;
                    Vec3 point = packet.origin().add(Math.cos(angle) * 3D, i * 4D * growth, Math.sin(angle) * 3D);
                    mc.level.addParticle(CYAN, true, point.x, point.y, point.z, 0, 0.2D, 0);
                }
            }
        }
    }
    private WaterPurpleEffects() {}
}
