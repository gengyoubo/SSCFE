package github.com.gengyoubo.sscfe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.init.ModWaterContent;
import github.com.gengyoubo.sscfe.water.WaterTankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Renders the water inside the model's hollow, transparent window. */
@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WaterTankRenderer implements BlockEntityRenderer<WaterTankBlockEntity> {
    // The body spans 2..14 model pixels. Its three-texel frame leaves 4.25..11.75 visible.
    private static final float MIN = 4.25F / 16.0F;
    private static final float MAX = 11.75F / 16.0F;

    public WaterTankRenderer(BlockEntityRendererProvider.Context context) {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModWaterContent.WATER_TANK_ENTITY.get(), WaterTankRenderer::new);
    }

    @Override
    public void render(WaterTankBlockEntity entity, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (entity.tank().isEmpty() || entity.getLevel() == null) return;
        var fluid = entity.tank().getFluid();
        var properties = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(properties.getStillTexture(fluid));
        int tint = properties.getTintColor(fluid.getFluid().defaultFluidState(), entity.getLevel(), entity.getBlockPos());
        if ((tint >>> 24) == 0) tint |= 0xFF000000;
        float top = MIN + (MAX - MIN) * entity.fillFraction();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poses.last();
        float u0 = sprite.getU(0);
        float u1 = sprite.getU((MAX - MIN) * 16);
        float vBottom = sprite.getV(16);
        float vTop = sprite.getV(16 - (top - MIN) * 16);
        float vSurface = sprite.getV((MAX - MIN) * 16);

        // Four sides, facing outwards. Crop the fluid UVs to preserve normal Minecraft texel density.
        quad(vertices, pose, tint, packedLight, 0, 0, -1,
                MIN, MIN, MIN, MIN, top, MIN, MAX, top, MIN, MAX, MIN, MIN, u0, u1, vTop, vBottom);
        quad(vertices, pose, tint, packedLight, 0, 0, 1,
                MAX, MIN, MAX, MAX, top, MAX, MIN, top, MAX, MIN, MIN, MAX, u0, u1, vTop, vBottom);
        quad(vertices, pose, tint, packedLight, -1, 0, 0,
                MIN, MIN, MAX, MIN, top, MAX, MIN, top, MIN, MIN, MIN, MIN, u0, u1, vTop, vBottom);
        quad(vertices, pose, tint, packedLight, 1, 0, 0,
                MAX, MIN, MIN, MAX, top, MIN, MAX, top, MAX, MAX, MIN, MAX, u0, u1, vTop, vBottom);
        // Horizontal surface and bottom complete the volume; the model frame never contains painted water.
        quad(vertices, pose, tint, packedLight, 0, 1, 0,
                MIN, top, MIN, MIN, top, MAX, MAX, top, MAX, MAX, top, MIN, u0, u1, sprite.getV(0), vSurface);
        quad(vertices, pose, tint, packedLight, 0, -1, 0,
                MIN, MIN, MAX, MIN, MIN, MIN, MAX, MIN, MIN, MAX, MIN, MAX, u0, u1, sprite.getV(0), vSurface);
    }

    private static void quad(VertexConsumer vertices, PoseStack.Pose pose, int tint, int light,
                             float nx, float ny, float nz,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float u0, float u1, float v0, float v1) {
        vertex(vertices, pose, tint, light, x0, y0, z0, u0, v1, nx, ny, nz);
        vertex(vertices, pose, tint, light, x1, y1, z1, u0, v0, nx, ny, nz);
        vertex(vertices, pose, tint, light, x2, y2, z2, u1, v0, nx, ny, nz);
        vertex(vertices, pose, tint, light, x3, y3, z3, u1, v1, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, int tint, int light,
                               float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        vertices.vertex(pose.pose(), x, y, z)
                .color((tint >> 16) & 255, (tint >> 8) & 255, tint & 255, (tint >>> 24) & 255)
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.normal(), nx, ny, nz).endVertex();
    }
}
