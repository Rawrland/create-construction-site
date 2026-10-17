package dev.rawrland.constructionsite.compat.aeronautics;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Excavator Bucket's block entity when Create Aeronautics is installed.
 *
 * It adds the two things that need Create Aeronautics; everything else is the
 * bucket's own, in the parent class:
 * <ul>
 *     <li>the rope end: Create Aeronautics' rope behaviour, which holds, saves and
 *     simulates a rope by itself. Every bucket block has one, but a bucket only
 *     uses the ones in its hook blocks (see the parent's rope section);</li>
 *     <li>the tick on simulated contraptions: Sable calls {@link #sable$tick} once
 *     per server tick while the bucket is part of one ("sub-level").</li>
 * </ul>
 * RopeStrandHolderBlockEntity is Simulated's interface for blocks with a rope
 * end; it includes Sable's interface for blocks that act on a contraption. The
 * rope's end point, {@code getAttachmentPoint}, comes from the parent class.
 */
public class AeroBucketBlockEntity extends ExcavatorBucketBlockEntity implements RopeStrandHolderBlockEntity {

    /**
     * The rope end of this block. Set in addBehaviours, which runs before the
     * fields get their starting values, so it must not have one here.
     */
    private RopeStrandHolderBehavior ropeHolder;

    public AeroBucketBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /** The plug-in parts of this block entity: the rope end. Called while the block entity is being created. */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        ropeHolder = new RopeStrandHolderBehavior(this);
        behaviours.add(ropeHolder);
    }

    @Override
    public RopeStrandHolderBehavior getBehavior() {
        return ropeHolder;
    }

    @Override
    public boolean hasRope() {
        return ropeHolder != null && ropeHolder.isAttached();
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        if (level instanceof ServerLevel serverLevel && Sable.HELPER.getContaining(this) == subLevel) {
            tickOnSimulatedContraption(serverLevel, new SableToolHost(serverLevel));
        }
    }
}
