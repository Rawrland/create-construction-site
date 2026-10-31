package dev.rawrland.constructionsite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.rawrland.constructionsite.content.console.ConsoleControl;
import dev.rawrland.constructionsite.content.console.OperatorConsoleBlock;
import dev.rawrland.constructionsite.content.console.OperatorConsoleBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Draws the Operator Console's controls on the desk of Create's Train Controls (the block
 * model reuses Create's desk). Left half: the cross of 4 buttons, the 4 bonus buttons in a row
 * in front of it. Right half: 5 slim levers, each arm in its own colour (owner, 30 October
 * 2026, 18:42). Pressed buttons sink in; levers tilt forward as their signal rises.
 *
 * Own frame, in pixels: x to the operator's right, z toward the operator (as for facing south).
 */
public class OperatorConsoleRenderer implements BlockEntityRenderer<OperatorConsoleBlockEntity> {

    /** Top of the desk. */
    private static final float DESK_TOP = 15.05F;

    /** Lever row on the right half: left edge and step between levers (pixels). */
    public static final double LEVER_ROW_LEFT = 8.5;
    public static final double LEVER_STEP = 1.1;
    private static final float LEVER_Z = 5.5F;
    /** Half the earlier 3.5 (owner, 31 October 2026). */
    private static final float LEVER_LENGTH = 1.75F;
    private static final float LEVER_HALF = 0.25F;
    private static final float MAX_TILT = 30.0F;

    /** Left half: cross centre and the distance of its buttons from it; bonus buttons in a row in front. */
    private static final float CROSS_X = 5.0F;
    private static final float CROSS_Z = 4.0F;
    private static final float CROSS_OFFSET = 1.4F;
    private static final float[] BONUS_X = {2.9F, 4.3F, 5.7F, 7.1F};
    private static final float BONUS_Z = 7.6F;
    private static final float BUTTON_HALF = 0.55F;
    private static final float BUTTON_UP = 0.8F;
    private static final float BUTTON_DOWN = 0.3F;

    private static final int CROSS_COLOUR = 0xFFB4342A;
    private static final int CROSS_PRESSED = 0xFFFF6A50;
    private static final int BONUS_COLOUR = 0xFF8A8F98;
    private static final int BONUS_PRESSED = 0xFFE6E6E6;

    public OperatorConsoleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(OperatorConsoleBlockEntity console, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = console.getBlockState().getValue(OperatorConsoleBlock.FACING);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        TextureAtlasSprite plain = sprite("minecraft", "block/white_concrete");

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, 0.0, -0.5);
        poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);

        // Cross: forward, left, back, right.
        float[][] cross = {{0, -CROSS_OFFSET}, {-CROSS_OFFSET, 0}, {0, CROSS_OFFSET}, {CROSS_OFFSET, 0}};
        for (int b = 0; b < ConsoleControl.CROSS; b++) {
            boolean down = console.buttonDown(b);
            button(poseStack, buffer, CROSS_X + cross[b][0], CROSS_Z + cross[b][1], down,
                down ? CROSS_PRESSED : CROSS_COLOUR, plain, packedLight);
        }
        for (int b = 0; b < ConsoleControl.BONUS; b++) {
            boolean down = console.buttonDown(ConsoleControl.CROSS + b);
            button(poseStack, buffer, BONUS_X[b], BONUS_Z, down, down ? BONUS_PRESSED : BONUS_COLOUR, plain, packedLight);
        }

        for (int i = 0; i < ConsoleControl.LEVERS; i++) {
            float x = (float) (LEVER_ROW_LEFT + (i + 0.5) * LEVER_STEP);
            // Slot in the desk the lever moves in.
            box(poseStack, buffer, x - 0.35F, DESK_TOP, LEVER_Z - 1.0F, x + 0.35F, DESK_TOP + 0.1F, LEVER_Z + 1.0F,
                plain, 0, 0, 16, 16, 0xFF1A1A1A, packedLight);

            poseStack.pushPose();
            poseStack.translate(x, DESK_TOP, LEVER_Z);
            float tilt = (console.lever(i) - 0.5F) * 2.0F * MAX_TILT;
            // Forward leans the top away from the operator, toward -Z.
            poseStack.mulPose(Axis.XP.rotationDegrees(-tilt));
            // A slim arm in the lever's colour, no knob.
            box(poseStack, buffer, -LEVER_HALF, 0, -LEVER_HALF, LEVER_HALF, LEVER_LENGTH, LEVER_HALF,
                plain, 0, 0, 16, 16, OperatorConsoleScreen.LEVER_COLOURS[i], packedLight);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void button(PoseStack poseStack, VertexConsumer buffer, float x, float z, boolean down, int colour,
                               TextureAtlasSprite sprite, int light) {
        float top = DESK_TOP + (down ? BUTTON_DOWN : BUTTON_UP);
        box(poseStack, buffer, x - BUTTON_HALF, DESK_TOP, z - BUTTON_HALF, x + BUTTON_HALF, top, z + BUTTON_HALF,
            sprite, 0, 0, 16, 16, colour, light);
    }

    private static TextureAtlasSprite sprite(String namespace, String path) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /**
     * A box between two corners in the current frame (pixels), with a part of a sprite (u0..u1,
     * v0..v1 in 0..16, as in block models) on every face, tinted with an ARGB colour.
     */
    private static void box(PoseStack poseStack, VertexConsumer buffer, float x0, float y0, float z0,
                            float x1, float y1, float z1, TextureAtlasSprite sprite,
                            float tu0, float tv0, float tu1, float tv1, int argb, int light) {
        PoseStack.Pose pose = poseStack.last();
        float u0 = sprite.getU(tu0 / 16.0F);
        float u1 = sprite.getU(tu1 / 16.0F);
        float v0 = sprite.getV(tv0 / 16.0F);
        float v1 = sprite.getV(tv1 / 16.0F);
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        // up, down, north (-Z), south (+Z), west (-X), east (+X)
        quad(buffer, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, u0, u1, v0, v1, r, g, b, light);
        quad(buffer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, u0, u1, v0, v1, r, g, b, light);
        quad(buffer, pose, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, -1, u0, u1, v0, v1, r, g, b, light);
        quad(buffer, pose, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, 1, u0, u1, v0, v1, r, g, b, light);
        quad(buffer, pose, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1, 0, 0, u0, u1, v0, v1, r, g, b, light);
        quad(buffer, pose, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1, 0, 0, u0, u1, v0, v1, r, g, b, light);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, float u0, float u1, float v0, float v1,
                             int r, int g, int b, int light) {
        vertex(buffer, pose, ax, ay, az, u0, v0, nx, ny, nz, r, g, b, light);
        vertex(buffer, pose, bx, by, bz, u0, v1, nx, ny, nz, r, g, b, light);
        vertex(buffer, pose, cx, cy, cz, u1, v1, nx, ny, nz, r, g, b, light);
        vertex(buffer, pose, dx, dy, dz, u1, v0, nx, ny, nz, r, g, b, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz, int r, int g, int b, int light) {
        buffer.addVertex(pose, x, y, z)
            .setColor(r, g, b, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }
}
