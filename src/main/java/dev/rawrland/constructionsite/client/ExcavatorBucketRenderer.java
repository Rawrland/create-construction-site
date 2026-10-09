package dev.rawrland.constructionsite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rawrland.constructionsite.content.bucket.BucketGroup;
import dev.rawrland.constructionsite.content.bucket.BucketMount;
import dev.rawrland.constructionsite.content.bucket.BucketTeeth;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Draws buckets.
 *
 * Bucket blocks have no block model of their own. The anchor block of each
 * bucket (bottom front left; for a single bucket, the block itself) draws the
 * whole bucket in code, in whatever size it has, plus its content.
 *
 * All drawing below uses the bucket's own coordinates, in pixels (1/16 block):
 * x runs along the row of teeth, y up from the floor, z from the open face
 * toward the back, all counted from the anchor block's front bottom left corner.
 */
public class ExcavatorBucketRenderer implements BlockEntityRenderer<ExcavatorBucketBlockEntity> {

    private static final ResourceLocation OUTSIDE = ResourceLocation.withDefaultNamespace("block/yellow_concrete");
    private static final ResourceLocation INSIDE = ResourceLocation.withDefaultNamespace("block/yellow_terracotta");
    private static final ResourceLocation STEEL = ResourceLocation.withDefaultNamespace("block/gray_concrete");

    private final BlockRenderDispatcher blockRenderer;

    public ExcavatorBucketRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(ExcavatorBucketBlockEntity bucket, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // Never draw for a bucket block that no longer exists. Without this check a
        // broken joined bucket could stay visible as a ghost.
        if (bucket.isRemoved() || bucket.getLevel() == null
            || bucket.getLevel().getBlockEntity(bucket.getBlockPos()) != bucket) {
            return;
        }
        // A rope is drawn by the block that owns it, with Create Aeronautics' own drawing code.
        // For a block without a rope of its own this draws nothing.
        RopeStrandRenderer.render(bucket, bucket.getBehavior(), partialTick, poseStack, bufferSource);
        // In a joined bucket only the anchor block draws.
        if (!bucket.isAnchor()) {
            return;
        }
        int height = bucket.getGroupHeight();
        int width = bucket.getGroupWidth();
        BucketShape shape = BucketShape.of(height);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Painter painter = new Painter(buffer, poseStack.last(), BucketGroup.axes(bucket.getBlockState()), packedLight);

        Function<ResourceLocation, TextureAtlasSprite> atlas =
            Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite outside = atlas.apply(OUTSIDE);
        TextureAtlasSprite steel = atlas.apply(STEEL);
        drawBody(painter, shape, width * 16.0F, outside, atlas.apply(INSIDE), steel);
        drawMount(painter, bucket.getMount(), shape, width * 16.0F, outside, steel);
        drawRopeRing(painter, bucket, height, width, steel);
        drawContent(painter, bucket, shape, width);
    }

    // ------------------------------------------------------------- the bucket

