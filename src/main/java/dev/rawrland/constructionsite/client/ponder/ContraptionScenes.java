package dev.rawrland.constructionsite.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Ponder scenes of the tools on Create's own contraptions, both on a Gantry: the bucket's
 * "Digging" and the blade's "Pushing a Pile and Filling Gaps".
 *
 * Structure files: excavator_bucket/digging and bulldozer_blade/gaps
 * (see tools/ponder/generate_schematics.py). Scripted like the other scenes:
 * what a tool takes is set by the script (see {@link ToolSceneHelper}).
 */
final class ContraptionScenes {

    /** How fast the gantry shafts are shown turning while the carriage moves. */
    private static final float SPEED = 16.0F;

    private ContraptionScenes() {
    }

    /**
     * The bucket's "Digging" scene. A gantry carriage carries a single bucket, open side first,
     * toward a stack of two dirt blocks. The shaft ends there, so the carriage stops with the
     * bucket's opening right at the lower block: the bucket digs it without moving into the gap,
     * and the dirt above drops straight into the hole. (On a machine that keeps moving, the bucket
     * is under the gap by the time the dirt comes down, and the dirt lands on the bucket instead.)
     */
    static void digging(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("excavator_bucket_digging", "Digging");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        Selection shaft = util.select().fromTo(5, 3, 4, 8, 3, 4);
        BlockPos carriage = util.grid().at(7, 2, 4);
        BlockPos bucket = util.grid().at(7, 1, 4);
        BlockPos dug = util.grid().at(4, 1, 4);
        BlockPos onTop = dug.above();

        scene.world().showSection(shaft, Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> tool =
            scene.world().showIndependentSection(util.select().position(carriage), Direction.DOWN);
        scene.idle(5);
        scene.world().showSectionAndMerge(util.select().position(bucket), Direction.DOWN, tool);
        scene.idle(10);
        scene.world().showSection(util.select().position(dug), Direction.DOWN);
        scene.idle(5);
        ElementLink<WorldSectionElement> topLink =
            scene.world().showIndependentSection(util.select().position(onTop), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(90)
            .text("On a moving contraption, such as this Gantry, an Excavator Bucket pushed open side first into loose material digs it")
            .pointAt(util.vector().topOf(bucket))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        // To the end of the shaft: the bucket's opening reaches the lower dirt block and digs it.
        scene.world().setKineticSpeed(shaft, SPEED);
        scene.world().moveSection(tool, util.vector().of(-2, 0, 0), 40);
        scene.idle(40);
        scene.world().setKineticSpeed(shaft, 0);
        scene.world().setBlock(dug, Blocks.AIR.defaultBlockState(), true);
        ToolSceneHelper.fillBuckets(scene, util.select().position(bucket), 1);
        scene.idle(5);
        // The dirt that stood on it drops into the hole.
        scene.world().moveSection(topLink, util.vector().of(0, -1, 0), 6);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("It takes the diggable blocks its opening and teeth reach, as long as it has room")
            .pointAt(util.vector().topOf(5, 1, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showText(80)
            .text("Diggable blocks standing on a dug block come down")
            .pointAt(util.vector().topOf(dug))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showText(90)
            .text("A bucket holds one block for each of its blocks, and shows its load inside")
            .pointAt(util.vector().topOf(5, 1, 4))
            .placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    /**
     * The blade's "Pushing a Pile and Filling Gaps" scene. A gantry carriage carries an empty blade 1 high and 3 wide
     * over a dirt floor. It takes a row of dirt onto its pile, carries it on, and when the pile is
     * over a trench the three blocks drop in and fill it; the blade drives on empty.
     */
    static void gaps(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("bulldozer_blade_gaps", "Pushing a Pile and Filling Gaps");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(5);

        Selection shaft = util.select().fromTo(0, 4, 4, 8, 4, 4);
        BlockPos carriage = util.grid().at(7, 3, 4);
        Selection bladeBlocks = util.select().fromTo(7, 2, 3, 7, 2, 5);
        Selection row = util.select().fromTo(5, 2, 3, 5, 2, 5);
        Selection trench = util.select().fromTo(3, 1, 3, 3, 1, 5);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 8, 1, 8), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(shaft, Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> blade =
            scene.world().showIndependentSection(util.select().position(carriage), Direction.DOWN);
        scene.idle(5);
        scene.world().showSectionAndMerge(bladeBlocks, Direction.DOWN, blade);
        scene.idle(10);
        scene.world().showSection(row, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(90)
            .text("On a moving contraption, such as this Gantry, a Bulldozer Blade pushes the loose material in front of it")
            .pointAt(util.vector().topOf(7, 2, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        // One block forward: the blade meets the row and takes it onto its pile, where it lies.
        move(scene, shaft, blade, 1.0, 20);
        scene.world().setBlocks(row, Blocks.AIR.defaultBlockState(), false);
        for (int z = 3; z <= 5; z++) {
            ToolSceneHelper.addDirtToPile(scene, util.grid().at(7, 2, z), 0.0F, 0.0F);
        }
        scene.idle(10);
        scene.overlay().showText(80)
            .text("It takes diggable blocks such as dirt, sand and gravel, at its own height, and carries them as a pile")
            .pointAt(util.vector().topOf(5, 2, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90)
            .text("Each block of width carries 1 block on a blade 1 high, 4 on a blade 2 high and 9 on a blade 3 high")
            .pointAt(util.vector().topOf(7, 2, 3))
            .placeNearTarget();
        scene.idle(100);

        // On until the pile is over the trench: the three blocks drop in.
        move(scene, shaft, blade, 2.0, 40);
        scene.world().setBlocks(trench, Blocks.DIRT.defaultBlockState(), true);
        for (int z = 3; z <= 5; z++) {
            ToolSceneHelper.removeNewestFromPile(scene, util.grid().at(7, 2, z));
        }
        scene.idle(10);
        scene.overlay().showText(80)
            .text("When the pile passes over a gap, blocks drop into it and fill it")
            .pointAt(util.vector().topOf(3, 1, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(30);
        move(scene, shaft, blade, 2.0, 40);
        scene.world().setKineticSpeed(shaft, 0);
        scene.idle(30);

        scene.overlay().showText(80)
            .text("This is how a blade fills trenches and levels the ground")
            .pointAt(util.vector().topOf(3, 1, 4))
            .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    /** Moves the carriage with the blade the given number of blocks forward (toward -x), with the shaft turning. */
    private static void move(CreateSceneBuilder scene, Selection shaft,
                             ElementLink<WorldSectionElement> blade, double blocks, int ticks) {
        scene.world().setKineticSpeed(shaft, SPEED);
        scene.world().moveSection(blade, new Vec3(-blocks, 0, 0), ticks);
        scene.idle(ticks);
    }
}
