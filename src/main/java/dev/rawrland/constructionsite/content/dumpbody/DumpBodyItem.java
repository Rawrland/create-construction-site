package dev.rawrland.constructionsite.content.dumpbody;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The dump body as an item. It places like any block, with one extra (owner's
 * request, 17 October 2026): placed on a full side of a joined dump body (one of
 * its walls, its top or its bottom), it adds that whole layer at once, the way
 * the bucket grows by a slice and Create's vaults grow. Only when the bigger
 * size is allowed and every place of the layer is free. Sneaking places a single
 * block. A player who is not in creative needs enough blocks for the whole layer;
 * with fewer, only the one block is placed.
 */
public class DumpBodyItem extends BlockItem {

    public DumpBodyItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        // Look at the body we are placing against BEFORE placing: the new block
        // makes it regroup, and then its size can no longer be read.
        BlockState body = null;
        List<BlockPos> restOfLayer = null;
        if (!level.isClientSide) {
            BlockPos against = context.getClickedPos().relative(context.getClickedFace().getOpposite());
            restOfLayer = findRestOfLayer(context, against);
            body = level.getBlockState(against);
        }

        InteractionResult result = super.place(context);
        if (!result.consumesAction() || restOfLayer == null || restOfLayer.isEmpty()) {
            return result;
        }
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        boolean creative = player != null && player.isCreative();
        // The first block is already placed and paid for. Only go on if there are enough for the rest.
        if (!creative && stack.getCount() < restOfLayer.size()) {
            return result;
        }
        BlockState state = DumpBodyBlock.withGatesOf(getBlock().defaultBlockState(), body);
        // Join once at the end, not after every block of the layer.
        DumpBodyGroup.suppressRegroup = true;
        try {
            for (BlockPos pos : restOfLayer) {
                level.setBlock(pos, state, Block.UPDATE_ALL);
            }
        } finally {
            DumpBodyGroup.suppressRegroup = false;
        }
        DumpBodyGroup.regroup(level, context.getClickedPos());
        if (!creative) {
            stack.shrink(restOfLayer.size());
        }
        return result;
    }

    /**
     * If this placement is on a full side of a joined dump body that may grow that
     * way, the other places of the new layer (not the one being placed). Null for
     * an ordinary placement.
     */
    @Nullable
    private static List<BlockPos> findRestOfLayer(BlockPlaceContext context, BlockPos against) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return null;
        }
        Level level = context.getLevel();
        if (!(level.getBlockEntity(against) instanceof DumpBodyBlockEntity cell) || !cell.isJoined()
            // Only the same colour joins, so a layer of another colour would not become part of it.
            || !(context.getItemInHand().getItem() instanceof net.minecraft.world.item.BlockItem item)
            || level.getBlockState(against).getBlock() != item.getBlock()) {
            return null;
        }
        Direction face = context.getClickedFace();
        BlockPos anchor = against.offset(cell.getAnchorOffset());
        int sx = cell.getSizeX();
        int sy = cell.getSizeY();
        int sz = cell.getSizeZ();
        // The clicked block must be on that side of the body, and the body must be allowed to grow that way.
        int nx = sx + (face.getAxis() == Direction.Axis.X ? 1 : 0);
        int ny = sy + (face.getAxis() == Direction.Axis.Y ? 1 : 0);
        int nz = sz + (face.getAxis() == Direction.Axis.Z ? 1 : 0);
        if (!DumpBodyGroup.isAllowedSize(nx, ny, nz)) {
            return null;
        }
        BlockPos placing = context.getClickedPos();
        int[] min = {anchor.getX(), anchor.getY(), anchor.getZ()};
        int[] max = {anchor.getX() + sx - 1, anchor.getY() + sy - 1, anchor.getZ() + sz - 1};
        int axis = face.getAxis().ordinal();
        int layer = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max[axis] + 1 : min[axis] - 1;
        int[] placingAt = {placing.getX(), placing.getY(), placing.getZ()};
        if (placingAt[axis] != layer) {
            return null;
        }
        List<BlockPos> rest = new ArrayList<>();
        for (int x = min[0]; x <= max[0]; x++) {
            for (int y = min[1]; y <= max[1]; y++) {
                for (int z = min[2]; z <= max[2]; z++) {
                    int[] at = {x, y, z};
                    at[axis] = layer;
                    BlockPos target = new BlockPos(at[0], at[1], at[2]);
                    if (target.equals(placing)) {
                        continue;
                    }
                    if (!level.isLoaded(target) || !level.getBlockState(target).canBeReplaced()) {
                        return null;
                    }
                    if (!rest.contains(target)) {
                        rest.add(target);
                    }
                }
            }
        }
        return rest;
    }
}