    private static void drawBody(Painter p, BucketShape shape, float totalWidth,
                                 TextureAtlasSprite outside, TextureAtlasSprite inside, TextureAtlasSprite steel) {
        int n = shape.pointCount();
        int last = n - 1;
        float s = shape.scale;
        float t = shape.thickness;
        float[] oz = shape.outerZ, oy = shape.outerY, iz = shape.innerZ, iy = shape.innerY;

        // Shell: floor, curved back and roof, across the full width. Outer and inner skin.
        for (int i = 0; i < last; i++) {
            float[] in = shape.inwardNormal(i);
            p.flatQuad(outside, 0, -in[1], -in[0],
                0, oy[i], oz[i], totalWidth, oy[i], oz[i],
                totalWidth, oy[i + 1], oz[i + 1], 0, oy[i + 1], oz[i + 1]);
            p.flatQuad(inside, 0, in[1], in[0],
                0, iy[i], iz[i], totalWidth, iy[i], iz[i],
                totalWidth, iy[i + 1], iz[i + 1], 0, iy[i + 1], iz[i + 1]);
        }

        // The two cut edges of the shell: at the lip and at the front of the roof.
        float[] open = shape.openingNormal();
        p.flatQuad(outside, 0, 0, -1,
            0, oy[0], oz[0], totalWidth, oy[0], oz[0], totalWidth, iy[0], iz[0], 0, iy[0], iz[0]);
        p.flatQuad(outside, 0, open[1], open[0],
            0, oy[last], oz[last], totalWidth, oy[last], oz[last],
            totalWidth, iy[last], iz[last], 0, iy[last], iz[last]);

        // Side walls at both ends: outer face, inner face, and the edge along the opening.
        for (int side = 0; side < 2; side++) {
            float outerX = side == 0 ? 0 : totalWidth;
            float innerX = side == 0 ? t : totalWidth - t;
            float outward = side == 0 ? -1 : 1;
            for (int i = 1; i < last; i++) {
                p.flatQuad(outside, outward, 0, 0,
                    outerX, oy[0], oz[0], outerX, oy[i], oz[i],
                    outerX, oy[i + 1], oz[i + 1], outerX, oy[i + 1], oz[i + 1]);
                p.flatQuad(inside, -outward, 0, 0,
                    innerX, oy[0], oz[0], innerX, oy[i], oz[i],
                    innerX, oy[i + 1], oz[i + 1], innerX, oy[i + 1], oz[i + 1]);
            }
            p.flatQuad(outside, 0, open[1], open[0],
                outerX, oy[0], oz[0], innerX, oy[0], oz[0],
                innerX, oy[last], oz[last], outerX, oy[last], oz[last]);
        }

        // Teeth along the lip: a thicker base and a thinner tip each.
        // Spread evenly: every gap is the same, and half a gap is left at each edge of a block,
        // so the gap between the teeth of two neighbouring blocks is the same again.
        int height = Math.round(s);
        int teeth = BucketTeeth.perBlock(height);
        int blocksWide = Math.round(totalWidth / 16.0F);
        for (int block = 0; block < blocksWide; block++) {
            for (int tooth = 0; tooth < teeth; tooth++) {
                float x = BucketTeeth.toothLeft(block, tooth, height);
                // The base wraps around the lip: it starts a little below the floor's underside,
                // so its faces never lie exactly on the floor's faces (that would flicker).
                p.box(steel, x, -0.25F * s, -2.5F * s, x + 2.0F * s, 1.5F * s, -0.4F * s);
                p.box(steel, x + 0.25F * s, 0, -4.0F * s, x + 1.75F * s, 0.8F * s, -2.5F * s);
            }
        }

        // A beam along the front edge of the roof.
        float top = 16.0F * s;
        p.box(outside, 0, top, 5.13F * s, totalWidth, top + 1.0F * s, 6.73F * s);
    }

    // ---------------------------------------------------------- the rope ring

    /** A small steel ring standing on every tooth that has a rope tied to it. */
    private static void drawRopeRing(Painter p, ExcavatorBucketBlockEntity bucket, int height, int width,
                                     TextureAtlasSprite steel) {
        float s = height;
        for (int column = 0; column < width; column++) {
            ExcavatorBucketBlockEntity hook = bucket.getHookBlock(column);
            if (hook == null || !hook.hasRope()) {
                continue;
            }
            float x = BucketTeeth.ringX(column, width, height);
            float y = BucketTeeth.ringY(height);
            float z = BucketTeeth.ringZ(height);
            // Two posts and a bar across them, around the ring's middle point.
            float half = 0.75F * s;
            float bar = 0.3F * s;
            float depth = 0.25F * s;
            float foot = 0.7F * s;
            float top = y + 0.6F * s;
            p.box(steel, x - half, foot, z - depth, x - half + bar, top, z + depth);
            p.box(steel, x + half - bar, foot, z - depth, x + half, top, z + depth);
            p.box(steel, x - half, top - bar, z - depth, x + half, top, z + depth);
        }
    }

    // -------------------------------------------------------------- the mount

