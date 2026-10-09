package dev.rawrland.constructionsite.content.bucket;

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

import java.util.ArrayList;
import java.util.List;

/**
 * The bucket as an item. It places like any block, with one extra: placed on
 * the end of a joined bucket, it extends that bucket by a whole slice at once,
 * the way Create's vaults and tanks grow.
 */
public class ExcavatorBucketItem extends BlockItem {

    public ExcavatorBucketItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        // Look at the bucket we are placing against BEFORE placing: the new block
        // makes it fall apart for a moment, and then its size can no longer be read.
        List<BlockPos> restOfSlice = context.getLevel().isClientSide ? null : findRestOfSlice(context);
        Direction facing = null;
        if (restOfSlice != null) {
            BlockPos against = context.getClickedPos().relative(context.getClickedFace().getOpposite());
            facing = context.getLevel().getBlockState(against).getValue(ExcavatorBucketBlock.FACING);
        }

        InteractionResult result = super.place(context);
        if (!result.consumesAction() || restOfSlice == null || restOfSlice.isEmpty()) {
            return result;
        }

        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        boolean creative = player != null && player.isCreative();
        // The first block is already placed and paid for. Only go on if there are enough for the rest.
        if (!creative && stack.getCount() < restOfSlice.size()) {
            return result;
        }
        BlockState state = getBlock().defaultBlockState().setValue(ExcavatorBucketBlock.FACING, facing);
        for (BlockPos pos : restOfSlice) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        if (!creative) {
            stack.shrink(restOfSlice.size());
        }
        return result;
    }

    /**
     * If this placement is on the end of a joined bucket that may still grow,
     * returns the other positions of the new slice (not the one being placed).
     * Returns null if this is an ordinary placement.
     */
    private List<BlockPos> findRestOfSlice(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return null;
        }
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        BlockPos placing = context.getClickedPos();
        BlockPos against = placing.relative(face.getOpposite());
        if (!(level.getBlockEntity(against) instanceof ExcavatorBucketBlockEntity bucket) || !bucket.isJoined()) {
            return null;
        }

        // Only the two ends of the width can be extended.
        BucketGroup.Axes axes = BucketGroup.axes(bucket.getBlockState());
        if (face.getAxis() != axes.right().getAxis()) {
            return null;
        }
        int height = bucket.getGroupHeight();
        if (height <= 1 || !BucketGroup.isAllowedSize(height, height, bucket.getGroupWidth() + 1)) {
            return null;
        }

        // The new slice is the clicked slice, moved one block outward.
        List<BlockPos> rest = new ArrayList<>();
        for (BlockPos slicePos : bucket.getSlicePositions()) {
            BlockPos target = slicePos.relative(face);
            if (target.equals(placing)) {
                continue;
            }
            if (!level.getBlockState(target).canBeReplaced()) {
                return null;
            }
            rest.add(target);
        }
        return rest;
    }
}
