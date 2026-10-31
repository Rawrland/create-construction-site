package dev.rawrland.constructionsite.client;

import dev.rawrland.constructionsite.content.tool.ToolPaint;
import net.minecraft.world.item.DyeColor;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.bucket.BucketGroup;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyBlock;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyBlockEntity;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyGroup;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws dump bodies.
 *
 * Dump body blocks have no block model of their own. The anchor block (the
 * corner with the lowest x, y and z) draws the whole body in code, with
 * {@link ToolPainter}: a floor, four dark steel corner posts, and on each side
 * either a tailgate or a fixed wall, with a dark steel rim along the top. Large
 * surfaces are one connected plate, so the number of faces does not depend on
 * the body's size.
 *
 * New dump bodies are closed on all sides. A side opened with the wrench is a tailgate: a panel of its own between the posts,
 * hanging from hinge pins under the rim, with a latch hook on each post at its
 * bottom corners. A side that is always closed is a fixed wall up to the rim,
 * with a dark steel locking bar across it. So the wrench setting can be seen.
 *
 * While the body empties, the low side's tailgate swings open about its top
 * edge, hanging plumb: it turns outward by the angle the body is tilted.
 *
 * The load is drawn as the blocks it is, inside the walls, never higher than
 * them. Only blocks that can be seen are drawn: in each column, those above the
 * lowest neighbouring column (the walls count as full, an open gate as empty).
 *
 * Coordinates are in pixels from the anchor block's lower north-west corner:
 * x toward east, y up, z toward south. The body lines up with the world's axes.
 */
public class DumpBodyRenderer implements BlockEntityRenderer<DumpBodyBlockEntity> {

    /** The painter's directions: the body has no facing, so they are the world's. */
    static final BucketGroup.Axes AXES = new BucketGroup.Axes(Direction.EAST, Direction.UP, Direction.SOUTH);

    /** Thickness of the floor plate, in pixels. */
    private static final float FLOOR = 2.0F;
    /** How thick the corner stakes, the top rail and the walls are, measured from the outer face. */
    private static final float POST = 3.0F;
    /** Height of the boxed top rail along every side. */
    private static final float RAIL = 3.0F;
    /** A gate opens or shuts over this many ticks. Set by the design. */
    private static final float GATE_TICKS = 4.0F;
    /** An open gate never swings further than this from the body, in degrees. */
    private static final float MAX_GATE_ANGLE = 80.0F;

    private final BlockRenderDispatcher blockRenderer;

    public DumpBodyRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(DumpBodyBlockEntity body, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (body.isRemoved() || body.getLevel() == null
            || body.getLevel().getBlockEntity(body.getBlockPos()) != body) {
            return;
        }
        if (!body.isAnchor()) {
            return;
        }
        // The level's block state, not the block entity's copy: in a Ponder scene the
        // copy keeps the first state, while the scene changes the gates in the level.
        BlockState state = body.getLevel().getBlockState(body.getBlockPos());
        if (!(state.getBlock() instanceof DumpBodyBlock)) {
            state = body.getBlockState();
        }
        int sx = body.getSizeX();
        int sy = body.getSizeY();
        int sz = body.getSizeZ();

        // Ease the gates toward open or shut.
        float now = BulldozerBladeRenderer.clock(body.getLevel()) + partialTick;
        float elapsed = body.gateAnimatedAt == Long.MIN_VALUE ? GATE_TICKS : now - body.gateAnimatedAt;
        body.gateAnimatedAt = (long) now;
        Direction openSide = body.getOpenSide();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int index = side.get2DDataValue();
            float target = side == openSide ? 1.0F : 0.0F;
            float step = Math.max(0.0F, elapsed) / GATE_TICKS;
            float progress = body.gateOpenProgress[index];
            progress = target > progress ? Math.min(target, progress + step) : Math.max(target, progress - step);
            body.gateOpenProgress[index] = progress;
        }
        float openAngle = Math.min(MAX_GATE_ANGLE, Math.max(0.0F, body.getOpenAngle()));

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        drawBody(buffer, poseStack, packedLight, sx, sy, sz, state, body.gateOpenProgress, openAngle);