    /**
     * Two ears and a pin through both, where an arm would hold the bucket:
     * on the roof, on the back, or not drawn at all.
     */
    private static void drawMount(Painter p, BucketMount mount, BucketShape shape, float totalWidth,
                                  TextureAtlasSprite outside, TextureAtlasSprite steel) {
        if (mount == BucketMount.NONE) {
            return;
        }
        float s = shape.scale;
        // One ear near each end of the width, the pin from one to the other.
        // On a bucket that is tall but only one block wide, the ears move in and get
        // thinner, so that there are still two of them with a gap between.
        float inset = Math.min(3.5F * s, totalWidth * 0.2F);
        float earWidth = Math.min(1.5F * s, totalWidth * 0.12F);
        float[] earStarts = {inset, totalWidth - inset - earWidth};
        float pinFrom = inset - 0.3F * earWidth;
        float pinTo = totalWidth - inset + 0.3F * earWidth;

        if (mount == BucketMount.TOP) {
            float top = 16.0F * s;
            for (float x : earStarts) {
                p.box(outside, x, top, 6.6F * s, x + earWidth, top + 1.6F * s, 11.0F * s);
                p.box(outside, x, top + 1.6F * s, 7.4F * s, x + earWidth, top + 3.4F * s, 10.2F * s);
            }
            p.box(steel, pinFrom, top + 1.6F * s, 8.2F * s, pinTo, top + 2.6F * s, 9.4F * s);
        } else {
            // The same ears turned onto the straight back, centred on its height.
            float back = BucketShape.BACK_Z * s;
            float middle = (BucketShape.BACK_START_Y + 16.0F) * 0.5F * s;
            for (float x : earStarts) {
                p.box(outside, x, middle - 2.2F * s, back, x + earWidth, middle + 2.2F * s, back + 1.6F * s);
                p.box(outside, x, middle - 1.4F * s, back + 1.6F * s, x + earWidth, middle + 1.4F * s, back + 3.4F * s);
            }
            p.box(steel, pinFrom, middle - 0.6F * s, back + 1.6F * s, pinTo, middle + 0.6F * s, back + 2.6F * s);
        }
    }

    // ------------------------------------------------------------ the content

    /**
     * One flat surface across the whole width. It starts at the lip and tilts up
     * toward the back as the bucket fills, until it fills the opening.
     *
     * The surface is made of tiles one block in size. Each tile shows the texture
     * of one stored block, so nothing is stretched and a mixed load looks mixed.
     * If there are more stored blocks than tiles, the most recently picked up ones are shown.
     */
    private void drawContent(Painter p, ExcavatorBucketBlockEntity bucket, BucketShape shape, int width) {
        // What the bucket holds, oldest first.
        List<ExcavatorBucketBlockEntity> filled = new ArrayList<>();
        for (ExcavatorBucketBlockEntity member : bucket.getGroupMembers()) {
            if (member.getStoredState() != null) {
                filled.add(member);
            }
        }
        if (filled.isEmpty()) {
            return;
        }
        filled.sort(Comparator.comparingLong(ExcavatorBucketBlockEntity::getPickedUpAt)
            .thenComparingLong(member -> member.getBlockPos().asLong()));
        int stored = filled.size();
        float fill = Math.min(1.0F, stored / (float) bucket.getGroupCapacity());

        // Where the surface lies.
        float startZ = shape.innerZ[0];
        float startY = shape.innerY[0];
        float[] end = shape.contentSurfaceEnd(fill);
        float dz = end[0] - startZ;
        float dy = end[1] - startY;
        float length = (float) Math.sqrt(dz * dz + dy * dy);
        if (length < 0.01F) {
            return;
        }
        dz /= length;
        dy /= length;
        // The surface faces up and forward: at right angles to its own direction.
        float normalZ = -dy;
        float normalY = dz;

        // Tiles: one column per block of width, rows of 16 pixels from the lip upward.
        int rows = Math.max(1, (int) Math.ceil(length / 16.0F - 0.01F));
        int tiles = width * rows;
        BlockState[] tileContent = new BlockState[tiles];

        // Give each of the most recent blocks a tile. A block keeps its tile while
        // newer ones arrive, and the tiles are visited in a scattered order, so the
        // surface looks mixed and does not reshuffle with every new block.
        int step = scatterStep(tiles);
        for (int rank = Math.max(0, stored - tiles); rank < stored; rank++) {
            int tile = (int) (((long) rank * step) % tiles);
            tileContent[tile] = filled.get(rank).getStoredState();
        }
        // Fewer blocks than tiles: repeat what is there.
        for (int tile = 0; tile < tiles; tile++) {
            if (tileContent[tile] == null) {
                tileContent[tile] = filled.get(tile % stored).getStoredState();
            }
        }

        float t = shape.thickness;
        float totalWidth = width * 16.0F;
        for (int column = 0; column < width; column++) {
            // The outermost columns are narrower, because the side walls take up room.
            float x0 = Math.max(column * 16.0F, t);
            float x1 = Math.min((column + 1) * 16.0F, totalWidth - t);
            float uFrom = (x0 - column * 16.0F) / 16.0F;
            float uTo = (x1 - column * 16.0F) / 16.0F;
            for (int row = 0; row < rows; row++) {
                float a0 = row * 16.0F;
                float a1 = Math.min((row + 1) * 16.0F, length);
                // The main texture of that block (the one also used for its break particles).
                TextureAtlasSprite sprite = blockRenderer.getBlockModelShaper()
                    .getParticleIcon(tileContent[column * rows + row]);
                float uSpan = sprite.getU1() - sprite.getU0();
                float vSpan = sprite.getV1() - sprite.getV0();
                float u0 = sprite.getU0() + uSpan * uFrom;
                float u1 = sprite.getU0() + uSpan * uTo;
                float vBottom = sprite.getV1();
                // A shorter top row shows only part of the texture instead of squashing it.
                float vTop = sprite.getV1() - vSpan * ((a1 - a0) / 16.0F);

                float y0 = startY + dy * a0, z0 = startZ + dz * a0;
                float y1 = startY + dy * a1, z1 = startZ + dz * a1;
                p.vertex(x0, y0, z0, u0, vBottom, 0, normalY, normalZ);
                p.vertex(x1, y0, z0, u1, vBottom, 0, normalY, normalZ);
                p.vertex(x1, y1, z1, u1, vTop, 0, normalY, normalZ);
                p.vertex(x0, y1, z1, u0, vTop, 0, normalY, normalZ);
            }
        }
    }

