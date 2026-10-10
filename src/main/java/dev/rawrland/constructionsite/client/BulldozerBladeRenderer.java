package dev.rawrland.constructionsite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rawrland.constructionsite.content.blade.BladeGroup;
import dev.rawrland.constructionsite.content.blade.BladeMount;
import dev.rawrland.constructionsite.content.blade.BladePile;
import dev.rawrland.constructionsite.content.blade.BulldozerBladeBlockEntity;
import dev.rawrland.constructionsite.content.bucket.BucketGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Function;

/**
 * Draws bulldozer blades.
 *
 * Blade blocks have no block model of their own. The anchor block of each blade
 * (bottom left; for a single blade, the block itself) draws the whole blade in
 * code as one stretched shape, so the number of faces is the same for a blade
 * 1 block wide and one 9 blocks wide. It also draws the load as a pile of
 * blocks in front of the blade.
 *
 * All drawing below uses the blade's own coordinates, in pixels (1/16 block):
 * x runs along the cutting edge, y up from the bottom, z from the working face
 * toward the back, all counted from the anchor block's front bottom left corner.
 */
public class BulldozerBladeRenderer implements BlockEntityRenderer<BulldozerBladeBlockEntity> {

    private static final ResourceLocation BODY = ResourceLocation.withDefaultNamespace("block/yellow_concrete");
    private static final ResourceLocation STEEL = ResourceLocation.withDefaultNamespace("block/gray_concrete");

    /**
     * The side profile of the working face for a 1-high blade, as (z, y) points
     * from the bottom up: the cutting edge leans back, the face stands straight,
     * the lip leans forward again. Three flat strips that read as a curve.
     */
    private static final float[][] PROFILE = {
        {0.0F, 0.0F}, {3.0F, 4.0F}, {3.0F, 12.0F}, {0.0F, 16.0F}
    };
    /** Thickness of the plate for a 1-high blade, measured toward the back. */
    private static final float PLATE = 2.0F;

    /**
     * A blade is always one block deep, however high it is. So the plate's shape
     * grows in height with the blade, but in depth only up to twice its 1-high
     * size, or a 3-high blade would not leave room for the mount behind it.
     */
    private static float depthScale(float bladeHeight) {
        return Math.min(bladeHeight, 2.0F);
    }

    /**
     * Blocks of the pile are drawn a hair smaller than a full block. Where the
     * pile overlaps real blocks its faces would otherwise lie exactly on theirs and flicker.
     */
    private static final float PILE_BLOCK_SCALE = 0.998F;

    /** A block that was just taken slides into its place in the pile over this many ticks. */
    private static final float SLIDE_TICKS = 6.0F;

    private final BlockRenderDispatcher blockRenderer;

    public BulldozerBladeRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(BulldozerBladeBlockEntity blade, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // Never draw for a blade block that no longer exists.
        if (blade.isRemoved() || blade.getLevel() == null
            || blade.getLevel().getBlockEntity(blade.getBlockPos()) != blade) {
            return;
        }
        // In a joined blade only the anchor block draws.
        if (!blade.isAnchor()) {
            return;
        }
        int height = blade.getGroupHeight();
        int width = blade.getGroupWidth();
        BucketGroup.Axes axes = BladeGroup.axes(blade.getBlockState());

        Function<ResourceLocation, TextureAtlasSprite> atlas =
            Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite body = atlas.apply(BODY);
        TextureAtlasSprite steel = atlas.apply(STEEL);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Painter painter = new Painter(buffer, poseStack.last(), axes, packedLight);
        drawPlate(painter, height, width * 16.0F, body, steel);
        drawMount(painter, blade.getMount(), height, width * 16.0F, body, steel);

        // The pile comes last: drawing blocks switches to other buffers, and the
        // painter's buffer must not be used any more after that.
        drawPile(blade, axes, partialTick, poseStack, bufferSource, packedLight);
    }

    // --------------------------------------------------------------- the blade

