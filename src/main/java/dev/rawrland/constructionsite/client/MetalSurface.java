package dev.rawrland.constructionsite.client;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.CTContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The geometry behind the platings' connected textures on slabs and stairs (MetalCTModel).
 * No Minecraft classes here, so it can be checked on its own.
 *
 * Slabs and stairs are built from half-block cubes, so the surface is worked out on a grid of
 * half blocks around the block being drawn. Every face is cut into its half-block quarters; each
 * quarter gets its own tile of Create's connected-texture sheet (the frame on every edge where the
 * surface does not go on), and shows the part of that tile it needs. Where a quarter needs a frame
 * on an edge inside the block (the top of a slab's side, the foot of a stair's step), it shows the
 * tile's own edge there instead of its middle. A whole face that needs nothing special stays one
 * piece, so full blocks look exactly as before.
 *
 * Coordinates are in pixels of the block (0 to 16). Faces are numbered like Minecraft's
 * Direction.get3DDataValue(): down, up, north, south, west, east.
 */
public final class MetalSurface {

    private MetalSurface() {
    }

    /** Which half-block cells around a block hold the same plating. The block's own cells are 0 and 1. */
    public static final class Grid {
        static final int MIN = -2;
        static final int SIZE = 6;
        private final long[] bits = new long[4];

        public void set(int x, int y, int z) {
            int i = index(x, y, z);
            bits[i >> 6] |= 1L << (i & 63);
        }

        public boolean get(int x, int y, int z) {
            if (x < MIN || y < MIN || z < MIN || x >= MIN + SIZE || y >= MIN + SIZE || z >= MIN + SIZE) {
                return false;
            }
            int i = index(x, y, z);
            return (bits[i >> 6] & (1L << (i & 63))) != 0;
        }

        private static int index(int x, int y, int z) {
            return (x - MIN) + SIZE * ((y - MIN) + SIZE * (z - MIN));
        }
    }

    /**
     * A piece of a face: where it sits along the face's two axes (block pixels, along the tile's u
     * and v world axes), the tile of the sheet, and the part of the tile it shows (tile pixels).
     */
    public record Piece(float w0u, float w1u, float w0v, float w1v, int tile,
                        float t0u, float t1u, float t0v, float t1v) {
    }

    /** For each face: normal axis and sign, the world axis and sign of the tile's u, and of its v. */
    private static final int[][] FRAME = {
        {1, -1, 0, 1, 2, -1},   // down
        {1, 1, 0, 1, 2, 1},     // up
        {2, -1, 0, -1, 1, -1},  // north
        {2, 1, 0, 1, 1, -1},    // south
        {0, -1, 2, 1, 1, -1},   // west
        {0, 1, 2, -1, 1, -1},   // east
    };

    public static int normalAxis(int face) {
        return FRAME[face][0];
    }

    public static int uAxis(int face) {
        return FRAME[face][2];
    }

    public static int vAxis(int face) {
        return FRAME[face][4];
    }

    public static int uSign(int face) {
        return FRAME[face][3];
    }

    public static int vSign(int face) {
        return FRAME[face][5];
    }

    /**
     * The pieces for one face of the model, or null if the face does not lie on the half-block
     * grid (it is then left as it is). plane: the face's position on its normal axis; u0..u1 and
     * v0..v1: its extent along the world axes of the tile's u and v.
     */
    public static List<Piece> pieces(Grid grid, int face, float plane, float u0, float u1, float v0, float v1) {
        if (!onGrid(plane) || !onGrid(u0) || !onGrid(u1) || !onGrid(v0) || !onGrid(v1) || u1 <= u0 || v1 <= v0) {
            return null;
        }
        int[] f = FRAME[face];
        int normalCell = f[1] > 0 ? Math.round(plane / 8) - 1 : Math.round(plane / 8);
        // The face's quarters, in tile space (tile u and v, 0 or 8 each).
        float tu0 = tile(u0, u1, f[3])[0], tu1 = tile(u0, u1, f[3])[1];
        float tv0 = tile(v0, v1, f[5])[0], tv1 = tile(v0, v1, f[5])[1];
        List<int[]> quarters = new ArrayList<>();
        for (int qu = Math.round(tu0); qu < Math.round(tu1); qu += 8) {
            for (int qv = Math.round(tv0); qv < Math.round(tv1); qv += 8) {
                quarters.add(new int[] {qu, qv});
            }
        }
        CTContext[] contexts = new CTContext[quarters.size()];
        for (int i = 0; i < quarters.size(); i++) {
            int[] q = quarters.get(i);
            contexts[i] = context(grid, face, normalCell, q[0], q[1]);
        }
        List<Piece> out = new ArrayList<>();
        if (quarters.size() == 4 && whole(contexts)) {
            // A whole face that joins the same way along each edge: one piece, as on a full block.
            CTContext tl = contexts[0], bl = contexts[1], tr = contexts[2], br = contexts[3];
            CTContext c = new CTContext();
            c.up = tl.up;
            c.down = bl.down;
            c.left = tl.left;
            c.right = tr.right;
            c.topLeft = tl.topLeft;
            c.topRight = tr.topRight;
            c.bottomLeft = bl.bottomLeft;
            c.bottomRight = br.bottomRight;
            out.add(toWorld(face, 0, 16, 0, 16, index(c), 0, 16, 0, 16));
            return out;
        }
        for (int i = 0; i < quarters.size(); i++) {
            quarterPieces(face, quarters.get(i)[0], quarters.get(i)[1], contexts[i], out);
        }
        return out;
    }

