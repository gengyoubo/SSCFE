package github.com.gengyoubo.sscfe.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.water.WaterCurseNetwork;
import github.com.gengyoubo.sscfe.water.WaterPurpleRules;
import github.com.gengyoubo.sscfe.water.WaterPurpleAnimationTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded local geometry and particles; global packets carry timing, never audio files. */
@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, value = Dist.CLIENT)
public final class WaterPurpleEffects {
    private record CastKey(UUID caster, ResourceLocation dimension, long startedAt) {}
    private static final Map<CastKey, Presentation> ACTIVE = new HashMap<>();
    private static final Map<UUID, BurstPresentation> BURSTS = new HashMap<>();
    // Music fades independently of the projectile and can overlap a new creative cast.
    private static final Set<WaterPurpleMusicSound> PLAYING_MUSIC = new HashSet<>();
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.05F, 0.45F, 1F), 2F);
    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.1F, 0.9F, 1F), 1.5F);
    private static ResourceLocation currentDimension;
    private static long clientTicks;

    public static void accept(WaterCurseNetwork.Effect packet) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        CastKey key = new CastKey(packet.caster(), packet.dimension(), packet.startedAt());
        Presentation previous = ACTIVE.get(key);
        if (packet.stage() == WaterCurseNetwork.Stage.CANCEL) {
            if (previous != null) {
                previous.stop(); ACTIVE.remove(key);
            }
            return;
        }
        if (previous != null && previous.packet.stage() == WaterCurseNetwork.Stage.RELEASE
                && packet.stage() == WaterCurseNetwork.Stage.CHARGE) return;
        if (previous != null) {
            boolean justReleased = previous.packet.stage() != WaterCurseNetwork.Stage.RELEASE
                    && packet.stage() == WaterCurseNetwork.Stage.RELEASE;
            previous.packet = packet;
            if (justReleased || packet.stage() == WaterCurseNetwork.Stage.CHARGE) previous.receivedAt = clientTicks;
            if (justReleased) previous.releaseMusic();
            return;
        }
        // Keep already released projectiles visible when this player begins another cast.
        ACTIVE.values().removeIf(p -> {
            if (p.packet.caster().equals(packet.caster()) && p.packet.stage() == WaterCurseNetwork.Stage.CHARGE) {
                p.stop(); return true;
            }
            return false;
        });
        Presentation next = new Presentation(packet);
        // A late join cannot seek streaming OGG. Keep its visuals synchronized without replaying the song from zero.
        next.musicEligible = packet.special() && packet.stage() == WaterCurseNetwork.Stage.CHARGE
                && packet.serverNow() - packet.startedAt() <= 5;
        ACTIVE.put(key, next);
        if (packet.stage() == WaterCurseNetwork.Stage.CHARGE && localCasting()) lockView(next);
    }

    private static Presentation latestPresentation(UUID caster) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Presentation latest = null;
        for (Presentation p : ACTIVE.values()) {
            if (p.packet.caster().equals(caster) && p.packet.dimension().equals(mc.level.dimension().location())
                    && (latest == null || p.packet.startedAt() > latest.packet.startedAt())) latest = p;
        }
        return latest;
    }

    public static boolean localCasting() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        Presentation p = latestPresentation(mc.player.getUUID());
        return p != null && p.packet.stage() == WaterCurseNetwork.Stage.CHARGE
                && p.packet.dimension().equals(mc.level.dimension().location())
                && clientTicks - p.receivedAt <= 60 && mc.player.isAlive();
    }

    public static void acceptFlight(WaterCurseNetwork.Flight flight) {
        var packet = flight.effect();
        CastKey key = new CastKey(packet.caster(), packet.dimension(), packet.startedAt());
        Presentation p = ACTIVE.get(key);
        if (p != null && p.serverControlled && packet.serverNow() < p.flightServerNow) return;
        if (flight.finished()) {
            if (p != null) { p.stop(); ACTIVE.remove(key); }
            return;
        }
        if (p == null || p.packet.stage() != WaterCurseNetwork.Stage.RELEASE) {
            accept(packet);
            p = ACTIVE.get(key);
        }
        if (p != null) {
            p.serverControlled = true;
            p.flightDistance = flight.distance(); p.flightMoving = flight.moving();
            p.flightReceivedAt = clientTicks; p.flightServerNow = packet.serverNow();
        }
    }

    public static void acceptBurst(WaterCurseNetwork.Burst packet) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !packet.dimension().equals(mc.level.dimension().location())) return;
        if (packet.finished()) { BURSTS.remove(packet.id()); return; }
        BURSTS.compute(packet.id(), (id, previous) -> {
            if (previous == null) return new BurstPresentation(packet);
            previous.packet = packet; previous.receivedAt = clientTicks; return previous;
        });
    }

    /** Animation time is tied to the server's casting timeline. */
    public static WaterPurpleAnimationTimeline.Sample animation(UUID player, float partialTick) {
        var mc = Minecraft.getInstance();
        Presentation p = latestPresentation(player);
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
                    || (p.packet.stage() == WaterCurseNetwork.Stage.RELEASE
                    && (p.serverControlled ? clientTicks - p.flightReceivedAt > 200
                    : since >= WaterPurpleRules.flightTicks(p.packet.duration())))) { p.stop(); return true; }
            p.music();
            if (p.packet.stage() == WaterCurseNetwork.Stage.CHARGE
                    && localCasting() && p.packet.caster().equals(mc.player.getUUID())) lockView(p);
            if (p.packet.dimension().equals(dimension)) p.particles();
            return false;
        });
        BURSTS.values().removeIf(p -> {
            if (clientTicks - p.receivedAt > 200 || p.age(0) >= 60) return true;
            p.particles(); return false;
        });
        PLAYING_MUSIC.removeIf(sound -> {
            if (!SscfeClientConfig.SPECIAL_MUSIC_ENABLED.get() || sound.isStopped() || sound.fadeFinished()) {
                stopSound(sound); return true;
            }
            return false;
        });
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    private static void clear() {
        ACTIVE.values().forEach(Presentation::stop);
        ACTIVE.clear();
        BURSTS.clear();
        PLAYING_MUSIC.forEach(WaterPurpleEffects::stopSound);
        PLAYING_MUSIC.clear();
        currentDimension = null;
    }

    private static void stopSound(WaterPurpleMusicSound sound) {
        sound.stopImmediately();
        Minecraft.getInstance().getSoundManager().stop(sound);
    }

    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().getPath().equals("hotbar") || !localCasting()) return;
        var mc = Minecraft.getInstance();
        Presentation p = latestPresentation(mc.player.getUUID());
        double seconds = Math.max(0, p.packet.duration() - p.elapsed()) / 20D;
        event.getGuiGraphics().drawCenteredString(mc.font,
                Component.translatable("hud.sscfe.water_purple", String.format(java.util.Locale.ROOT, "%.1f", seconds)),
                event.getWindow().getGuiScaledWidth() / 2, event.getWindow().getGuiScaledHeight() / 2 + 28, 0x66CCFF);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || (ACTIVE.isEmpty() && BURSTS.isEmpty())) return;
        var poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poses.pushPose(); poses.translate(-camera.x, -camera.y, -camera.z);
        var buffer = mc.renderBuffers().bufferSource();
        var smoke = buffer.getBuffer(WaterPurpleAuraRenderer.SMOKE);
        double renderDistance = SscfeClientConfig.EFFECT_RENDER_DISTANCE.get();
        for (Presentation p : ACTIVE.values()) {
            if (p.packet.stage() != WaterCurseNetwork.Stage.CHARGE
                    || !p.packet.dimension().equals(mc.level.dimension().location())
                    || p.packet.casterPosition().distanceToSqr(camera) > renderDistance * renderDistance) continue;
            WaterPurpleAuraRenderer.render(smoke, poses.last().pose(), event.getCamera(), p.packet.casterPosition(),
                    p.elapsed() + event.getPartialTick(), p.packet.duration(), p.auraSeed());
        }
        for (BurstPresentation burst : BURSTS.values()) {
            if (!burst.packet.dimension().equals(mc.level.dimension().location())
                    || burst.packet.center().distanceToSqr(camera) > renderDistance * renderDistance) continue;
            burst.render(smoke, poses.last().pose(), event.getCamera(), event.getPartialTick());
        }
        // Finish each render type before acquiring the next fallback buffer.
        buffer.endBatch(WaterPurpleAuraRenderer.SMOKE);
        var vertices = buffer.getBuffer(RenderType.lightning());
        for (Presentation p : ACTIVE.values()) {
            if (!p.packet.dimension().equals(mc.level.dimension().location())) continue;
            double elapsed = p.elapsed() + event.getPartialTick();
            Vec3 core = p.core(elapsed);
            if (core.distanceToSqr(camera) > renderDistance * renderDistance) continue;
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
        ring(out, matrix, core, side, up, radius, rotation, 1D);
    }
    private static void ring(VertexConsumer out, Matrix4f matrix, Vec3 core, Vec3 side, Vec3 up, double radius, double rotation, double fade) {
        int alpha = (int) (190D * fade);
        for (int i = 0; i < 48; i++) {
            double a = rotation + i * Math.PI * 2 / 48, b = rotation + (i + 1) * Math.PI * 2 / 48;
            Vec3 first = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 second = side.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
            double width = Math.max(0.12D, radius * 0.025D);
            vertex(out, matrix, core.add(first.scale(radius)), 70, 210, 255, alpha);
            vertex(out, matrix, core.add(first.scale(radius + width)), 70, 210, 255, alpha);
            vertex(out, matrix, core.add(second.scale(radius + width)), 70, 210, 255, alpha);
            vertex(out, matrix, core.add(second.scale(radius)), 70, 210, 255, alpha);
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 v, int red, int green, int blue, int alpha) {
        out.vertex(matrix, (float) v.x, (float) v.y, (float) v.z).color(red, green, blue, alpha).endVertex();
    }

    private static final class BurstPresentation {
        WaterCurseNetwork.Burst packet;
        long receivedAt = clientTicks;
        BurstPresentation(WaterCurseNetwork.Burst packet) { this.packet = packet; }
        double age(float partial) {
            double next = packet.age() + Math.min(5D, clientTicks - receivedAt + partial);
            return packet.age() <= 30 ? Math.min(30D, next) : Math.min(60D, next);
        }
        double radius(double age) {
            double power = packet.visualRadius() / 64D;
            if (age < 4D) return Math.max(0.4D, (8D - age * 1.5D) * power);
            double expansion = Math.min(Math.min(1D, (age - 4D) / 26D), packet.workProgress());
            return packet.visualRadius() * Math.max(0.025D, expansion);
        }
        double opacity(double age) { return age <= 30D ? 1D : Math.max(0D, (60D - age) / 30D); }
        void render(VertexConsumer out, Matrix4f matrix, net.minecraft.client.Camera camera, float partial) {
            double age = age(partial), radius = radius(age), fade = opacity(age);
            Vec3 center = packet.center();
            sphere(out, matrix, center, radius, 30, 115, 255, (int) (65 * fade));
            sphere(out, matrix, center, radius * 0.8D, 90, 195, 255, (int) (32 * fade));
            if (age >= 4D && age < 9D)
                sphere(out, matrix, center, Math.max(radius, packet.visualRadius() * 0.18D), 195, 245, 255,
                        (int) (180D * (9D - age) / 5D));
            if (age < 4D) {
                ring(out, matrix, center, new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius * 1.5D, age * 0.2D);
                return;
            }
            var cameraRight = new Vector3f(1, 0, 0).rotate(camera.rotation());
            var cameraUp = new Vector3f(0, 1, 0).rotate(camera.rotation());
            Vec3 right = new Vec3(cameraRight.x, cameraRight.y, cameraRight.z);
            Vec3 up = new Vec3(cameraUp.x, cameraUp.y, cameraUp.z);
            int count = center.distanceToSqr(camera.getPosition()) < 128D * 128D ? 24 : 12;
            for (int i = 0; i < count; i++) {
                double y = 1D - 2D * (i + 0.5D) / count;
                double theta = i * 2.39996D + age * 0.018D;
                Vec3 radial = new Vec3(Math.sqrt(1D - y * y) * Math.cos(theta), y, Math.sqrt(1D - y * y) * Math.sin(theta));
                WaterPurpleAuraRenderer.puff(out, matrix, center.add(radial.scale(radius)), right, up,
                        Math.max(0.5D, radius * 0.15D), Math.max(0.4D, radius * 0.1D), 125, 205, 255, (int) (85 * fade));
            }
            ring(out, matrix, center, new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius, age * 0.025D, fade);
            ring(out, matrix, center, new Vec3(1, 0, 0), new Vec3(0, 1, 0), radius * 0.98D, -age * 0.03D, fade);
        }
        void particles() {
            var mc = Minecraft.getInstance();
            Vec3 center = packet.center(), camera = mc.gameRenderer.getMainCamera().getPosition();
            double limit = SscfeClientConfig.PARTICLE_RENDER_DISTANCE.get();
            if (center.distanceToSqr(camera) > limit * limit) return;
            double age = age(0), radius = radius(age), fade = opacity(age);
            int count = (int) (SscfeClientConfig.EFFECT_PARTICLES.get() * fade);
            for (int i = 0; i < count; i++) {
                double y = 1D - 2D * (i + 0.5D) / Math.max(1, count), theta = i * 2.39996D + clientTicks * 0.12D;
                Vec3 radial = new Vec3(Math.sqrt(1D - y * y) * Math.cos(theta), y, Math.sqrt(1D - y * y) * Math.sin(theta));
                Vec3 point = center.add(radial.scale(radius));
                Vec3 speed = radial.scale(age < 4D ? -0.15D : 0.08D).add(0, age > 30D ? 0.05D : 0D, 0);
                mc.level.addParticle(i % 4 == 0 ? ParticleTypes.CLOUD : i % 2 == 0 ? BLUE : CYAN,
                        true, point.x, point.y, point.z, speed.x, speed.y, speed.z);
            }
        }
    }

    private static final class Presentation {
        WaterCurseNetwork.Effect packet;
        long receivedAt = clientTicks;
        final Vec3 side, up;
        final Vec3 lockedPosition;
        boolean musicEligible, musicAttempted;
        boolean serverControlled, flightMoving;
        double flightDistance;
        long flightReceivedAt, flightServerNow;
        WaterPurpleMusicSound music;
        Presentation(WaterCurseNetwork.Effect packet) {
            this.packet = packet;
            var player = Minecraft.getInstance().player;
            lockedPosition = player != null && packet.caster().equals(player.getUUID()) ? packet.casterPosition() : null;
            side = packet.direction().cross(Math.abs(packet.direction().y) > 0.95D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            up = side.cross(packet.direction()).normalize();
        }
        long elapsed() {
            return packet.stage() == WaterCurseNetwork.Stage.CHARGE
                    ? packet.serverNow() - packet.startedAt() + clientTicks - receivedAt : clientTicks - receivedAt;
        }
        double flightElapsed() {
            return serverControlled ? flightDistance / WaterPurpleRules.SPEED_BLOCKS_PER_TICK
                    + (flightMoving ? Math.min(5L, clientTicks - flightReceivedAt) : 0D) : elapsed();
        }
        Vec3 core(double elapsed) {
            if (packet.stage() == WaterCurseNetwork.Stage.CHARGE) return packet.origin();
            double flight = serverControlled ? flightElapsed() + (flightMoving ? elapsed - Math.floor(elapsed) : 0D) : elapsed;
            return packet.origin().add(packet.direction().scale(WaterPurpleRules.travelDistance(packet.duration(), flight)));
        }
        long auraSeed() { return packet.caster().getLeastSignificantBits() ^ packet.startedAt(); }
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
            music = new WaterPurpleMusicSound(id, SscfeClientConfig.SPECIAL_MUSIC_VOLUME.get().floatValue(),
                    SscfeClientConfig.MUSIC_FADE_IN_TICKS.get(), SscfeClientConfig.MUSIC_RELEASE_HOLD_TICKS.get(),
                    SscfeClientConfig.MUSIC_FADE_OUT_TICKS.get(), () -> clientTicks);
            PLAYING_MUSIC.add(music);
            manager.play(music);
        }
        void releaseMusic() {
            if (music != null) {
                music.release();
                // Keep the stream in PLAYING_MUSIC so visual cleanup cannot cut off the fade.
                music = null;
            }
        }
        void stop() {
            if (music != null) { stopSound(music); PLAYING_MUSIC.remove(music); music = null; }
        }
        void particles() {
            var mc = Minecraft.getInstance();
            double elapsed = elapsed();
            Vec3 core = core(elapsed);
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            double particleDistance = SscfeClientConfig.PARTICLE_RENDER_DISTANCE.get();
            boolean coreVisible = core.distanceToSqr(camera) <= particleDistance * particleDistance;
            boolean auraVisible = packet.stage() == WaterCurseNetwork.Stage.CHARGE
                    && packet.casterPosition().distanceToSqr(camera) <= particleDistance * particleDistance;
            if (!coreVisible && !auraVisible) return;
            double growth = Math.min(1D, elapsed / 600D);
            double radius = packet.stage() == WaterCurseNetwork.Stage.RELEASE
                    ? 2D + 6D * WaterPurpleRules.power(packet.duration()) : 1D + 10D * growth;
            int count = SscfeClientConfig.EFFECT_PARTICLES.get();
            int auraCount = auraVisible ? count * 3 / 4 : 0;
            if (auraCount > 0) WaterPurpleAuraRenderer.particles(mc.level, packet.casterPosition(), elapsed,
                    packet.duration(), auraSeed(), auraCount);
            int coreCount = coreVisible ? count - auraCount : 0;
            for (int i = 0; i < coreCount; i++) {
                double angle = elapsed * 0.08D + i * Math.PI * 2 / coreCount;
                double height = (i % 8) * 0.65D;
                Vec3 radial = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                Vec3 point = core.add(radial.scale(radius)).add(0, height, 0);
                Vec3 velocity = radial.scale(-0.12D).add(packet.direction().scale(0.04D));
                mc.level.addParticle(i % 3 == 0 ? ParticleTypes.SPLASH : i % 2 == 0 ? BLUE : CYAN,
                        true, point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
            }
        }
    }
    private WaterPurpleEffects() {}
}
