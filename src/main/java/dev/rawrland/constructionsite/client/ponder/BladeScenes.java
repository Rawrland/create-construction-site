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
 * Ponder scenes of the Bulldozer Blade. Structure files: assets/create_construction_site/ponder/bulldozer_blade/.
 *
 * The blade faces west (working face toward -x) and is moved west as one group of blocks.
 * Its anchor, the bottom block at the left end, is the one with the highest z. Its "right"
 * points north, so its columns are counted from the highest z down.
 *
 * What the blade takes, carries and leaves is set by the script (see {@link ToolSceneHelper}).
 * The pile is drawn by the blade's own renderer from the blocks stored in the blade.
 * A blade 2 high stores a column's pile in its two blade blocks, two each, bottom one first;
 * a pile place is (depth, level): the first block (0, 0), then (0, 1), (1, 0), (2, 0).
 */
final class BladeScenes {

    private static final int MOUNT_CENTRE = 0;
    private static final int MOUNT_ENDS = 1;
    private static final int MOUNT_NONE = 2;

    /** How long the blade takes to move one block. */
    private static final int STEP_TICKS = 15;

    private BladeScenes() {
    }

    // --------------------------------------------------------------------- full

    static void full(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("bulldozer_blade_full", "When the Blade is Full, and Backing Away");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(5);

        Selection blade = util.select().fromTo(7, 1, 3, 7, 1, 4);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 8, 3, 8).substract(blade), Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> bladeLink = scene.world().showIndependentSection(blade, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("On a moving contraption, a Bulldozer Blade carries only as much as its pile can hold")
            .pointAt(util.vector().topOf(6, 1, 3))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        // Full: each row of dirt the pile meets is moved off the nearer end of the blade.
        for (int x = 5; x >= 4; x--) {
            shoveRowAside(scene, util, x);
            scene.world().moveSection(bladeLink, util.vector().of(-1, 0, 0), STEP_TICKS);
            scene.idle(STEP_TICKS + 5);
        }
        scene.overlay().showText(90)
            .text("When the blade is full, it moves further blocks off its nearer end, where they form a ridge")
            .pointAt(util.vector().topOf(4, 1, 2))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        // Backing away: the pile (now 1 block ahead of the blade at x 5) stays on the ground.
        scene.world().setBlocks(util.select().fromTo(4, 1, 3, 4, 1, 4), Blocks.DIRT.defaultBlockState(), false);
        ToolSceneHelper.clearPile(scene, blade);
        scene.world().moveSection(bladeLink, util.vector().of(2, 0, 0), STEP_TICKS * 2);
        scene.idle(STEP_TICKS * 2 + 5);
        scene.overlay().showText(90)
            .text("When the blade backs away, it leaves its pile on the ground as it was")
            .pointAt(util.vector().topOf(4, 1, 3))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }

    /**
     * The full 1 high, 2 wide blade meets the dirt row at x (z 3 and 4). Each block goes off the
     * nearer end of the blade: z 3 (its right end) to z 2, z 4 (its left end) to z 5.
     */
    private static void shoveRowAside(SceneBuilder scene, SceneBuildingUtil util, int x) {
        scene.world().setBlocks(util.select().fromTo(x, 1, 3, x, 1, 4), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(x, 1, 2), Blocks.DIRT.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(x, 1, 5), Blocks.DIRT.defaultBlockState(), false);
    }

    // -------------------------------------------------------------------- sizes

    /** The three blades of the sizes scene: x and height. Each grows from z 4 to z 0..8. */
    private static final int[][] BLADES = {
        {2, 1},
        {4, 2},
        {6, 3},
    };
    private static final int MIDDLE_Z = 4;
    private static final int GROW_STEPS = 4;

    /**
     * First a single blade on its own. Then a 2 and a 3 high blade behind it, each first one block wide, growing
     * a block on each side at a time until it is 9 wide. Then the wrench on the biggest one.
     */
    static void sizes(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("bulldozer_blade_sizes", "Sizes and the Wrench");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.9F);
        scene.showBasePlate();
        scene.idle(10);

