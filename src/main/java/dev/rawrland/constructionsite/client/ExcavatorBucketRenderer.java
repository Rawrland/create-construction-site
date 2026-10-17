package dev.rawrland.constructionsite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rawrland.constructionsite.content.bucket.BucketGroup;
import dev.rawrland.constructionsite.content.bucket.BucketMount;
import dev.rawrland.constructionsite.content.bucket.BucketTeeth;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlockEntity;
import dev.rawrland.constructionsite.compat.Mods;
import dev.rawrland.constructionsite.compat.aeronautics.client.BucketRopeRendering;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static dev.rawrland.constructionsite.client.BucketShape.BACK_START_Y;
import static dev.rawrland.constructionsite.client.BucketShape.BACK_Z;
import static dev.rawrland.constructionsite.client.BucketShape.OPENING_Z;
import static dev.rawrland.constructionsite.client.BucketShape.STEP_1_Y;
import static dev.rawrland.constructionsite.client.BucketShape.STEP_1_Z;
import static dev.rawrland.constructionsite.client.BucketShape.STEP_2_Z;
import static dev.rawrland.constructionsite.client.BucketShape.TOP_Y;

/**
 * Draws buckets.
 *
 * Bucket blocks have no block model of their own. The anchor block of each
 * bucket (bottom front left; for a single bucket, the block itself) draws the
 * whole bucket in code, in whatever size it has, plus its content.
 *
 * The bucket is made of boxes on the grid (see {@link BucketShape} for its
 * outline), each textured with the mod's own textures, one texture per block
 * of face, as Create's blocks are. Only the teeth reach past the front of the
 * bucket's blocks.
 *
 * All drawing below uses the bucket's own coordinates, in pixels (1/16 block):
 * x runs along the row of teeth, y up from the floor, z from the open face
 * toward the back, all counted from the anchor block's front bottom left corner.
 */
public class ExcavatorBucketRenderer implements BlockEntityRenderer<ExcavatorBucketBlockEntity> {

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
        // For a block without a rope of its own this draws nothing. Ropes need Create Aeronautics.
        if (Mods.AERONAUTICS) {
            BucketRopeRendering.renderRope(bucket, partialTick, poseStack, bufferSource);
        }
        // In a joined bucket only the anchor block draws.
        if (!bucket.isAnchor()) {
            return;
        }
        int height = bucket.getGroupHeight();
        int width = bucket.getGroupWidth();

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        ToolPainter painter = new ToolPainter(buffer, poseStack.last(), BucketGroup.axes(bucket.getBlockState()), packedLight);