    /** The plate: three strips, each with a front, a back and two end faces, plus the narrow edges at the bottom and the top. 14 faces. */
    private static void drawPlate(Painter p, float s, float totalWidth, TextureAtlasSprite body, TextureAtlasSprite steel) {
        float d = depthScale(s);
        float t = PLATE * d;
        int last = PROFILE.length - 1;
        for (int i = 0; i < last; i++) {
            float z0 = PROFILE[i][0] * d, y0 = PROFILE[i][1] * s;
            float z1 = PROFILE[i + 1][0] * d, y1 = PROFILE[i + 1][1] * s;
            // The direction a strip faces: at right angles to its own line, toward the front.
            float dz = z1 - z0, dy = y1 - y0;
            float length = (float) Math.sqrt(dz * dz + dy * dy);
            float nz = -dy / length, ny = dz / length;
            // The lowest strip is the cutting edge, in steel.
            TextureAtlasSprite front = i == 0 ? steel : body;

            p.flatQuad(front, 0, ny, nz,
                0, y0, z0, totalWidth, y0, z0, totalWidth, y1, z1, 0, y1, z1);
            p.flatQuad(body, 0, -ny, -nz,
                0, y0, z0 + t, totalWidth, y0, z0 + t, totalWidth, y1, z1 + t, 0, y1, z1 + t);
            // The two ends of this strip.
            p.flatQuad(front, -1, 0, 0,
                0, y0, z0, 0, y0, z0 + t, 0, y1, z1 + t, 0, y1, z1);
            p.flatQuad(front, 1, 0, 0,
                totalWidth, y0, z0, totalWidth, y0, z0 + t, totalWidth, y1, z1 + t, totalWidth, y1, z1);
        }
        // The narrow edge along the bottom and the one along the top.
        float zBottom = PROFILE[0][0] * d, yBottom = PROFILE[0][1] * s;
        float zTop = PROFILE[last][0] * d, yTop = PROFILE[last][1] * s;
        p.flatQuad(steel, 0, -1, 0,
            0, yBottom, zBottom, totalWidth, yBottom, zBottom, totalWidth, yBottom, zBottom + t, 0, yBottom, zBottom + t);
        p.flatQuad(body, 0, 1, 0,
            0, yTop, zTop, totalWidth, yTop, zTop, totalWidth, yTop, zTop + t, 0, yTop, zTop + t);
    }

    /**
     * The mount on the back: a beam along the whole width, two push arms that
     * reach from it to the back edge of the block, and a pin through both arms.
     * The arms end exactly where the next block of the vehicle begins, so the
     * blade looks attached to it.
     *
     * The arms sit either in the middle of the blade, one block apart (for a
     * vehicle narrower than the blade), or near the two ends of the blade (for
     * a vehicle as wide as the blade). On a blade one block wide both look the same.
     */
    private static void drawMount(Painter p, BladeMount mount, float s, float totalWidth,
                                  TextureAtlasSprite body, TextureAtlasSprite steel) {
        if (mount == BladeMount.NONE) {
            return;
        }
        float d = depthScale(s);
        float plateBack = (PROFILE[1][0] + PLATE) * d;
        // A blade is always one block deep, however high it is.
        float blockBack = 16.0F;
        float middle = 8.0F * s;
        float beamBack = plateBack + 1.5F * d;
        p.box(body, 0, middle - 2.0F * s, plateBack, totalWidth, middle + 2.0F * s, beamBack);

        // The stretch of the width the arms stand on: one block in the middle, or the whole blade.
        float span = mount == BladeMount.CENTRE ? 16.0F : totalWidth;
        float spanStart = (totalWidth - span) * 0.5F;

        // One arm near each end of that stretch. On a narrow stretch the arms move in and get thinner.
        float inset = Math.min(3.5F * s, span * 0.2F);
        float armWidth = Math.min(1.5F * s, span * 0.12F);
        float[] armStarts = {spanStart + inset, spanStart + span - inset - armWidth};
        for (float x : armStarts) {
            p.box(body, x, middle - 1.6F * s, beamBack, x + armWidth, middle + 1.6F * s, blockBack);
        }
        // The pin sits halfway along the arms.
        float pinMiddle = (beamBack + blockBack) * 0.5F;
        float pinHalf = Math.min(0.6F * s, (blockBack - beamBack) / 4.0F);
        p.box(steel, spanStart + inset - 0.3F * armWidth, middle - pinHalf, pinMiddle - pinHalf,
            spanStart + span - inset + 0.3F * armWidth, middle + pinHalf, pinMiddle + pinHalf);
    }

    // ---------------------------------------------------------------- the pile

