package dev.rawrland.constructionsite.client;

import javax.annotation.Nullable;
import net.minecraft.world.item.DyeColor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.bucket.BucketGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Draws the mod's tools in code: flat faces and boxes with textures.
 *
 * Coordinates are the tool's own, in pixels (1/16 block): x along the tool's
 * width, y up, z from the working side toward the back, counted from the anchor
 * block's front bottom left corner. The painter knows which way the tool is
 * turned, so the drawing code never has to think about directions.
 *
 * <b>Connected look.</b> A surface of a material is drawn like Create's
 * connected textures (item vaults, casings): one calm fill across the whole
 * surface, however many blocks it covers, and a frame only around its outer
 * edge. The fill is a plain texture repeated once per block, never stretched.
 * The frame is taken from the edges of the material's framed block texture.
 * A surface whose outline is not a rectangle (a side wall with steps) is given
 * as a grid of cells; the frame then follows the outline and leaves out the
 * edges between filled cells.
 *
 * Faces: a face "along" axis 0 looks along x, axis 1 along y, axis 2 along z.
 * It spans a (x for axes 1 and 2, z for axis 0) and b (y for axes 0 and 2, z for axis 1).
 * "Up" in the texture is toward higher b.
 */
final class ToolPainter {

    /** A material: its plain fill and its framed block texture. A frame of null means no frame is drawn. */
    record Material(TextureAtlasSprite fill, TextureAtlasSprite frame) {
    }

    /*
     * The tools are drawn with the textures of the mod's plating blocks, so every part of a tool
     * looks like a block you can build with (owner, 20 October 2026). The names come from
     * MetalBlockTextures, which gives the same textures to the blocks.
     */

    /** The body of a tool: Brass Plating when bare, Brass Plating of its colour when painted. */
    static Material body(@Nullable DyeColor colour) {
        return framed(MetalBlockTextures.brassPlating(colour));
    }

    /**
     * The working edges (teeth, the blade's cutting edge): Dark Steel Plating on every version,
     * bare or painted, so the bare tools match the painted ones (owner, 20 October 2026).
     */
    static String edge(@Nullable DyeColor colour) {
        return DARK_STEEL;
    }

    /** Dark Steel Plating: frames, plates, pins and hinges. */
    static final String DARK_STEEL = MetalBlockTextures.DARK_STEEL;

    /** How wide the frame around a surface is, in pixels: the texture's outline plus its bevel. */
    private static final float BORDER = 2.0F;
    private static final float TILE = 16.0F;

    // Box faces, for leaving faces out of a box.
    static final int FRONT = 1;   // z low
    static final int BACK = 2;    // z high
    static final int LEFT = 4;    // x low
    static final int RIGHT = 8;   // x high
    static final int BOTTOM = 16; // y low
    static final int TOP = 32;    // y high

    private final VertexConsumer buffer;
    private final PoseStack.Pose pose;
    private final int packedLight;
    // The tool's own directions as plain numbers: right, up, back.
    private final float rx, ry, rz, ux, uy, uz, bx, by, bz;

    ToolPainter(VertexConsumer buffer, PoseStack.Pose pose, BucketGroup.Axes axes, int packedLight) {
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

    /** A material with a frame: textures "name" (framed) and "name_fill" (plain). */
    static Material framed(String name) {
        return new Material(sprite(texture(name + "_fill")), sprite(texture(name)));
    }

    /** A material without a frame, for small parts like teeth and pins. */
    static Material plain(String name) {
        return new Material(sprite(texture(name + "_fill")), null);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, "block/" + name);
    }

    /** A texture from the block atlas, where Minecraft puts every texture under textures/block. */
    static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    /** One vertex. x, y, z in tool pixels; the normal in tool directions. */
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

    // ------------------------------------------------------------ plain tiling

    /**
     * A face covered with one texture, repeated once per block on the grid that
     * starts at the anchor corner, never stretched. Used for fills and for the
     * bucket's content.
     */
    void face(TextureAtlasSprite sprite, int axis, int facing, float at,
              float a0, float a1, float b0, float b1) {
        if (a1 - a0 < 0.001F || b1 - b0 < 0.001F) {
            return;
        }
        for (float aFrom = a0; aFrom < a1 - 0.001F; ) {
            float aTileStart = (float) Math.floor(aFrom / TILE + 0.0001F) * TILE;
            float aTo = Math.min(a1, aTileStart + TILE);
            for (float bFrom = b0; bFrom < b1 - 0.001F; ) {
                float bTileStart = (float) Math.floor(bFrom / TILE + 0.0001F) * TILE;
                float bTo = Math.min(b1, bTileStart + TILE);
                // Texel columns from the left, texel rows from the top (the texture's top is up).
                region(sprite, axis, facing, at, aFrom, aTo, bFrom, bTo,
                    aFrom - aTileStart, aTo - aTileStart, TILE - (bTo - bTileStart), TILE - (bFrom - bTileStart));
                bFrom = bTo;
            }
            aFrom = aTo;
        }
    }

    // ---------------------------------------------------------- connected look