    /** A step size that visits every tile exactly once before repeating, in a scattered order. */
    private static int scatterStep(int tiles) {
        for (int candidate : new int[] {7, 5, 11, 13, 3}) {
            if (candidate < tiles && greatestCommonDivisor(candidate, tiles) == 1) {
                return candidate;
            }
        }
        return 1;
    }

    private static int greatestCommonDivisor(int a, int b) {
        return b == 0 ? a : greatestCommonDivisor(b, a % b);
    }

    // ------------------------------------------------- telling the game what to draw

    /** NeoForge asks for the space the drawing takes up. Generous on purpose. */
    public AABB getRenderBoundingBox(ExcavatorBucketBlockEntity bucket) {
        AABB box = new AABB(bucket.getBlockPos()).inflate(bucket.isJoined() ? 10.0 : 1.5);
        // A rope reaches far beyond the bucket. The block that owns it has to cover it.
        ClientRopeStrand rope = ownedRope(bucket);
        if (rope != null && rope.getBounds() != null) {
            box = box.minmax(rope.getBounds().inflate(3.0));
        }
        return box;
    }

    /** A block that owns a rope is drawn even when the block itself is out of view, or the rope would vanish. */
    @Override
    public boolean shouldRenderOffScreen(ExcavatorBucketBlockEntity bucket) {
        return ownedRope(bucket) != null;
    }

    @Override
    public boolean shouldRender(ExcavatorBucketBlockEntity bucket, Vec3 cameraPos) {
        return ownedRope(bucket) != null || BlockEntityRenderer.super.shouldRender(bucket, cameraPos);
    }

    /** The rope this block owns in the player's game, or null. Of a rope's two ends, one owns it. */
    private static ClientRopeStrand ownedRope(ExcavatorBucketBlockEntity bucket) {
        RopeStrandHolderBehavior holder = bucket.getBehavior();
        return holder != null && holder.ownsRope() ? holder.getClientStrand() : null;
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Turns bucket coordinates into vertices. It knows which way the bucket is
     * turned, so the drawing code above never has to think about directions.
     */
    private static final class Painter {

        private final VertexConsumer buffer;
        private final PoseStack.Pose pose;
        private final int packedLight;
        // The bucket's own directions as plain numbers: right, up, back.
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

        /** One vertex. x, y, z in bucket pixels; the normal in bucket directions. */
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

        /** A box with its sides along the bucket's directions. */
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