    /**
     * The load: every carried block is drawn as the block it is, full size, at
     * its place in the pile in front of its column (see {@link BladePile} for
     * the shape). The pile is never higher than the blade.
     *
     * A block that was taken a moment ago is drawn on its way from where it
     * stood in the world to that place, so it is seen being pushed in.
     */
    private void drawPile(BulldozerBladeBlockEntity blade, BucketGroup.Axes axes, float partialTick,
                          PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (blade.getLevel() == null) {
            return;
        }
        int height = blade.getGroupHeight();
        long gameTime = blade.getLevel().getGameTime();
        List<List<BulldozerBladeBlockEntity.Stored>> columns = blade.getColumnContents();
        Vec3i right = axes.right().getNormal();
        Vec3i up = axes.up().getNormal();
        Vec3i forward = axes.back().getOpposite().getNormal();

        for (int column = 0; column < columns.size(); column++) {
            List<BulldozerBladeBlockEntity.Stored> pile = columns.get(column);
            for (int index = 0; index < pile.size(); index++) {
                BulldozerBladeBlockEntity.Stored block = pile.get(index);
                int[] slot = BladePile.slot(height, index);

                // How much of the way into the pile is still to go: 1 when just taken, 0 when arrived.
                float age = (float) (gameTime - block.takenAt()) + partialTick;
                float progress = Math.max(0.0F, Math.min(1.0F, age / SLIDE_TICKS));
                float toGo = (1.0F - progress) * (1.0F - progress);

                // Along the blade's own directions, in blocks, counted from the anchor block.
                float alongRight = column + block.fromRight() * toGo;
                float alongUp = slot[1] + block.fromUp() * toGo;
                float alongForward = 1.0F + slot[0] + block.fromForward() * toGo;

                poseStack.pushPose();
                // To the middle of the block's place, then shrink it a hair around that middle.
                poseStack.translate(
                    0.5 + right.getX() * alongRight + up.getX() * alongUp + forward.getX() * alongForward,
                    0.5 + right.getY() * alongRight + up.getY() * alongUp + forward.getY() * alongForward,
                    0.5 + right.getZ() * alongRight + up.getZ() * alongUp + forward.getZ() * alongForward);
                poseStack.scale(PILE_BLOCK_SCALE, PILE_BLOCK_SCALE, PILE_BLOCK_SCALE);
                poseStack.translate(-0.5, -0.5, -0.5);
                blockRenderer.renderSingleBlock(block.state(), poseStack, bufferSource, packedLight,
                    OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
    }

    // ------------------------------------------------- telling the game what to draw

    /** NeoForge asks for the space the drawing takes up: the whole blade and its pile, not just the anchor block. */
    public AABB getRenderBoundingBox(BulldozerBladeBlockEntity blade) {
        return new AABB(blade.getBlockPos()).inflate(blade.isJoined() ? BladeGroup.MAX_WIDTH + 1.0 : 4.0);
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Turns blade coordinates into vertices. It knows which way the blade is
     * turned, so the drawing code above never has to think about directions.
     */
    private static final class Painter {

        private final VertexConsumer buffer;
        private final PoseStack.Pose pose;
        private final int packedLight;
        // The blade's own directions as plain numbers: right, up, back.
        private final float rx, ry, rz, ux, uy, uz, bx, by, bz;

        Painter(VertexConsumer buffer, PoseStack.Pose pose, BucketGroup.Axes axes, int packedLight) {
            this.buffer = buffer;
            this.pose = pose;
            this.packedLight = packedLight;
            Vec3i r = axes.right().getNormal(), u = axes.up().getNormal(), b = axes.back().getNormal();
            rx = r.getX();
            ry = r.getY();
            rz = r.getZ();
            ux = u.getX();
            uy = u.getY();
            uz = u.getZ();
            bx = b.getX();
            by = b.getY();
            bz = b.getZ();
        }

        /** One vertex. x, y, z in blade pixels; the normal in blade directions. */
        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            // From the anchor block's front bottom left corner, measured from the block's centre.
            float cx = x / 16.0F - 0.5F;
            float cy = y / 16.0F - 0.5F;
            float cz = z / 16.0F - 0.5F;
            float worldX = 0.5F + cx * rx + cy * ux + cz * bx;
            float worldY = 0.5F + cx * ry + cy * uy + cz * by;
            float worldZ = 0.5F + cx * rz + cy * uz + cz * bz;
            float normalX = nx * rx + ny * ux + nz * bx;
            float normalY = nx * ry + ny * uy + nz * by;
            float normalZ = nx * rz + ny * uz + nz * bz;
            buffer.addVertex(pose, worldX, worldY, worldZ)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
        }

        /** A four-cornered face in one flat colour: the colour in the middle of the given texture. */
        void flatQuad(TextureAtlasSprite sprite, float nx, float ny, float nz,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4) {
            float u = (sprite.getU0() + sprite.getU1()) * 0.5F;
            float v = (sprite.getV0() + sprite.getV1()) * 0.5F;
            vertex(x1, y1, z1, u, v, nx, ny, nz);
            vertex(x2, y2, z2, u, v, nx, ny, nz);
            vertex(x3, y3, z3, u, v, nx, ny, nz);
            vertex(x4, y4, z4, u, v, nx, ny, nz);
        }

        /** A box with its sides along the blade's directions. */
        void box(TextureAtlasSprite sprite, float x0, float y0, float z0, float x1, float y1, float z1) {
            flatQuad(sprite, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
            flatQuad(sprite, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            flatQuad(sprite, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            flatQuad(sprite, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
            flatQuad(sprite, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            flatQuad(sprite, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        }
    }
}
