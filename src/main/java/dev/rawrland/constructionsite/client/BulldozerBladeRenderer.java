package dev.rawrland.constructionsite.client;

import dev.rawrland.constructionsite.content.tool.ToolPaint;
import javax.annotation.Nullable;
import net.minecraft.world.item.DyeColor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rawrland.constructionsite.content.blade.BladeGroup;
import dev.rawrland.constructionsite.content.blade.BladeMount;
import dev.rawrland.constructionsite.content.blade.BladePile;
import dev.rawrland.constructionsite.content.blade.BulldozerBladeBlockEntity;
import dev.rawrland.constructionsite.content.bucket.BucketGroup;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Vec3i;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Draws bulldozer blades.
 *
 * Blade blocks have no block model of their own. The anchor block of each blade
 * (bottom left; for a single blade, the block itself) draws the whole blade in
 * code: a few boxes on the grid, each face one connected surface with a frame
 * around its edge (see {@link ToolPainter}), as Create's connected blocks look. It also draws the load as
 * a pile of blocks in front of the blade.
 *
 * All drawing below uses the blade's own coordinates, in pixels (1/16 block):
 * x runs along the cutting edge, y up from the bottom, z from the working face
 * toward the back, all counted from the anchor block's front bottom left corner.
 */
public class BulldozerBladeRenderer implements BlockEntityRenderer<BulldozerBladeBlockEntity> {

    /**
     * The side profile for a 1-high blade, in pixels: z from the working face
     * backward, y up. Three boxes across the whole width:
     * the dark steel cutting edge at the bottom (z 0 to 3, y 0 to 4), the plate in the
     * middle (z 3 to 5, y 4 to 12) and the lip at the top (z 0 to 3, y 12 to 16).
     */
    private static final float EDGE_DEPTH = 3.0F;
    private static final float EDGE_TOP = 4.0F;
    private static final float PLATE_BACK = 5.0F;
    private static final float LIP_BOTTOM = 12.0F;
    /** Thickness of the end plates at both ends of the blade, for a 1-high blade. */
    private static final float END_PLATE = 1.0F;
    /** How far the stiffening ribs stand out behind the plate. Less than the mount's beam. */
    private static final float RIB_DEPTH = 1.0F;

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

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        ToolPainter painter = new ToolPainter(buffer, poseStack.last(), axes, packedLight);
        drawBlade(painter, height, width, blade.getMount(), ToolPaint.of(blade.getBlockState().getBlock()));

        // The pile comes last: drawing blocks switches to other buffers, and the
        // painter's buffer must not be used any more after that.
        drawPile(blade, axes, partialTick, poseStack, bufferSource, packedLight);
    }

    // --------------------------------------------------------------- the blade

    /**
     * The empty blade itself with its mount, in the given size. Used for placed
     * blades and for the blade item (see {@link ToolItemRenderer}), so both
     * always look the same.
     */
    static void drawBlade(ToolPainter painter, int height, int width, BladeMount mount, @Nullable DyeColor colour) {
        ToolPainter.Material painted = ToolPainter.body(colour);
        ToolPainter.Material steel = ToolPainter.framed(ToolPainter.DARK_STEEL);
        ToolPainter.Material dark = ToolPainter.plain(ToolPainter.DARK_STEEL);
        ToolPainter.Material edge = ToolPainter.framed(ToolPainter.edge(colour));
        drawPlate(painter, height, width * 16.0F, painted, steel, edge);
        drawMount(painter, mount, height, width * 16.0F, painted, dark);
    }

    /**
     * The blade itself: cutting edge, plate and lip, each a box across the whole
     * width; a dark steel end plate closing each end; and upright ribs on the back
     * of the plate, one in the middle of every block, that stiffen it.
     */
    private static void drawPlate(ToolPainter p, float s, float totalWidth,
                                  ToolPainter.Material painted, ToolPainter.Material steel, ToolPainter.Material edge) {
        float d = depthScale(s);
        float end = END_PLATE * s;
        float plateBack = PLATE_BACK * d;
        p.box(edge, end, 0, 0, totalWidth - end, EDGE_TOP * s, EDGE_DEPTH * d);
        p.box(painted, end, EDGE_TOP * s, EDGE_DEPTH * d, totalWidth - end, LIP_BOTTOM * s, plateBack);
        p.box(painted, end, LIP_BOTTOM * s, 0, totalWidth - end, 16.0F * s, EDGE_DEPTH * d);

        // End plates: the whole side outline of the blade, filled in.
        for (float x : new float[] {0, totalWidth - end}) {
            p.box(steel, x, 0, 0, x + end, 16.0F * s, plateBack);
        }

        // Ribs: shallower than the mount's beam, so their back faces never lie on the beam's.
        int blocks = Math.round(totalWidth / 16.0F);
        float ribHalf = 0.75F * s;
        for (int block = 0; block < blocks; block++) {
            float middle = block * 16.0F + 8.0F;
            p.box(painted, middle - ribHalf, EDGE_TOP * s, plateBack,
                middle + ribHalf, LIP_BOTTOM * s, plateBack + RIB_DEPTH * d);
        }
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
    private static void drawMount(ToolPainter p, BladeMount mount, float s, float totalWidth,
                                  ToolPainter.Material body, ToolPainter.Material steel) {
        if (mount == BladeMount.NONE) {
            return;
        }
        float d = depthScale(s);
        float plateBack = PLATE_BACK * d;
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
        long now = clock(blade.getLevel());
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
                float age = (float) (now - block.takenAt()) + partialTick;
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

    /**
     * The time the slide-in of taken blocks is measured in. In a world that is the
     * game time, which is also what the blade stores when it takes a block. Create's
     * Ponder scenes pause the game, so there the game time stands still; a scene
     * counts in Ponder's own ticks instead, and stores those (see ToolSceneHelper).
     */
    public static long clock(Level level) {
        return level instanceof PonderLevel ? AnimationTickHolder.getTicks(level) : level.getGameTime();
    }

    // ------------------------------------------------- telling the game what to draw

    /** NeoForge asks for the space the drawing takes up: the whole blade and its pile, not just the anchor block. */
    public AABB getRenderBoundingBox(BulldozerBladeBlockEntity blade) {
        return new AABB(blade.getBlockPos()).inflate(blade.isJoined() ? BladeGroup.MAX_WIDTH + 1.0 : 4.0);
    }
}
