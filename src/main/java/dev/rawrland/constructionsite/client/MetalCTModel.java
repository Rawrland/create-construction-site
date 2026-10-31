package dev.rawrland.constructionsite.client;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Connected textures for a plating block, its slabs and its stairs (add-metal-slabs-and-stairs).
 * Works like Create's CTModel, with the frame worked out per half-block quarter of each face
 * (MetalSurface), so slabs and stairs join their block and each other into one framed surface.
 *
 * family: tells for any block which plating it belongs to (the same key for a plating and its
 * slab and stairs), or null for every other block.
 */
public class MetalCTModel extends BakedModelWrapperWithData {

    private static final ModelProperty<MetalSurface.Grid> GRID = new ModelProperty<>();
    private static final int SHEET = 8;

    private final CTSpriteShiftEntry shift;
    private final Function<BlockState, Object> family;

    public MetalCTModel(BakedModel originalModel, CTSpriteShiftEntry shift, Function<BlockState, Object> family) {
        super(originalModel);
        this.shift = shift;
        this.family = family;
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos,
                                                BlockState state, ModelData blockEntityData) {
        Object own = family.apply(state);
        if (own == null) {
            return builder;
        }
        // Where the same plating is, in half blocks, in the 3 x 3 x 3 blocks around this one.
        MetalSurface.Grid grid = new MetalSurface.Grid();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    at.setWithOffset(pos, dx, dy, dz);
                    BlockState other = world.getBlockState(at);
                    if (!own.equals(family.apply(other))) {
                        continue;
                    }
                    VoxelShape shape = other.getShape(world, at);
                    List<AABB> boxes = shape.toAabbs();
                    for (int sx = 0; sx < 2; sx++) {
                        for (int sy = 0; sy < 2; sy++) {
                            for (int sz = 0; sz < 2; sz++) {
                                if (inside(boxes, sx * 0.5 + 0.25, sy * 0.5 + 0.25, sz * 0.5 + 0.25)) {
                                    grid.set(2 * dx + sx, 2 * dy + sy, 2 * dz + sz);
                                }
                            }
                        }
                    }
                }
            }
        }
        return builder.with(GRID, grid);
    }

    private static boolean inside(List<AABB> boxes, double x, double y, double z) {
        for (AABB box : boxes) {
            if (box.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
                                    RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, extraData, renderType);
        if (!extraData.has(GRID)) {
            return quads;
        }
        MetalSurface.Grid grid = extraData.get(GRID);
        List<BakedQuad> out = new ArrayList<>(quads.size() * 2);
        for (BakedQuad quad : quads) {
            if (quad.getSprite() != shift.getOriginal()) {
                out.add(quad);
                continue;
            }
            List<BakedQuad> split = split(grid, quad);
            if (split == null) {
                out.add(quad);
            } else {
                out.addAll(split);
            }
        }
        return out;
    }

    /** The quad cut into the pieces MetalSurface asks for, each showing its part of its tile. */
    private List<BakedQuad> split(MetalSurface.Grid grid, BakedQuad quad) {
        int face = quad.getDirection().get3DDataValue();
        int n = MetalSurface.normalAxis(face), ua = MetalSurface.uAxis(face), va = MetalSurface.vAxis(face);
        int[] data = quad.getVertices();
        float[][] p = new float[4][];
        float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            Vec3 v = BakedQuadHelper.getXYZ(data, i);
            p[i] = new float[] {(float) v.x * 16, (float) v.y * 16, (float) v.z * 16};
            minU = Math.min(minU, p[i][ua]);
            maxU = Math.max(maxU, p[i][ua]);
            minV = Math.min(minV, p[i][va]);
            maxV = Math.max(maxV, p[i][va]);
        }
        List<MetalSurface.Piece> pieces = MetalSurface.pieces(grid, face, p[0][n], minU, maxU, minV, maxV);
        if (pieces == null) {
            return null;
        }
        TextureAtlasSprite target = shift.getTarget();
        int us = MetalSurface.uSign(face), vs = MetalSurface.vSign(face);
        List<BakedQuad> out = new ArrayList<>(pieces.size());
        for (MetalSurface.Piece piece : pieces) {
            BakedQuad copy = BakedQuadHelper.clone(quad);
            int[] v = copy.getVertices();
            int col = piece.tile() % SHEET, row = piece.tile() / SHEET;
            for (int i = 0; i < 4; i++) {
                float[] xyz = p[i].clone();
                xyz[ua] = p[i][ua] <= minU + 1e-3f ? piece.w0u() : piece.w1u();
                xyz[va] = p[i][va] <= minV + 1e-3f ? piece.w0v() : piece.w1v();
                BakedQuadHelper.setXYZ(v, i, new Vec3(xyz[0] / 16, xyz[1] / 16, xyz[2] / 16));
                // The tile's pixel at this corner: piece positions and tile parts have the same size.
                float tu = texCoord(MetalSurface.tileCoord(xyz[ua], us), piece, true, us);
                float tv = texCoord(MetalSurface.tileCoord(xyz[va], vs), piece, false, vs);
                BakedQuadHelper.setU(v, i, target.getU((col * 16 + tu) / (16f * SHEET)));
                BakedQuadHelper.setV(v, i, target.getV((row * 16 + tv) / (16f * SHEET)));
            }
            out.add(copy);
        }
        return out;
    }

    /** The tile pixel for a corner at tile position t of a piece (its tile part starts at its low end). */
    private static float texCoord(float t, MetalSurface.Piece piece, boolean u, int sign) {
        float w0 = u ? piece.w0u() : piece.w0v(), w1 = u ? piece.w1u() : piece.w1v();
        float lowTile = MetalSurface.tileCoord(sign > 0 ? w0 : w1, sign);
        float t0 = u ? piece.t0u() : piece.t0v();
        return t0 + (t - lowTile);
    }
}