        // First a single blade, and what it is for.
        BlockPos single = util.grid().at(2, 1, MIDDLE_Z);
        scene.world().showSection(util.select().position(single), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
            .text("On a moving contraption, a Bulldozer Blade pushes the loose material in front of it")
            .pointAt(util.vector().topOf(single))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        // Then the two taller ones, one block wide each.
        for (int i = 1; i < BLADES.length; i++) {
            int x = BLADES[i][0], height = BLADES[i][1];
            Selection column = columnAt(util, x, height, MIDDLE_Z);
            scene.world().showSection(column, Direction.DOWN);
            scene.idle(8);
            ToolSceneHelper.joinBlades(scene, column, util.grid().at(x, 1, MIDDLE_Z), height, 1);
            scene.effects().indicateSuccess(util.grid().at(x, height, MIDDLE_Z));
            scene.idle(12);
        }
        scene.overlay().showText(80)
            .text("Blade blocks placed on top of each other make a taller blade, up to 3 blocks high")
            .pointAt(util.vector().topOf(6, 3, MIDDLE_Z))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(70)
            .text("Blade blocks placed beside a blade make it wider")
            .pointAt(util.vector().topOf(4, 2, MIDDLE_Z + 1))
            .placeNearTarget()
            .attachKeyFrame();
        for (int step = 1; step <= GROW_STEPS; step++) {
            for (int[] b : BLADES) {
                scene.world().showSection(columnAt(util, b[0], b[1], MIDDLE_Z - step)
                    .add(columnAt(util, b[0], b[1], MIDDLE_Z + step)), Direction.DOWN);
            }
            scene.idle(10);
            for (int[] b : BLADES) {
                int x = b[0], height = b[1];
                ToolSceneHelper.joinBlades(scene, bladeBlocks(util, x, height, step),
                    util.grid().at(x, 1, MIDDLE_Z + step), height, 2 * step + 1);
                scene.effects().indicateSuccess(util.grid().at(x, height, MIDDLE_Z + step));
            }
            scene.idle(20);
        }
        scene.idle(30);
        scene.overlay().showText(80)
            .text("A blade can be up to 9 blocks wide, whatever its height")
            .pointAt(util.vector().topOf(6, 3, 0))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        // The wrench switches the mount on the back of the biggest blade. Turn the view round to see it.
        Selection biggest = bladeBlocks(util, 6, 3, GROW_STEPS);
        BlockPos back = util.grid().at(6, 2, MIDDLE_Z);
        scene.rotateCameraY(180);
        scene.idle(25);
        scene.overlay().showText(80)
            .text("Use a Wrench to switch the mount on the back: arms in the middle, at the ends, or hidden")
            .pointAt(util.vector().blockSurface(back, Direction.EAST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(20);
        for (int mount : new int[] {MOUNT_ENDS, MOUNT_NONE, MOUNT_CENTRE}) {
            scene.overlay().showControls(util.vector().blockSurface(back, Direction.EAST), Pointing.DOWN, 20)
                .rightClick()
                .withItem(AllItems.WRENCH.asStack());
            scene.idle(7);
            ToolSceneHelper.setBladeMount(scene, biggest, mount);
            scene.idle(30);
        }
        scene.rotateCameraY(-180);
        scene.idle(25);
        scene.markAsFinished();
    }

    /** One column of a blade: its blocks at one place along its width. */
    private static Selection columnAt(SceneBuildingUtil util, int x, int height, int z) {
        return util.select().fromTo(x, 1, z, x, height, z);
    }

    /** All blocks of a blade that has grown the given number of blocks to each side of the middle. */
    private static Selection bladeBlocks(SceneBuildingUtil util, int x, int height, int grown) {
        return util.select().fromTo(x, 1, MIDDLE_Z - grown, x, height, MIDDLE_Z + grown);
    }
}
