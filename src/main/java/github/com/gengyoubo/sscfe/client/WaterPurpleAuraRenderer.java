package github.com.gengyoubo.sscfe.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A bounded volume around the caster, separate from the forward projectile core. */
final class WaterPurpleAuraRenderer extends RenderType {
    static final RenderType SMOKE = create("sscfe_water_aura", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 131072, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.04F, 0.12F, 0.42F), 3.5F);
    private static final DustParticleOptions EDGE = new DustParticleOptions(new Vector3f(0.22F, 0.48F, 1F), 2.5F);
    private static final DustParticleOptions CLOUD = new DustParticleOptions(new Vector3f(0.55F, 0.8F, 1F), 3F);

    private WaterPurpleAuraRenderer() {
        super("sscfe_water_aura_factory", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                256, false, true, () -> {}, () -> {});
    }

    private static double growth(double elapsed, int duration) {
        double progress = Math.max(0D, Math.min(1D, elapsed / Math.max(1, Math.min(600, duration))));
        return progress * progress * (3D - 2D * progress);
    }

    private static double phase(long seed) { return (seed & 1023L) * Math.PI * 2D / 1024D; }
    private static double height(double growth) {
        return 8D + (SscfeClientConfig.CHARGE_AURA_HEIGHT.get() - 8D) * growth;
    }

    static void render(VertexConsumer smoke, Matrix4f matrix, Camera camera,
                       Vec3 center, double elapsed, int duration, long seed) {
        double growth = growth(elapsed, duration);
        if (growth <= 0.001D) return;
        double height = height(growth), phase = phase(seed);
        boolean near = center.distanceToSqr(camera.getPosition()) < 128D * 128D;
        int layers = near ? 16 : 10, segments = near ? 24 : 16;

        // Two translucent, undulating shells supply volume even with very few particles.
        for (int shell = 0; shell < 2; shell++) {
            double scale = shell == 0 ? 0.85D : 1.15D;
            for (int layer = 0; layer < layers; layer++) {
                double t0 = layer / (double) layers, t1 = (layer + 1D) / layers;
                int alpha0 = shellAlpha(t0, growth, shell), alpha1 = shellAlpha(t1, growth, shell);
                for (int segment = 0; segment < segments; segment++) {
                    double a0 = segment * Math.PI * 2D / segments, a1 = (segment + 1D) * Math.PI * 2D / segments;
                    int red = shell == 0 ? 9 : 20, green = shell == 0 ? 24 : 50, blue = shell == 0 ? 90 : 142;
                    vertex(smoke, matrix, bodyPoint(center, height, growth, elapsed, phase, t0, a0, scale), red, green, blue, alpha0);
                    vertex(smoke, matrix, bodyPoint(center, height, growth, elapsed, phase, t1, a0, scale), red, green, blue, alpha1);
                    vertex(smoke, matrix, bodyPoint(center, height, growth, elapsed, phase, t1, a1, scale), red, green, blue, alpha1);
                    vertex(smoke, matrix, bodyPoint(center, height, growth, elapsed, phase, t0, a1, scale), red, green, blue, alpha0);
                }
            }
        }

        // Broad spirals around the feet, rather than thin, evenly stacked rings.
        for (int band = 0; band < 2; band++) {
            for (int segment = 0; segment < 48; segment++) {
                double a0 = segment * Math.PI * 2D / 48D, a1 = (segment + 1D) * Math.PI * 2D / 48D;
                vertex(smoke, matrix, basePoint(center, growth, elapsed, phase, band, a0, 0), 14, 42, 112, (int) (48 * growth));
                vertex(smoke, matrix, basePoint(center, growth, elapsed, phase, band, a0, 1), 65, 135, 238, 0);
                vertex(smoke, matrix, basePoint(center, growth, elapsed, phase, band, a1, 1), 65, 135, 238, 0);
                vertex(smoke, matrix, basePoint(center, growth, elapsed, phase, band, a1, 0), 14, 42, 112, (int) (48 * growth));
            }
        }

        // Broken, wide highlights trace the column's turning surface.
        for (int ribbon = 0; ribbon < 3; ribbon++) {
            for (int layer = 0; layer < layers; layer++) {
                double t0 = 0.05D + layer * 0.9D / layers, t1 = 0.05D + (layer + 1D) * 0.9D / layers;
                double a0 = t0 * 10D - elapsed * 0.035D + phase + ribbon * Math.PI * 2D / 3D;
                double a1 = t1 * 10D - elapsed * 0.035D + phase + ribbon * Math.PI * 2D / 3D;
                int alpha = (int) (35D * growth * Math.pow(Math.sin(Math.PI * (t0 + t1) * 0.5D), 2D));
                vertex(smoke, matrix, bodyPoint(center, height, growth, elapsed, phase, t0, a0, 1.17D), 48, 118, 255, alpha);
                vertex(smoke, matrix, bodyPoint(center, height, growth, elapsed, phase, t1, a1, 1.17D), 48, 118, 255, alpha);
                vertex(smoke, matrix, bodyPoint(center, height, growth, elapsed, phase, t1, a1 + 0.22D, 1.17D), 100, 180, 255, 0);
                vertex(smoke, matrix, bodyPoint(center, height, growth, elapsed, phase, t0, a0 + 0.22D, 1.17D), 100, 180, 255, 0);
            }
        }

        Vector3f cameraRight = new Vector3f(1, 0, 0).rotate(camera.rotation());
        Vector3f cameraUp = new Vector3f(0, 1, 0).rotate(camera.rotation());
        Vec3 right = new Vec3(cameraRight.x, cameraRight.y, cameraRight.z);
        Vec3 up = new Vec3(cameraUp.x, cameraUp.y, cameraUp.z);
        // Overlapping soft discs break the outline into cloud lobes, without square billboard edges.
        for (int cloud = 0; cloud < (near ? 10 : 5); cloud++) {
            double t = 0.18D + (cloud % 5) * 0.16D;
            double angle = phase + cloud * 2.39996D + elapsed * 0.012D;
            Vec3 point = bodyPoint(center, height, growth, elapsed, phase, t, angle, 1.05D);
            puff(smoke, matrix, point, right, up, 2D + growth * 2D, 1.2D + growth,
                    22, 48, 125, (int) (28D * growth));
        }
        for (int cloud = 0; cloud < (near ? 12 : 6); cloud++) {
            Vec3 point = topPoint(center, height, growth, elapsed, phase, cloud);
            puff(smoke, matrix, point, right, up, 1.4D + (cloud % 5) * 0.5D + growth * 1.6D,
                    0.55D + (cloud % 3) * 0.25D, 110, 180, 255, (int) (65D * growth));
        }
    }

    private static int shellAlpha(double t, double growth, int shell) {
        return (int) ((shell == 0 ? 32D : 20D) * growth * Math.pow(Math.max(0D, Math.sin(Math.PI * t)), 0.65D));
    }

    private static Vec3 bodyPoint(Vec3 center, double height, double growth, double time, double phase,
                                  double t, double angle, double scale) {
        double bulge = 2.3D + 3.2D * Math.sin(t * Math.PI * 0.85D) + 1.7D * t;
        double ripple = 0.75D * Math.sin(angle * 3D + time * 0.026D + t * 13D + phase)
                + 0.4D * Math.sin(angle * 5D - time * 0.041D + t * 7D);
        double radius = (bulge + ripple) * (0.55D + 0.45D * growth) * scale;
        double leanX = Math.sin(time * 0.022D + t * 4D + phase) * 1.6D * t;
        double leanZ = Math.cos(time * 0.019D + t * 5D + phase) * 1.6D * t;
        return center.add(leanX + Math.cos(angle) * radius, t * height, leanZ + Math.sin(angle) * radius);
    }

    private static Vec3 basePoint(Vec3 center, double growth, double time, double phase, int band, double angle, int edge) {
        double radius = 5D + 3D * growth - band * 1.2D - edge * 1.5D
                + 0.45D * Math.sin(angle * 3D + time * 0.03D + phase);
        double rotation = angle + time * 0.022D * (band == 0 ? 1D : -1D);
        return center.add(Math.cos(rotation) * radius,
                0.2D + band * 0.6D + 0.25D * Math.sin(angle * 2D - time * 0.04D), Math.sin(rotation) * radius);
    }

    private static Vec3 topPoint(Vec3 center, double height, double growth, double time, double phase, int cloud) {
        double angle = phase + cloud * 2.39996D + time * 0.009D;
        double radius = 3D + 5D * growth + 1.2D * Math.sin(cloud * 1.3D + time * 0.017D);
        return center.add(Math.cos(angle) * radius, height + 1D + (cloud % 3) * 1.2D
                + 2D * Math.sin(time * 0.014D + cloud * 0.75D), Math.sin(angle) * radius);
    }

    private static void puff(VertexConsumer out, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                             double width, double height, int red, int green, int blue, int alpha) {
        for (int lobe = 0; lobe < 3; lobe++) {
            Vec3 lobeCenter = center.add(right.scale((lobe - 1) * width * 0.45D)).add(up.scale(lobe == 1 ? height * 0.25D : 0D));
            double scale = lobe == 1 ? 1D : 0.7D;
            for (int segment = 0; segment < 8; segment++) {
                double a0 = segment * Math.PI / 4D, a1 = (segment + 1D) * Math.PI / 4D;
                vertex(out, matrix, lobeCenter, red, green, blue, alpha);
                vertex(out, matrix, lobeCenter.add(right.scale(Math.cos(a0) * width * scale))
                        .add(up.scale(Math.sin(a0) * height * scale)), red, green, blue, 0);
                vertex(out, matrix, lobeCenter.add(right.scale(Math.cos(a1) * width * scale))
                        .add(up.scale(Math.sin(a1) * height * scale)), red, green, blue, 0);
                vertex(out, matrix, lobeCenter, red, green, blue, alpha);
            }
        }
    }

    static void particles(ClientLevel level, Vec3 center, double elapsed, int duration, long seed, int count) {
        double growth = growth(elapsed, duration), height = height(growth), phase = phase(seed);
        if (growth <= 0.001D) return;
        for (int i = 0; i < count; i++) {
            Vec3 point, velocity;
            DustParticleOptions color;
            double angle = phase + i * 2.39996D + elapsed * 0.08D;
            if (i < count / 4) {
                double radius = 5D + 3D * growth + 0.5D * Math.sin(i + elapsed * 0.03D);
                point = center.add(Math.cos(angle) * radius, 0.25D + (i % 4) * 0.3D, Math.sin(angle) * radius);
                velocity = new Vec3(-Math.cos(angle) * 0.08D, 0.12D, -Math.sin(angle) * 0.08D);
                color = EDGE;
            } else if (i < count * 3 / 4) {
                double t = (Math.floorMod(i * 5 + (int) (elapsed * 2D), 18) + 0.5D) / 18D;
                point = bodyPoint(center, height, growth, elapsed, phase, t, angle, 0.8D + (i % 3) * 0.12D);
                velocity = new Vec3(-Math.sin(angle) * 0.08D, 0.16D + t * 0.12D, Math.cos(angle) * 0.08D);
                color = i % 3 == 0 ? EDGE : DARK;
            } else {
                point = topPoint(center, height, growth, elapsed, phase, i % 12)
                        .add(Math.cos(angle) * 1.5D, Math.sin(angle * 1.3D) * 0.8D, Math.sin(angle) * 1.5D);
                velocity = new Vec3(Math.cos(angle) * 0.025D, 0.03D, Math.sin(angle) * 0.025D);
                color = CLOUD;
            }
            level.addParticle(color, true, point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 point, int red, int green, int blue, int alpha) {
        out.vertex(matrix, (float) point.x, (float) point.y, (float) point.z).color(red, green, blue, alpha).endVertex();
    }
}
