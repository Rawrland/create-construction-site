package dev.rawrland.constructionsite.compat.aeronautics.client;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.rawrland.constructionsite.content.bucket.BucketTeeth;
import dev.rawrland.constructionsite.compat.aeronautics.AeroBucketBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.ponder.elements.rope.RopeStrandElement;
import dev.simulated_team.simulated.ponder.instructions.CreateRopeStrandInstruction;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The bucket's rope scene. It uses Create Aeronautics' (Simulated's) own Ponder rope:
 * {@link RopeStrandElement}, added with {@link CreateRopeStrandInstruction} and moved with its
 * modify instruction, as Simulated's own rope scenes do (read in Simulated 1.3.1 source).
 * Only registered when Simulated is installed (see {@link ConstructionSitePonderPlugin}).
 *
 * Layout (structure file excavator_bucket/rope): a bucket 1 high and 3 wide at x 4, y 1,
 * z 3..5, facing west; an oak log at x 1, y 1, z 4 with a Rope Connector on top of it. A rope
 * is tied from the ring on the middle tooth of the bucket's middle block to the connector; a
 * rope always runs between two rope holders, never to a plain block. Then the bucket pulls the
 * log along by it.
 */
public final class BucketRopeScene {

    private static final int STEP_TICKS = 15;

    private BucketRopeScene() {
    }

    public static void rope(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("excavator_bucket_rope", "Tying a Rope");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(5);

        Selection bucket = util.select().fromTo(4, 1, 3, 4, 1, 5);
        BlockPos middleBlock = util.grid().at(4, 1, 4);
        BlockPos log = util.grid().at(1, 1, 4);
        BlockPos connector = util.grid().at(1, 2, 4);
        ElementLink<WorldSectionElement> bucketLink = scene.world().showIndependentSection(bucket, Direction.DOWN);
        scene.idle(5);
        ElementLink<WorldSectionElement> logLink =
            scene.world().showIndependentSection(util.select().position(log), Direction.DOWN);
        scene.idle(15);

        // Where the ring sits: on the middle tooth of the middle block, in front of the opening.
        Vec3 ring = ringPosition(util, 4);
        // Where the rope meets a connector standing on a block: a quarter block below its middle,
        // as in Create Aeronautics' own rope scenes.
        Vec3 connectorEnd = util.vector().centerOf(connector).subtract(0, 4.0 / 16.0, 0);

        scene.overlay().showText(90)
            .text("Each block of a bucket's width takes one Create Aeronautics rope, tied to its middle tooth")
            .pointAt(ring)
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        // A rope runs between two rope holders: the other end needs a Rope Connector, here on the log.
        scene.world().showSectionAndMerge(util.select().position(connector), Direction.DOWN, logLink);
        scene.idle(15);
        scene.overlay().showText(90)
            .text("The other end needs another rope holder, such as a Rope Connector, here placed on a log. A rope cannot be tied to a plain block")
            .pointAt(connectorEnd)
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(ring, Pointing.DOWN, 30)
            .rightClick()
            .withItem(SimItems.ROPE_COUPLING.asStack());
        scene.idle(35);
        scene.overlay().showControls(connectorEnd.add(0, 0.5, 0), Pointing.DOWN, 30)
            .rightClick()
            .withItem(SimItems.ROPE_COUPLING.asStack());
        scene.idle(20);

        // The rope appears, a little slack, and both ends show as tied.
        RopeStrandElement rope = new RopeStrandElement(ring, connectorEnd, 3, 0.6, 1);
        scene.addInstruction(new CreateRopeStrandInstruction(rope));
        scene.world().modifyBlockEntity(middleBlock, AeroBucketBlockEntity.class, be -> {
            if (be.getBehavior() != null) {
                be.getBehavior().renderAttached = true;
            }
        });
        scene.world().modifyBlockEntity(connector, RopeConnectorBlockEntity.class,
            be -> be.getRopeHolder().renderAttached = true);
        scene.idle(70);

        // The bucket backs away: the rope tightens.
        scene.world().moveSection(bucketLink, util.vector().of(1, 0, 0), STEP_TICKS);
        rope.modify(STEP_TICKS)
            .setStart(ring.add(1, 0, 0))
            .setSog(0.05)
            .start(scene);
        scene.idle(STEP_TICKS + 5);

        // And pulls the log along.
        scene.world().moveSection(bucketLink, util.vector().of(2, 0, 0), STEP_TICKS * 2);
        scene.world().moveSection(logLink, util.vector().of(2, 0, 0), STEP_TICKS * 2);
        rope.modify(STEP_TICKS * 2)
            .setStart(ring.add(3, 0, 0))
            .setEnd(connectorEnd.add(2, 0, 0))
            .start(scene);
        scene.idle(STEP_TICKS * 2 + 5);

        scene.overlay().showText(90)
            .text("With it, an excavator can pull or lift things")
            .pointAt(connectorEnd.add(2, 0.5, 0))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }

    /**
     * The middle of the rope ring of the middle block of the 1 high, 3 wide bucket whose front is
     * at the given x. The bucket faces west; its "right" points north, so column 1 is the block at
     * z 4, and a position across the bucket counts from its left edge at z 6.
     */
    private static Vec3 ringPosition(SceneBuildingUtil util, int frontX) {
        int height = 1;
        float across = BucketTeeth.ringX(1, 3, height) / 16.0F;
        float up = BucketTeeth.ringY(height) / 16.0F;
        float back = BucketTeeth.ringZ(height) / 16.0F;
        return util.vector().of(frontX + back, 1 + up, 6 - across);
    }
}
