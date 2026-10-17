package dev.rawrland.constructionsite.compat.aeronautics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rawrland.constructionsite.compat.aeronautics.AeroBucketBlockEntity;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * Drawing the ropes tied to a bucket, with Create Aeronautics' own rope drawing.
 * Called by the bucket's renderer only when Create Aeronautics is installed
 * (see {@link dev.rawrland.constructionsite.compat.Mods#AERONAUTICS}).
 */
public final class BucketRopeRendering {

    private BucketRopeRendering() {
    }

    /** Draws the rope this block owns. For a block without a rope of its own this draws nothing. */
    public static void renderRope(ExcavatorBucketBlockEntity bucket, float partialTick, PoseStack poseStack,
                                  MultiBufferSource bufferSource) {
        RopeStrandHolderBehavior holder = holder(bucket);
        if (holder != null) {
            RopeStrandRenderer.render(bucket, holder, partialTick, poseStack, bufferSource);
        }
    }

    /**
     * True if a Ponder scene shows a rope tied to this block. Create Aeronautics' own
     * Ponder scenes mark a shown rope this way: the rope holder's renderAttached on a
     * virtual block entity.
     */
    public static boolean showsRopeInPonder(ExcavatorBucketBlockEntity hook) {
        RopeStrandHolderBehavior holder = holder(hook);
        return hook.isVirtual() && holder != null && holder.renderAttached;
    }

    /** The space taken by the rope this block owns in the player's game, or null if it owns none. */
    @Nullable
    public static AABB ownedRopeBounds(ExcavatorBucketBlockEntity bucket) {
        RopeStrandHolderBehavior holder = holder(bucket);
        if (holder == null || !holder.ownsRope()) {
            return null;
        }
        ClientRopeStrand rope = holder.getClientStrand();
        return rope == null ? null : rope.getBounds();
    }

    @Nullable
    private static RopeStrandHolderBehavior holder(ExcavatorBucketBlockEntity bucket) {
        return bucket instanceof AeroBucketBlockEntity aero ? aero.getBehavior() : null;
    }
}