        // The load comes last: drawing blocks switches to other buffers.
        drawLoad(body, sx, sy, sz, openSide, poseStack, bufferSource, packedLight);
    }

    // ---------------------------------------------------------------- the body
    //
    // Modelled on real tipper bodies (Schwarzmueller's three-way tippers, Rugby's steel
    // dump bodies): fixed corner stakes, a boxed top rail, side walls stiffened by
    // vertical braces, tailgates with a boxed edge and a middle rib, hinges at the top
    // and swivel-bolt locks at the bottom corners, and a steel floor plate. Everything
    // stays inside the blocks: panels sit 1 pixel back from the outer face, and the
    // braces, ribs, hinges and locks fill that pixel. Only the tailgate levers stand half
    // a pixel proud, so they do not share a face with the stakes.

    /** An empty new body, closed on all sides as it is placed, for the item (see {@link ToolItemRenderer}). */
    static void drawEmptyBody(ToolPainter painter, int sx, int sy, int sz, @Nullable DyeColor colour) {
        Parts parts = new Parts(colour);
        float w = sx * 16.0F;
        float h = sy * 16.0F;
        float d = sz * 16.0F;
        drawFrame(painter, parts, w, h, d);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            drawSide(painter, parts, side, false, w, h, d, true);
        }
    }

    private static final class Parts {
        final ToolPainter.Material painted;

        Parts(@Nullable DyeColor colour) {
            painted = ToolPainter.body(colour);
        }

        final ToolPainter.Material steel = ToolPainter.framed(ToolPainter.DARK_STEEL);
        final ToolPainter.Material dark = ToolPainter.plain(ToolPainter.DARK_STEEL);
        final ToolPainter.Material tread = new ToolPainter.Material(ToolPainter.sprite(texture("tread_plate")), null);
        final ToolPainter.Material hazard = new ToolPainter.Material(ToolPainter.sprite(texture("hazard_stripes")), null);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, "block/" + name);
    }

    private static void drawBody(VertexConsumer buffer, PoseStack poseStack, int light, int sx, int sy, int sz,
                                 BlockState state, float[] progress, float openAngle) {
        Parts parts = new Parts(ToolPaint.of(state.getBlock()));
        float w = sx * 16.0F;
        float h = sy * 16.0F;
        float d = sz * 16.0F;
        ToolPainter painter = new ToolPainter(buffer, poseStack.last(), AXES, light);
        drawFrame(painter, parts, w, h, d);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean mayOpen = DumpBodyBlock.mayOpen(state, side);
            float angle = mayOpen ? progress[side.get2DDataValue()] * openAngle : 0.0F;
            if (angle < 0.01F) {
                drawSide(painter, parts, side, mayOpen, w, h, d, true);
                continue;
            }
            // The tailgate turns about its hinge line: the top of the panel, at the body's outer face.
            drawSide(painter, parts, side, true, w, h, d, false);
            poseStack.pushPose();
            float hingeY = (h - RAIL) / 16.0F;
            switch (side) {
                case NORTH -> {
                    poseStack.translate(0, hingeY, 0);
                    poseStack.mulPose(Axis.XP.rotationDegrees(angle));
                    poseStack.translate(0, -hingeY, 0);
                }
                case SOUTH -> {
                    poseStack.translate(0, hingeY, d / 16.0F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-angle));
                    poseStack.translate(0, -hingeY, -d / 16.0F);
                }
                case WEST -> {
                    poseStack.translate(0, hingeY, 0);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-angle));
                    poseStack.translate(0, -hingeY, 0);
                }
                default -> {
                    poseStack.translate(w / 16.0F, hingeY, 0);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
                    poseStack.translate(-w / 16.0F, -hingeY, 0);
                }
            }
            drawTailgate(new ToolPainter(buffer, poseStack.last(), AXES, light), parts, side, w, h, d);
            poseStack.popPose();
        }
    }

    /** The floor plate (tread plate inside) and the four corner stakes. */
    private static void drawFrame(ToolPainter p, Parts parts, float w, float h, float d) {
        // The floor covers the whole footprint, under the walls too, so nothing can be
        // seen through at the bottom edge, also not under an open tailgate.
        // Dark steel outside: its sides, its underside, and the strip of its top that shows
        // in front of the walls. Inside the walls the top is tread plate (owner, 20 October 2026).
        p.box(parts.steel, 0, 0, 0, w, FLOOR, d, ToolPainter.TOP);
        p.face(parts.tread.fill(), 1, 1, FLOOR, POST, w - POST, POST, d - POST);
        TextureAtlasSprite edge = parts.dark.fill();
        p.face(edge, 1, 1, FLOOR, 0, w, 0, POST);
        p.face(edge, 1, 1, FLOOR, 0, w, d - POST, d);
        p.face(edge, 1, 1, FLOOR, 0, POST, POST, d - POST);
        p.face(edge, 1, 1, FLOOR, w - POST, w, POST, d - POST);
        float[][] corners = {{0, 0}, {w - POST, 0}, {0, d - POST}, {w - POST, d - POST}};
        for (float[] c : corners) {
            p.box(parts.steel, c[0], FLOOR, c[1], c[0] + POST, h, c[1] + POST);
        }
    }

    /**
     * One side: the boxed top rail, and either a fixed wall (braced, with a locking bar
     * across it) or a tailgate's hinges and swivel-bolt locks, with the tailgate itself
     * if {@code withGate} (it is drawn separately while it swings open).
     */
    private static void drawSide(ToolPainter p, Parts parts, Direction side, boolean mayOpen,
                                 float w, float h, float d, boolean withGate) {
        float length = sideLength(side, w, d);
        int blocks = Math.round(length / 16.0F);
        // The boxed top rail between the stakes.
        sideBox(p, parts.steel, side, h - RAIL, h, 0, POST, w, d, POST, length - POST);
        if (!mayOpen) {
            // A fixed wall: the panel, 1 pixel back from the outer face ...
            sideBox(p, parts.painted, side, FLOOR, h - RAIL, 1.0F, POST, w, d, POST, length - POST);
            // ... vertical braces where two blocks meet ...
            for (int block = 1; block < blocks; block++) {
                float at = block * 16.0F;
                sideBox(p, parts.painted, side, FLOOR, h - RAIL, 0.5F, 1.0F, w, d, at - 1.5F, at + 1.5F);
            }
            // ... and a dark steel locking bar across the wall, held by a bracket next to each stake.
            // Bar and brackets touch the stakes but never reach into them, and the bar runs only
            // between the brackets: overlapping boxes would share a face and flicker.
            float barMiddle = (FLOOR + h - RAIL) / 2.0F;
            float bracket = 2.5F;
            sideBox(p, parts.dark, side, barMiddle - 1.0F, barMiddle + 1.0F, 0.25F, 1.0F, w, d,
                POST + bracket, length - POST - bracket);
            sideBox(p, parts.dark, side, barMiddle - 2.0F, barMiddle + 2.0F, 0, 1.0F, w, d,
                POST, POST + bracket);
            sideBox(p, parts.dark, side, barMiddle - 2.0F, barMiddle + 2.0F, 0, 1.0F, w, d,
                length - POST - bracket, length - POST);
            return;
        }
        // Swivel-bolt locks on the stakes at the tailgate's bottom corners: a lever and its bolt.
        // The lever stands half a pixel proud of the stake: with its front in the stake's own
        // face the two would flicker (owner, 20 October 2026).
        for (float at : new float[] {0.5F, length - POST + 0.5F}) {
            sideBox(p, parts.dark, side, FLOOR + 0.5F, FLOOR + 5.0F, -0.5F, 0.5F, w, d, at, at + 2.0F);
        }
        // The bolts start at the stakes, not inside them (a shared face would flicker).
        sideBox(p, parts.dark, side, FLOOR + 1.5F, FLOOR + 3.0F, 0, 1.0F, w, d, POST, POST + 2.0F);
        sideBox(p, parts.dark, side, FLOOR + 1.5F, FLOOR + 3.0F, 0, 1.0F, w, d,
            length - POST - 2.0F, length - POST);
        // Hinges under the rail: one at each end, and one in the middle of every block between.
        for (int block = 0; block < blocks; block++) {
            float middle = block == 0 ? POST + 3.0F : block == blocks - 1 ? length - POST - 3.0F : block * 16.0F + 8.0F;
            sideBox(p, parts.dark, side, h - RAIL - 2.5F, h - RAIL, 0, 1.0F, w, d,
                middle - 2.0F, middle + 2.0F);
            if (blocks == 1) {
                break;
            }
        }
        if (withGate) {
            drawTailgate(p, parts, side, w, h, d);
        }
    }

    /**
     * The tailgate: a panel filling the whole opening between floor, stakes and rail
     * (so its edges are closed), 1 pixel back from the outer face, with a boxed edge,
     * a rib across its middle and one where two blocks meet, and warning stripes
     * along its bottom edge, as real tipper tailgates carry.
     */
    private static void drawTailgate(ToolPainter p, Parts parts, Direction side, float w, float h, float d) {
        float length = sideLength(side, w, d);
        int blocks = Math.round(length / 16.0F);
        float top = h - RAIL;
        sideBox(p, parts.painted, side, FLOOR, top, 1.0F, POST, w, d, POST, length - POST);
        // The boxed edge: along the top and both ends; the bottom edge carries the stripes.
        sideBox(p, parts.painted, side, top - 1.5F, top, 0.5F, 1.0F, w, d, POST, length - POST);
        sideBox(p, parts.painted, side, FLOOR + 2.0F, top - 1.5F, 0.5F, 1.0F, w, d, POST, POST + 1.5F);
        sideBox(p, parts.painted, side, FLOOR + 2.0F, top - 1.5F, 0.5F, 1.0F, w, d,
            length - POST - 1.5F, length - POST);
        sideBox(p, parts.hazard, side, FLOOR, FLOOR + 2.0F, 0.5F, 1.0F, w, d, POST, length - POST);
        // A rib across the middle, and one where two blocks meet.
        float middle = (FLOOR + 2.0F + top - 1.5F) / 2.0F;
        sideBox(p, parts.painted, side, middle - 0.75F, middle + 0.75F, 0.5F, 1.0F, w, d,
            POST + 1.5F, length - POST - 1.5F);
        for (int block = 1; block < blocks; block++) {
            float at = block * 16.0F;
            sideBox(p, parts.painted, side, FLOOR + 2.0F, top - 1.5F, 0.5F, 1.0F, w, d, at - 0.75F, at + 0.75F);
        }
    }

    /** How long a side is, in pixels. */
    private static float sideLength(Direction side, float w, float d) {
        return side.getAxis() == Direction.Axis.Z ? w : d;
    }

    /**
     * A box on one side. Along the side (west to east, or north to south) it runs
     * from {@code along0} to {@code along1}; across, from {@code out0} to
     * {@code out1}, measured inward from the side's outer face.
     */
    private static void sideBox(ToolPainter p, ToolPainter.Material m, Direction side,
                                float y0, float y1, float out0, float out1, float w, float d,
                                float along0, float along1) {
        switch (side) {
            case NORTH -> p.box(m, along0, y0, out0, along1, y1, out1);
            case SOUTH -> p.box(m, along0, y0, d - out1, along1, y1, d - out0);
            case WEST -> p.box(m, out0, y0, along0, out1, y1, along1);
            default -> p.box(m, w - out1, y0, along0, w - out0, y1, along1);
        }
    }

    // ---------------------------------------------------------------- the load

    /**
     * The load: in each column, the blocks that can be seen, each drawn as the
     * block it is, fitted inside the walls and above the floor.
     */
    private void drawLoad(DumpBodyBlockEntity body, int sx, int sy, int sz, Direction openSide,
                          PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float[] lean = body.getLean();
        if (lean[1] > -SETTLE_FROM_COS) {
            drawSettledLoad(body, sx, sy, sz, openSide, lean, poseStack, bufferSource, packedLight);
            return;
        }
        int[][] heights = new int[sx][sz];
        BlockState[][][] blocks = new BlockState[sx][sy][sz];
        boolean any = false;
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                for (int y = 0; y < sy; y++) {
                    DumpBodyBlockEntity cell = body.cell(x, y, z);
                    BlockState stored = cell == null ? null : cell.getStoredState();
                    if (stored == null) {
                        break;
                    }
                    blocks[x][y][z] = stored;
                    heights[x][z] = y + 1;
                    any = true;
                }
            }
        }
        if (!any) {
            return;
        }
        float inner = POST / 16.0F;
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                int lowestBeside = Math.min(
                    Math.min(neighbour(heights, x - 1, z, sy, openSide == Direction.WEST),
                        neighbour(heights, x + 1, z, sy, openSide == Direction.EAST)),
                    Math.min(neighbour(heights, x, z - 1, sy, openSide == Direction.NORTH),
                        neighbour(heights, x, z + 1, sy, openSide == Direction.SOUTH)));
                int from = Math.max(0, Math.min(lowestBeside, heights[x][z] - 1));
                for (int y = from; y < heights[x][z]; y++) {
                    // The block's place, fitted inside the walls and above the floor.
                    float x0 = Math.max(x, inner);
                    float x1 = Math.min(x + 1, sx - inner);
                    float z0 = Math.max(z, inner);
                    float z1 = Math.min(z + 1, sz - inner);
                    float y0 = Math.max(y, FLOOR / 16.0F);
                    float y1 = y + 1;
                    poseStack.pushPose();
                    poseStack.translate(x0, y0, z0);
                    poseStack.scale(x1 - x0, y1 - y0, z1 - z0);
                    blockRenderer.renderSingleBlock(blocks[x][y][z], poseStack, bufferSource, packedLight,
                        OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                }
            }
        }
    }

    /** The load is drawn settled toward "down" once the body leans more than this (cosine of 15 degrees). */
    private static final double SETTLE_FROM_COS = Math.cos(Math.toRadians(15.0));

    /**
     * A tilted body: its load is drawn as loose material that has slid toward the
     * low side, only for show (owner's request, 17 October 2026). The blocks fill the
     * cells that lie lowest along the body's current "down", like sand settling;
     * the stored block that lies lowest is drawn in the lowest of them, and so on.
     * Where the blocks are stored, and what leaves first, does not change.
     */
    private void drawSettledLoad(DumpBodyBlockEntity body, int sx, int sy, int sz, Direction openSide, float[] down,
                                 PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // Every cell, lowest along "down" first; ties keep a fixed order.
        int count = sx * sy * sz;
        Integer[] order = new Integer[count];
        double[] lowness = new double[count];
        for (int i = 0; i < count; i++) {
            int x = i / (sy * sz), y = (i / sz) % sy, z = i % sz;
            order[i] = i;
            lowness[i] = down[0] * (x + 0.5) + down[1] * (y + 0.5) + down[2] * (z + 0.5);
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(lowness[b], lowness[a]));
        // What is stored, in the same order (lowest first).
        java.util.List<BlockState> load = new java.util.ArrayList<>();
        for (Integer index : order) {
            int x = index / (sy * sz), y = (index / sz) % sy, z = index % sz;
            DumpBodyBlockEntity cell = body.cell(x, y, z);
            if (cell != null && cell.getStoredState() != null) {
                load.add(cell.getStoredState());
            }
        }
        if (load.isEmpty()) {
            return;
        }
        BlockState[][][] shown = new BlockState[sx][sy][sz];
        for (int i = 0; i < load.size(); i++) {
            int index = order[i];
            shown[index / (sy * sz)][(index / sz) % sy][index % sz] = load.get(i);
        }
        float inner = POST / 16.0F;
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    BlockState state = shown[x][y][z];
                    if (state == null || !isSeen(shown, x, y, z, sx, sy, sz, openSide, steps)) {
                        continue;
                    }
                    float x0 = Math.max(x, inner);
                    float x1 = Math.min(x + 1, sx - inner);
                    float z0 = Math.max(z, inner);
                    float z1 = Math.min(z + 1, sz - inner);
                    float y0 = Math.max(y, FLOOR / 16.0F);
                    float y1 = y + 1;
                    poseStack.pushPose();
                    poseStack.translate(x0, y0, z0);
                    poseStack.scale(x1 - x0, y1 - y0, z1 - z0);
                    blockRenderer.renderSingleBlock(state, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                }
            }
        }
    }

    /** True if a drawn block of the load can be seen: something around it is empty, or it lies at the open top or an open gate. */
    private static boolean isSeen(BlockState[][][] shown, int x, int y, int z, int sx, int sy, int sz,
                                  Direction openSide, int[][] steps) {
        for (int[] step : steps) {
            int nx = x + step[0], ny = y + step[1], nz = z + step[2];
            if (nx >= 0 && ny >= 0 && nz >= 0 && nx < sx && ny < sy && nz < sz) {
                if (shown[nx][ny][nz] == null) {
                    return true;
                }
                continue;
            }
            // Outside the body: the open top, or an open gate, lets it be seen; walls and floor do not.
            if (ny >= sy) {
                return true;
            }
            if ((nx < 0 && openSide == Direction.WEST) || (nx >= sx && openSide == Direction.EAST)
                || (nz < 0 && openSide == Direction.NORTH) || (nz >= sz && openSide == Direction.SOUTH)) {
                return true;
            }
        }
        return false;
    }

    /** How full a neighbouring column is; outside the body the walls count as full, an open gate as empty. */
    private static int neighbour(int[][] heights, int x, int z, int sy, boolean openGate) {
        if (x < 0 || z < 0 || x >= heights.length || z >= heights[0].length) {
            return openGate ? 0 : sy;
        }
        return heights[x][z];
    }

    // ------------------------------------------------- telling the game what to draw

    /** NeoForge asks for the space the drawing takes up: the whole body, and room for an open gate. */
    public AABB getRenderBoundingBox(DumpBodyBlockEntity body) {
        return new AABB(body.getBlockPos()).inflate(body.isJoined() ? DumpBodyGroup.MAX_LONG + 1.0 : 2.0);
    }
}
