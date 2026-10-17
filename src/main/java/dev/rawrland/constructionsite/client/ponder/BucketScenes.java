package dev.rawrland.constructionsite.client.ponder;

import com.simibubi.create.AllItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Ponder scenes of the Excavator Bucket. Structure files: assets/create_construction_site/ponder/excavator_bucket/.
 *
 * The buckets face west (open side toward -x). A bucket's anchor, its bottom front left block,
 * is the one with the lowest x and the highest z.
 *
 * Scenes are scripted: what a bucket digs and empties is set by the script (see {@link ToolSceneHelper}),
 * and the bucket's own renderer draws its load from that.
 */
final class BucketScenes {

    private static final int MOUNT_TOP = 0;
    private static final int MOUNT_BACK = 1;
    private static final int MOUNT_NONE = 2;

    /** The three buckets of the sizes scene: front x, height, and how many blocks each grows on each side. */
    private static final int[][] BUCKETS = {
        {1, 1, 1},
        {3, 2, 2},
        {6, 3, 3},
    };
    /** The middle of every bucket's width in the sizes scene. */
    private static final int MIDDLE_Z = 4;

    private BucketScenes() {
    }

    /**
     * First a single bucket on its own. Then a 2 and a 3 high bucket behind it. Each starts one block wide and
     * grows a block on each side at a time until it is as wide as it can be (3, 5, 7).
     * Then the wrench on the biggest one: the mount, and picking up a slice.
     */
    static void sizes(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("excavator_bucket_sizes", "Sizes and the Wrench");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        // First a single bucket, and what it is for.
        BlockPos single = util.grid().at(1, 1, MIDDLE_Z);
        scene.world().showSection(util.select().position(single), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
            .text("Excavator Buckets dig loose material and carry it when a moving contraption pushes them into it")
            .pointAt(util.vector().topOf(single))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        // Then the two bigger heights, one block wide each.
        for (int i = 1; i < BUCKETS.length; i++) {
            int x = BUCKETS[i][0], height = BUCKETS[i][1];
            Selection slice = sliceAt(util, x, height, MIDDLE_Z);
            scene.world().showSection(slice, Direction.DOWN);
            scene.idle(8);
            ToolSceneHelper.joinBuckets(scene, slice, util.grid().at(x, 1, MIDDLE_Z), height, 1);
            scene.effects().indicateSuccess(util.grid().at(x, height, MIDDLE_Z));
            scene.idle(12);
        }
        scene.overlay().showText(80)
            .text("A bucket is 1, 2 or 3 blocks high, and as deep as it is high")
            .pointAt(util.vector().topOf(6, 3, MIDDLE_Z))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        // Each grows a block on each side at a time, until it is as wide as it can be.
        scene.overlay().showText(70)
            .text("Bucket blocks placed beside a bucket make it wider")
            .pointAt(util.vector().topOf(3, 2, MIDDLE_Z + 1))
            .placeNearTarget()
            .attachKeyFrame();
        for (int step = 1; step <= 3; step++) {
            for (int[] bucket : BUCKETS) {
                if (step > bucket[2]) {
                    continue;
                }
                int x = bucket[0], height = bucket[1];
                scene.world().showSection(sliceAt(util, x, height, MIDDLE_Z - step)
                    .add(sliceAt(util, x, height, MIDDLE_Z + step)), Direction.DOWN);
            }
            scene.idle(10);
            for (int[] bucket : BUCKETS) {
                if (step > bucket[2]) {
                    continue;
                }
                int x = bucket[0], height = bucket[1];
                ToolSceneHelper.joinBuckets(scene, bucketBlocks(util, x, height, step),
                    util.grid().at(x, 1, MIDDLE_Z + step), height, 2 * step + 1);
                scene.effects().indicateSuccess(util.grid().at(x, height, MIDDLE_Z + step));
            }
            scene.idle(20);
        }
        scene.idle(30);
        scene.overlay().showText(90)
            .text("A bucket can be up to 3 blocks wide when 1 high, 5 when 2 high and 7 when 3 high")
            .pointAt(util.vector().topOf(6, 3, 1))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        // The wrench moves the mount of the biggest bucket. It sits on the back, so turn the view round.
        Selection biggest = bucketBlocks(util, 6, 3, 3);
        BlockPos backMiddle = util.grid().at(8, 2, MIDDLE_Z);
        scene.rotateCameraY(180);
        scene.idle(25);
        scene.overlay().showText(80)
            .text("Use a Wrench to move the mount: from the back, to hidden, to the top, and back again")
            .pointAt(util.vector().blockSurface(backMiddle, Direction.EAST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(20);
        for (int mount : new int[] {MOUNT_NONE, MOUNT_TOP, MOUNT_BACK}) {
            scene.overlay().showControls(util.vector().blockSurface(backMiddle, Direction.EAST), Pointing.DOWN, 20)
                .rightClick()
                .withItem(AllItems.WRENCH.asStack());
            scene.idle(7);
            ToolSceneHelper.setBucketMount(scene, biggest, mount);
            scene.idle(30);
        }
        scene.rotateCameraY(-180);
        scene.idle(25);

        // Sneaking with the wrench picks up one slice: the biggest bucket's right end.
        Selection endSlice = sliceAt(util, 6, 3, 1);
        scene.overlay().showControls(util.vector().topOf(6, 3, 1), Pointing.DOWN, 30)
            .rightClick()
            .whileSneaking()
            .withItem(AllItems.WRENCH.asStack());
        scene.idle(10);
        scene.world().hideSection(endSlice, Direction.UP);
        ToolSceneHelper.separateBuckets(scene, endSlice);
        ToolSceneHelper.joinBuckets(scene, util.select().fromTo(6, 1, 2, 8, 3, 7), util.grid().at(6, 1, 7), 3, 6);
        scene.idle(15);
        scene.overlay().showText(80)
            .text("Sneak and use a Wrench to pick up a whole slice of a bucket")
            .pointAt(util.vector().topOf(6, 3, 2))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);
        scene.markAsFinished();
    }

    // ----------------------------------------------------------------- emptying

    /**
     * A full bucket 1 high and 3 wide is tipped forward about its front bottom edge. At about 20
     * degrees the first block comes out, the newest first; the last leaves at about 45 degrees.
     * The blocks fall and form a heap.
     */
    static void emptying(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("excavator_bucket_emptying", "Emptying");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(5);

        Selection bucket = util.select().fromTo(4, 3, 3, 4, 3, 5);
        ElementLink<WorldSectionElement> bucketLink = scene.world().showIndependentSection(bucket, Direction.DOWN);
        // Tipped about the front bottom edge, the lip, along the bucket's width.
        scene.world().configureCenterOfRotation(bucketLink, util.vector().of(4, 3, 4.5));
        scene.idle(20);

        scene.overlay().showText(80)
            .text("On a moving contraption, an Excavator Bucket empties when it is tipped so its open side points down")
            .pointAt(util.vector().topOf(4, 3, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        // Tip to about 20 degrees: a full bucket starts to spill.
        scene.world().rotateSection(bucketLink, 0, 0, 20, 20);
        scene.idle(25);
        // The newest block first, then the others, while it tips further to 45 degrees.
        int[] order = {5, 4, 3};
        BlockPos[] landing = {util.grid().at(3, 1, 4), util.grid().at(3, 1, 3), util.grid().at(3, 1, 5)};
        for (int i = 0; i < order.length; i++) {
            ToolSceneHelper.emptyBuckets(scene, util.select().position(4, 3, order[i]));
            scene.world().setBlock(landing[i], Blocks.DIRT.defaultBlockState(), true);
            scene.world().showSection(util.select().position(landing[i]), Direction.DOWN);
            scene.world().rotateSection(bucketLink, 0, 0, 25.0 / order.length, 10);
            scene.idle(12);
        }
        scene.idle(10);
        scene.overlay().showText(90)
            .text("The load comes out as a stream, newest blocks first, falls, and lands as a heap")
            .pointAt(util.vector().topOf(3, 1, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90)
            .text("A full bucket starts to spill at about 20 degrees; the last block leaves at about 45")
            .pointAt(util.vector().topOf(4, 3, 4))
            .placeNearTarget();
        scene.idle(100);

        // Back to level.
        scene.world().rotateSection(bucketLink, 0, 0, -45, 30);
        scene.idle(40);
        scene.markAsFinished();
    }

    /** One slice of a bucket: every block at one place along its width. */
    private static Selection sliceAt(SceneBuildingUtil util, int frontX, int height, int z) {
        return util.select().fromTo(frontX, 1, z, frontX + height - 1, height, z);
    }

    /** All blocks of a bucket that has grown the given number of blocks to each side of the middle. */
    private static Selection bucketBlocks(SceneBuildingUtil util, int frontX, int height, int grown) {
        return util.select().fromTo(frontX, 1, MIDDLE_Z - grown, frontX + height - 1, height, MIDDLE_Z + grown);
    }
}