    /** The four quarters (top-left, bottom-left, top-right, bottom-right) join inside and agree along each edge. */
    private static boolean whole(CTContext[] q) {
        CTContext tl = q[0], bl = q[1], tr = q[2], br = q[3];
        boolean inside = tl.right && tl.down && tl.bottomRight && tr.left && tr.down && tr.bottomLeft
            && bl.right && bl.up && bl.topRight && br.left && br.up && br.topLeft;
        return inside && tl.up == tr.up && bl.down == br.down && tl.left == bl.left && tr.right == br.right;
    }

    private static void quarterPieces(int face, int qu, int qv, CTContext c, List<Piece> out) {
        int tile = index(c);
        // Along u: the half at the block's edge shows the tile where it is; the inner half shows
        // the tile's far edge when the surface stops inside the block.
        boolean innerU = qu == 0 ? c.right : c.left;
        boolean innerV = qv == 0 ? c.down : c.up;
        boolean innerCorner = qu == 0 ? (qv == 0 ? c.bottomRight : c.topRight) : (qv == 0 ? c.bottomLeft : c.topLeft);
        float[][] posU = halves(qu);
        float[][] posV = halves(qv);
        Piece[][] grid = new Piece[2][2];
        for (int iu = 0; iu < 2; iu++) {
            for (int iv = 0; iv < 2; iv++) {
                boolean cornerFar = iu == 1 && iv == 1 && innerU && innerV && !innerCorner;
                float[] texU = iu == 0 ? posU[0] : (!innerU || cornerFar ? far(qu) : posU[1]);
                float[] texV = iv == 0 ? posV[0] : (!innerV || cornerFar ? far(qv) : posV[1]);
                grid[iu][iv] = new Piece(posU[iu][0], posU[iu][1], posV[iv][0], posV[iv][1], tile,
                    texU[0], texU[1], texV[0], texV[1]);
            }
        }
        // Join neighbouring pieces that show neighbouring parts of the tile.
        List<Piece> rows = new ArrayList<>();
        for (int iv = 0; iv < 2; iv++) {
            Piece a = grid[0][iv], b = grid[1][iv];
            Piece joined = joinU(a, b);
            if (joined != null) {
                rows.add(joined);
            } else {
                rows.add(a);
                rows.add(b);
            }
        }
        List<Piece> pieces = new ArrayList<>();
        if (rows.size() == 2 && joinV(rows.get(0), rows.get(1)) != null) {
            pieces.add(joinV(rows.get(0), rows.get(1)));
        } else {
            pieces.addAll(rows);
        }
        for (Piece p : pieces) {
            out.add(toWorld(face, p.w0u(), p.w1u(), p.w0v(), p.w1v(), tile, p.t0u(), p.t1u(), p.t0v(), p.t1v()));
        }
    }

    /** The outer and the inner half of a quarter starting at q (0 or 8), in tile space. */
    private static float[][] halves(int q) {
        return q == 0 ? new float[][] {{0, 4}, {4, 8}} : new float[][] {{12, 16}, {8, 12}};
    }

    /** The tile's far edge for the inner half of a quarter starting at q. */
    private static float[] far(int q) {
        return q == 0 ? new float[] {12, 16} : new float[] {0, 4};
    }