        drawBucket(painter, height, width, bucket.getMount());
        drawRopeRing(painter, bucket, height, width, ToolPainter.plain(ToolPainter.DARK_STEEL));
        drawContent(painter, bucket, height, width);
    }

    /**
     * The empty bucket itself with its mount, in the given size. Used for placed
     * buckets and for the bucket item (see {@link ToolItemRenderer}), so both
     * always look the same.
     */
    static void drawBucket(ToolPainter painter, int height, int width, BucketMount mount) {
        ToolPainter.Material painted = ToolPainter.framed(ToolPainter.PAINTED);
        ToolPainter.Material teeth = ToolPainter.plain(ToolPainter.DARK_STEEL);
        ToolPainter.Material darkPlate = ToolPainter.framed(ToolPainter.DARK_STEEL);
        ToolPainter.Material pins = ToolPainter.plain(ToolPainter.DARK_STEEL);
        drawBody(painter, height, width * 16.0F, painted, teeth);
        drawMount(painter, mount, height, width * 16.0F, painted, darkPlate, pins);
    }

    // ------------------------------------------------------------- the bucket

    /**
     * The shell and the teeth.
     *
     * The shell is drawn as its surfaces, not as boxes: the outside (bottom, the
     * steps, back, roof and the two side walls) and the inside (floor, steps,
     * back, roof and the inner faces of the side walls). Each surface is one
     * connected panel with a frame only around its outer edge, so a big bucket
     * shows big calm plates instead of a grid of blocks. The wall thickness only
     * shows at the opening, as one yellow ring.
     */
    private static void drawBody(ToolPainter p, int height, float totalWidth,
                                 ToolPainter.Material painted, ToolPainter.Material teeth) {
        float s = height;
        float t = BucketShape.thickness(s);
        float w = totalWidth;
        float open = OPENING_Z * s;
        float top = TOP_Y * s;
        float step1Z = STEP_1_Z * s, step1Y = STEP_1_Y * s;
        float step2Z = STEP_2_Z * s, backY = BACK_START_Y * s;
        float backZ = BACK_Z * s;

        // The front of the walls around the opening: one ring.
        boolean[][] ring = {{true, true, true}, {true, false, true}, {true, true, true}};
        p.panel(painted, 2, -1, open, new float[] {0, t, w - t, w}, new float[] {0, t, top - t, top}, ring);

        // Outside: bottom, first step (back and underside), second step, back, roof.
        p.rect(painted, 1, -1, 0, 0, w, open, step1Z);
        p.rect(painted, 2, 1, step1Z, 0, w, 0, step1Y);
        p.rect(painted, 1, -1, step1Y, 0, w, step1Z, step2Z);
        p.rect(painted, 2, 1, step2Z, 0, w, step1Y, backY);
        p.rect(painted, 1, -1, backY, 0, w, step2Z, backZ);
        p.rect(painted, 2, 1, backZ, 0, w, backY, top);
        p.rect(painted, 1, 1, top, 0, w, open, backZ);
        // Outside of the side walls: the stepped outline as a grid of cells.
        boolean[][] steps = {{true, true, true}, {false, true, true}, {false, false, true}};
        float[] outerZ = {open, step1Z, step2Z, backZ};
        float[] outerY = {0, step1Y, backY, top};
        p.panel(painted, 0, -1, 0, outerZ, outerY, steps);
        p.panel(painted, 0, 1, w, outerZ, outerY, steps);

        // Inside: floor, first step (front and top), second step, back, roof.
        float in0 = t, in1 = w - t;
        float innerStep1Z = step1Z - t, innerStep2Z = step2Z - t, innerBackZ = backZ - t;
        p.rect(painted, 1, 1, t, in0, in1, open, innerStep1Z);
        p.rect(painted, 2, -1, innerStep1Z, in0, in1, t, step1Y + t);
        p.rect(painted, 1, 1, step1Y + t, in0, in1, innerStep1Z, innerStep2Z);
        p.rect(painted, 2, -1, innerStep2Z, in0, in1, step1Y + t, backY + t);
        p.rect(painted, 1, 1, backY + t, in0, in1, innerStep2Z, innerBackZ);
        p.rect(painted, 2, -1, innerBackZ, in0, in1, backY + t, top - t);
        p.rect(painted, 1, -1, top - t, in0, in1, open, innerBackZ);
        // Inside of the side walls.
        float[] innerZ = {open, innerStep1Z, innerStep2Z, innerBackZ};
        float[] innerY = {t, step1Y + t, backY + t, top - t};
        p.panel(painted, 0, 1, in0, innerZ, innerY, steps);
        p.panel(painted, 0, -1, in1, innerZ, innerY, steps);

        // Teeth in front of the floor: a thicker base and a thinner tip each.
        // They are the only part of the bucket that reaches past the front of its blocks.
        // Spread evenly: every gap is the same, and half a gap is left at each edge of a block,
        // so the gap between the teeth of two neighbouring blocks is the same again.
        int perBlock = BucketTeeth.perBlock(height);
        int blocksWide = Math.round(w / 16.0F);
        for (int block = 0; block < blocksWide; block++) {
            for (int tooth = 0; tooth < perBlock; tooth++) {
                float x = BucketTeeth.toothLeft(block, tooth, height);
                p.box(teeth, x, 0, open - 2.5F * s, x + 2.0F * s, 1.5F * s, open, ToolPainter.BACK);
                p.box(teeth, x + 0.25F * s, 0.2F * s, open - 4.0F * s, x + 1.75F * s, 0.9F * s, open - 2.5F * s,
                    ToolPainter.BACK);
            }
        }
    }

    // ---------------------------------------------------------- the rope ring

    /** A small steel ring standing on every tooth that has a rope tied to it. */
    private static void drawRopeRing(ToolPainter p, ExcavatorBucketBlockEntity bucket, int height, int width,
                                     ToolPainter.Material steel) {
        float s = height;
        for (int column = 0; column < width; column++) {
            ExcavatorBucketBlockEntity hook = bucket.getHookBlock(column);
            if (hook == null || !showsRing(hook)) {
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

    /**
     * True if a ring is drawn on this block's tooth: a rope is tied to it, or, in a Ponder
     * scene, the scene shows a rope tied to it. Create Aeronautics' own Ponder scenes mark a
     * shown rope the same way: the rope holder's renderAttached on a virtual block entity.
     */
    private static boolean showsRing(ExcavatorBucketBlockEntity hook) {
        if (hook.hasRope()) {
            return true;
        }
        return Mods.AERONAUTICS && BucketRopeRendering.showsRopeInPonder(hook);
    }

    // -------------------------------------------------------------- the mount

    /**
     * Two ears and a pin through both, where an arm would hold the bucket:
     * on the roof, on the back, or not drawn at all. The ears stand on a
     * dark steel base plate that spreads the load into the shell.
     */
    private static void drawMount(ToolPainter p, BucketMount mount, int height, float totalWidth,
                                  ToolPainter.Material painted, ToolPainter.Material plate,
                                  ToolPainter.Material steel) {
        if (mount == BucketMount.NONE) {
            return;
        }
        float s = height;
        // One ear near each end of the width, the pin from one to the other.
        // On a bucket that is tall but only one block wide, the ears move in and get
        // thinner, so that there are still two of them with a gap between.
        float inset = Math.min(3.5F * s, totalWidth * 0.2F);
        float earWidth = Math.min(1.5F * s, totalWidth * 0.12F);
        float[] earStarts = {inset, totalWidth - inset - earWidth};
        float pinFrom = inset - 0.3F * earWidth;
        float pinTo = totalWidth - inset + 0.3F * earWidth;
        float plateThickness = 0.5F * s;
        float plateFrom = inset - 0.8F * earWidth;
        float plateTo = totalWidth - inset + 0.8F * earWidth;

        if (mount == BucketMount.TOP) {
            float top = TOP_Y * s;
            p.box(plate, plateFrom, top, 6.0F * s, plateTo, top + plateThickness, 11.6F * s, ToolPainter.BOTTOM);
            float base = top + plateThickness;
            for (float x : earStarts) {
                p.box(painted, x, base, 6.6F * s, x + earWidth, base + 1.6F * s, 11.0F * s);
                p.box(painted, x, base + 1.6F * s, 7.4F * s, x + earWidth, base + 3.4F * s, 10.2F * s);
            }
            p.box(steel, pinFrom, base + 1.6F * s, 8.2F * s, pinTo, base + 2.6F * s, 9.4F * s);
        } else {
            // The same ears turned onto the straight back, centred on its height.
            float middle = (BACK_START_Y + TOP_Y) * 0.5F * s;
            p.box(plate, plateFrom, middle - 2.8F * s, BACK_Z * s, plateTo, middle + 2.8F * s, BACK_Z * s + plateThickness, ToolPainter.FRONT);
            float back = BACK_Z * s + plateThickness;
            for (float x : earStarts) {
                p.box(painted, x, middle - 2.2F * s, back, x + earWidth, middle + 2.2F * s, back + 1.6F * s);
                p.box(painted, x, middle - 1.4F * s, back + 1.6F * s, x + earWidth, middle + 1.4F * s, back + 3.4F * s);
            }
            p.box(steel, pinFrom, middle - 0.6F * s, back + 1.6F * s, pinTo, middle + 0.6F * s, back + 2.6F * s);
        }
    }

    // ------------------------------------------------------------ the content

    /**
     * A level surface across the inside of the bucket. It rises from the floor
     * toward the roof as the bucket fills, and reaches from the opening back to
     * the inside of the back or of the step at that height. A front face at the
     * opening, from the floor up to the surface, makes the load look solid.
     *
     * The faces are cut into tiles one block in size, on the same grid as the
     * bucket's own textures. Each tile shows the texture of one stored block, so
     * nothing is stretched and a mixed load looks mixed. If there are more stored
     * blocks than tiles, the most recently picked up ones are shown.
     */
    private void drawContent(ToolPainter p, ExcavatorBucketBlockEntity bucket, int height, int width) {
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

        float s = height;
        float t = BucketShape.thickness(s);
        // The surface's height: a hair above the floor when nearly empty, a hair under the roof when full,
        // so it never lies exactly on a face of the bucket.
        float bottom = t;
        float room = TOP_Y * s - 2.0F * t;
        float surfaceY = bottom + 0.05F + fill * (room - 0.15F);
        float frontZ = OPENING_Z * s + 0.05F;
        float backZ = BucketShape.innerBackAt(surfaceY, s);

        // Tiles of the surface: one column per block of width, rows on the 16-pixel grid along z.
        int firstRow = (int) Math.floor(frontZ / 16.0F);
        int lastRow = (int) Math.ceil(backZ / 16.0F) - 1;
        int rows = Math.max(1, lastRow - firstRow + 1);
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

        float totalWidth = width * 16.0F;
        for (int column = 0; column < width; column++) {
            // The outermost columns are narrower, because the side walls take up room.
            float x0 = Math.max(column * 16.0F, t);
            float x1 = Math.min((column + 1) * 16.0F, totalWidth - t);

            // The level surface, row by row.
            for (int row = 0; row < rows; row++) {
                float z0 = Math.max((firstRow + row) * 16.0F, frontZ);
                float z1 = Math.min((firstRow + row + 1) * 16.0F, backZ);
                p.face(contentSprite(tileContent[column * rows + row]), 1, 1, surfaceY, x0, x1, z0, z1);
            }

            // The front face at the opening, in 16-pixel rows from the floor up.
            int frontRows = (int) Math.ceil(surfaceY / 16.0F);
            for (int row = 0; row < frontRows; row++) {
                float y0 = Math.max(row * 16.0F, bottom);
                float y1 = Math.min((row + 1) * 16.0F, surfaceY);
                BlockState state = tileContent[column * rows + (row % rows)];
                p.face(contentSprite(state), 2, -1, frontZ, x0, x1, y0, y1);
            }
        }
    }

    /** The main texture of a block: the one also used for its break particles. */
    private TextureAtlasSprite contentSprite(BlockState state) {
        return blockRenderer.getBlockModelShaper().getParticleIcon(state);
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
        AABB rope = ownedRopeBounds(bucket);
        if (rope != null) {
            box = box.minmax(rope.inflate(3.0));
        }
        return box;
    }

    /** A block that owns a rope is drawn even when the block itself is out of view, or the rope would vanish. */
    @Override
    public boolean shouldRenderOffScreen(ExcavatorBucketBlockEntity bucket) {
        return ownedRopeBounds(bucket) != null;
    }

    @Override
    public boolean shouldRender(ExcavatorBucketBlockEntity bucket, Vec3 cameraPos) {
        return ownedRopeBounds(bucket) != null || BlockEntityRenderer.super.shouldRender(bucket, cameraPos);
    }

    /**
     * The space taken by the rope this block owns in the player's game, or null.
     * Of a rope's two ends, one owns it. Always null without Create Aeronautics.
     */
    private static AABB ownedRopeBounds(ExcavatorBucketBlockEntity bucket) {
        return Mods.AERONAUTICS ? BucketRopeRendering.ownedRopeBounds(bucket) : null;
    }
}