    /** A rectangular surface of a material, with a frame all around. */
    void rect(Material m, int axis, int facing, float at, float a0, float a1, float b0, float b1) {
        cell(m, axis, facing, at, a0, a1, b0, b1, true, true, true, true, false, false, false, false);
    }

    /**
     * A surface made of a grid of cells, of which some are filled. The fill runs
     * across all filled cells; the frame follows the outline of the filled cells.
     *
     * @param aEdges the cell edges along a, rising
     * @param bEdges the cell edges along b, rising
     * @param filled filled[i][j] for the cell between aEdges[i], aEdges[i+1] and bEdges[j], bEdges[j+1]
     */
    void panel(Material m, int axis, int facing, float at, float[] aEdges, float[] bEdges, boolean[][] filled) {
        int na = aEdges.length - 1;
        int nb = bEdges.length - 1;
        for (int i = 0; i < na; i++) {
            for (int j = 0; j < nb; j++) {
                if (!filled[i][j]) {
                    continue;
                }
                boolean left = !has(filled, i - 1, j);
                boolean right = !has(filled, i + 1, j);
                boolean bottom = !has(filled, i, j - 1);
                boolean top = !has(filled, i, j + 1);
                cell(m, axis, facing, at, aEdges[i], aEdges[i + 1], bEdges[j], bEdges[j + 1],
                    left, right, bottom, top,
                    !left && !bottom && !has(filled, i - 1, j - 1),
                    !right && !bottom && !has(filled, i + 1, j - 1),
                    !left && !top && !has(filled, i - 1, j + 1),
                    !right && !top && !has(filled, i + 1, j + 1));
            }
        }
    }

    private static boolean has(boolean[][] filled, int i, int j) {
        return i >= 0 && j >= 0 && i < filled.length && j < filled[i].length && filled[i][j];
    }

    /**
     * One cell of a surface: the fill in the middle, a frame strip along every
     * open edge, a frame corner where two open edges meet, and a single outline
     * pixel at an inner corner (where the outline turns inward around a missing
     * diagonal neighbour), so the outline never has a gap.
     */
    private void cell(Material m, int axis, int facing, float at, float a0, float a1, float b0, float b1,
                      boolean left, boolean right, boolean bottom, boolean top,
                      boolean innerBottomLeft, boolean innerBottomRight, boolean innerTopLeft, boolean innerTopRight) {
        if (a1 - a0 < 0.001F || b1 - b0 < 0.001F) {
            return;
        }
        if (m.frame() == null) {
            face(m.fill(), axis, facing, at, a0, a1, b0, b1);
            return;
        }
        TextureAtlasSprite frame = m.frame();
        // The frame is as wide everywhere; only a cell too small for it gets a narrower one, and then
        // only if it has a frame on both sides. So the frame lines of a stepped panel meet cleanly.
        float ba = Math.min(BORDER, (a1 - a0) / (left && right ? 2.0F : 1.0F));
        float bb = Math.min(BORDER, (b1 - b0) / (bottom && top ? 2.0F : 1.0F));
        // Where the middle part starts and ends.
        float ia0 = left ? a0 + ba : a0;
        float ia1 = right ? a1 - ba : a1;
        float ib0 = bottom ? b0 + bb : b0;
        float ib1 = top ? b1 - bb : b1;

        // Frame strips. Texel columns 6 to 10 and rows 6 to 10 are the even middle
        // of the frame's sides; stretching them along an edge looks the same as repeating them.
        if (left) {
            region(frame, axis, facing, at, a0, ia0, ib0, ib1, 0, ba, 6, 10);
        }
        if (right) {
            region(frame, axis, facing, at, ia1, a1, ib0, ib1, TILE - ba, TILE, 6, 10);
        }
        if (bottom) {
            region(frame, axis, facing, at, ia0, ia1, b0, ib0, 6, 10, TILE - bb, TILE);
        }
        if (top) {
            region(frame, axis, facing, at, ia0, ia1, ib1, b1, 6, 10, 0, bb);
        }
        // Outer corners: the texture's own corners.
        if (left && bottom) {
            region(frame, axis, facing, at, a0, ia0, b0, ib0, 0, ba, TILE - bb, TILE);
        }
        if (right && bottom) {
            region(frame, axis, facing, at, ia1, a1, b0, ib0, TILE - ba, TILE, TILE - bb, TILE);
        }
        if (left && top) {
            region(frame, axis, facing, at, a0, ia0, ib1, b1, 0, ba, 0, bb);
        }
        if (right && top) {
            region(frame, axis, facing, at, ia1, a1, ib1, b1, TILE - ba, TILE, 0, bb);
        }

        // The middle, with a one-pixel outline dot cut out at each inner corner.
        float dot = Math.min(1.0F, Math.min((ia1 - ia0) / 2.0F, (ib1 - ib0) / 2.0F));
        boolean lowRow = innerBottomLeft || innerBottomRight;
        boolean highRow = innerTopLeft || innerTopRight;
        float midB0 = lowRow ? ib0 + dot : ib0;
        float midB1 = highRow ? ib1 - dot : ib1;
        face(m.fill(), axis, facing, at, ia0, ia1, midB0, midB1);
        if (lowRow) {
            cornerRow(m, axis, facing, at, ia0, ia1, ib0, midB0, dot, innerBottomLeft, innerBottomRight);
        }
        if (highRow) {
            cornerRow(m, axis, facing, at, ia0, ia1, midB1, ib1, dot, innerTopLeft, innerTopRight);
        }
    }