    private static Piece joinU(Piece a, Piece b) {
        if (a.t0v() != b.t0v() || a.t1v() != b.t1v()) {
            return null;
        }
        Piece lo = a.w0u() < b.w0u() ? a : b, hi = lo == a ? b : a;
        if (lo.w1u() != hi.w0u() || lo.t1u() != hi.t0u()) {
            return null;
        }
        return new Piece(lo.w0u(), hi.w1u(), a.w0v(), a.w1v(), a.tile(), lo.t0u(), hi.t1u(), a.t0v(), a.t1v());
    }

    private static Piece joinV(Piece a, Piece b) {
        if (a.t0u() != b.t0u() || a.t1u() != b.t1u() || a.w0u() != b.w0u() || a.w1u() != b.w1u()) {
            return null;
        }
        Piece lo = a.w0v() < b.w0v() ? a : b, hi = lo == a ? b : a;
        if (lo.w1v() != hi.w0v() || lo.t1v() != hi.t0v()) {
            return null;
        }
        return new Piece(a.w0u(), a.w1u(), lo.w0v(), hi.w1v(), a.tile(), a.t0u(), a.t1u(), lo.t0v(), hi.t1v());
    }

    /** Tile-space positions back to world pixels (a tile axis may run against the world axis). */
    private static Piece toWorld(int face, float p0u, float p1u, float p0v, float p1v, int tile,
                                 float t0u, float t1u, float t0v, float t1v) {
        int[] f = FRAME[face];
        float[] wu = f[3] > 0 ? new float[] {p0u, p1u} : new float[] {16 - p1u, 16 - p0u};
        float[] wv = f[5] > 0 ? new float[] {p0v, p1v} : new float[] {16 - p1v, 16 - p0v};
        return new Piece(wu[0], wu[1], wv[0], wv[1], tile, t0u, t1u, t0v, t1v);
    }

    /** The tile coordinate of a world coordinate on an axis the tile runs along (sign) or against. */
    public static float tileCoord(float w, int sign) {
        return sign > 0 ? w : 16 - w;
    }

    private static float[] tile(float w0, float w1, int sign) {
        return sign > 0 ? new float[] {w0, w1} : new float[] {16 - w1, 16 - w0};
    }

    /** How the quarter at tile position (qu, qv) of a face joins its surroundings. */
    static CTContext context(Grid grid, int face, int normalCell, int qu, int qv) {
        int[] f = FRAME[face];
        int[] cell = new int[3];
        cell[f[0]] = normalCell;
        // The quarter's half-block cell along the tile's u and v world axes.
        cell[f[2]] = worldCell(qu, f[3]);
        cell[f[4]] = worldCell(qv, f[5]);
        int[] left = step(f[2], -f[3]), right = step(f[2], f[3]);
        int[] up = step(f[4], -f[5]), down = step(f[4], f[5]);
        int[] out = step(f[0], f[1]);
        CTContext c = new CTContext();
        c.up = joins(grid, cell, out, up, null);
        c.down = joins(grid, cell, out, down, null);
        c.left = joins(grid, cell, out, left, null);
        c.right = joins(grid, cell, out, right, null);
        c.topLeft = c.up && c.left && joins(grid, cell, out, up, left);
        c.topRight = c.up && c.right && joins(grid, cell, out, up, right);
        c.bottomLeft = c.down && c.left && joins(grid, cell, out, down, left);
        c.bottomRight = c.down && c.right && joins(grid, cell, out, down, right);
        return c;
    }

    /** The half-block cell (0 or 1) of a quarter at tile position q on an axis. */
    private static int worldCell(int q, int sign) {
        int cell = q / 8;
        return sign > 0 ? cell : 1 - cell;
    }

    private static int[] step(int axis, int sign) {
        int[] s = new int[3];
        s[axis] = sign;
        return s;
    }

    /** The surface goes on into the neighbouring cell: plating there, and nothing of it in front. */
    private static boolean joins(Grid grid, int[] cell, int[] out, int[] a, int[] b) {
        int x = cell[0] + a[0] + (b == null ? 0 : b[0]);
        int y = cell[1] + a[1] + (b == null ? 0 : b[1]);
        int z = cell[2] + a[2] + (b == null ? 0 : b[2]);
        return grid.get(x, y, z) && !grid.get(x + out[0], y + out[1], z + out[2]);
    }

    private static int index(CTContext c) {
        return AllCTTypes.OMNIDIRECTIONAL.getTextureIndex(c);
    }

    private static boolean onGrid(float v) {
        return Math.abs(v / 8 - Math.round(v / 8)) < 1e-3;
    }
}
