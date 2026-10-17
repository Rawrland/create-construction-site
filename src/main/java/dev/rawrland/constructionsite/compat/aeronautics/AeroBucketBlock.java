package dev.rawrland.constructionsite.compat.aeronautics;

import com.mojang.serialization.MapCodec;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlock;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import dev.simulated_team.simulated.index.SimTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Excavator Bucket block when Create Aeronautics is installed. It adds
 * tying and cutting Create Aeronautics' ropes; everything else is the bucket's own.
 *
 * RopeHolderBlock is Create Aeronautics' helper for blocks a rope can be tied
 * to. It keeps a rope tied when the block becomes part of a simulated contraption.
 */
public class AeroBucketBlock extends ExcavatorBucketBlock implements RopeHolderBlock<ExcavatorBucketBlockEntity> {

    public static final MapCodec<AeroBucketBlock> CODEC = simpleCodec(AeroBucketBlock::new);

    public AeroBucketBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * Right-click with an item. Two items matter here:
     * the Rope Coupling ties a rope to the bucket, and shears (anything that
     * cuts Create Aeronautics' ropes) remove it again.
     *
     * A rope goes to the teeth of the clicked block's column. If that column
     * already has a rope, the nearest free column is used. Shears look for a
     * rope in the same order.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ExcavatorBucketBlockEntity bucket)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(SimTags.Items.DESTROYS_ROPE)) {
            ExcavatorBucketBlockEntity roped = bucket.findHookBlock(true);
            if (roped == null) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
                return ItemInteractionResult.SUCCESS;
            }
            return RopeHolderBlock.shearRope(this, level, roped.getBlockPos(), serverPlayer);
        }

        if (stack.getItem() instanceof RopeItem) {
            // A rope from a bucket to itself makes no sense: ignore the second click.
            BlockPos firstEnd = stack.get(SimDataComponents.ROPE_FIRST_CONNECTION);
            if (firstEnd != null && bucket.isPartOfBucket(firstEnd)) {
                return ItemInteractionResult.CONSUME;
            }
            // One rope per block of width: if every column has one, no further rope is started.
            ExcavatorBucketBlockEntity free = bucket.findHookBlock(false);
            if (free == null) {
                return ItemInteractionResult.CONSUME;
            }
            BlockPos hook = free.getBlockPos();
            if (hook.equals(pos)) {
                // The hook block itself was clicked: the item does its normal work on it.
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            // Another block of the bucket was clicked: let the item act as if the hook block had been.
            InteractionResult result = stack.useOn(new UseOnContext(player, hand, hitResult.withPosition(hook)));
            return result.consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