    /**
     * A rectangle all in the material's outline colour (the frame texture's outermost column),
     * for rims too thin to hold a frame and a fill, such as the bucket's front ring.
     */
    void outline(Material m, int axis, int facing, float at, float a0, float a1, float b0, float b1) {
        if (m.frame() == null) {
            face(m.fill(), axis, facing, at, a0, a1, b0, b1);
            return;
        }
        region(m.frame(), axis, facing, at, a0, a1, b0, b1, 0, 1, 7, 8);
    }

    /** A thin row of fill with an outline dot at its left end, its right end, or both. */
    private void cornerRow(Material m, int axis, int facing, float at, float a0, float a1, float b0, float b1,
                           float dot, boolean dotLeft, boolean dotRight) {
        float fillFrom = dotLeft ? a0 + dot : a0;
        float fillTo = dotRight ? a1 - dot : a1;
        face(m.fill(), axis, facing, at, fillFrom, fillTo, b0, b1);
        // The outline colour: the frame texture's outermost column, halfway down.
        if (dotLeft) {
            region(m.frame(), axis, facing, at, a0, fillFrom, b0, b1, 0, 1, 7, 8);
        }
        if (dotRight) {
            region(m.frame(), axis, facing, at, fillTo, a1, b0, b1, 0, 1, 7, 8);
        }
    }

    // ------------------------------------------------------------------- boxes

    /** A box with its sides along the tool's directions, every side a framed surface. */
    void box(Material m, float x0, float y0, float z0, float x1, float y1, float z1) {
        box(m, x0, y0, z0, x1, y1, z1, 0);
    }

    /** A box that leaves out the given sides (FRONT, BACK, LEFT, RIGHT, BOTTOM, TOP added together). */
    void box(Material m, float x0, float y0, float z0, float x1, float y1, float z1, int leaveOut) {
        if ((leaveOut & FRONT) == 0) {
            rect(m, 2, -1, z0, x0, x1, y0, y1);
        }
        if ((leaveOut & BACK) == 0) {
            rect(m, 2, 1, z1, x0, x1, y0, y1);
        }
        if ((leaveOut & LEFT) == 0) {
            rect(m, 0, -1, x0, z0, z1, y0, y1);
        }
        if ((leaveOut & RIGHT) == 0) {
            rect(m, 0, 1, x1, z0, z1, y0, y1);
        }
        if ((leaveOut & BOTTOM) == 0) {
            rect(m, 1, -1, y0, x0, x1, z0, z1);
        }
        if ((leaveOut & TOP) == 0) {
            rect(m, 1, 1, y1, x0, x1, z0, z1);
        }
    }

    // ------------------------------------------------------------------ quads

    /**
     * One quad showing a part of a texture: texel columns c0 to c1 from the left
     * across a, texel rows r0 to r1 from the top (r0 at high b, r1 at low b).
     */
    private void region(TextureAtlasSprite sprite, int axis, int facing, float at,
                        float a0, float a1, float b0, float b1, float c0, float c1, float r0, float r1) {
        if (a1 - a0 < 0.001F || b1 - b0 < 0.001F) {
            return;
        }
        float uSpan = sprite.getU1() - sprite.getU0();
        float vSpan = sprite.getV1() - sprite.getV0();
        float u0 = sprite.getU0() + uSpan * c0 / TILE;
        float u1 = sprite.getU0() + uSpan * c1 / TILE;
        float vHigh = sprite.getV0() + vSpan * r0 / TILE;
        float vLow = sprite.getV0() + vSpan * r1 / TILE;
        switch (axis) {
            case 0 -> { // looks along x: a = z, b = y
                vertex(at, b0, a0, u0, vLow, facing, 0, 0);
                vertex(at, b0, a1, u1, vLow, facing, 0, 0);
                vertex(at, b1, a1, u1, vHigh, facing, 0, 0);
                vertex(at, b1, a0, u0, vHigh, facing, 0, 0);
            }
            case 1 -> { // looks along y: a = x, b = z
                vertex(a0, at, b0, u0, vLow, 0, facing, 0);
                vertex(a1, at, b0, u1, vLow, 0, facing, 0);
                vertex(a1, at, b1, u1, vHigh, 0, facing, 0);
                vertex(a0, at, b1, u0, vHigh, 0, facing, 0);
            }
            default -> { // looks along z: a = x, b = y
                vertex(a0, b0, at, u0, vLow, 0, 0, facing);
                vertex(a1, b0, at, u1, vLow, 0, 0, facing);
                vertex(a1, b1, at, u1, vHigh, 0, 0, facing);
                vertex(a0, b1, at, u0, vHigh, 0, 0, facing);
            }
        }
    }
}
