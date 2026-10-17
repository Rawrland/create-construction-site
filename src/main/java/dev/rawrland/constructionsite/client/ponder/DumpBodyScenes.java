package dev.rawrland.constructionsite.client.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyBlock;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyBlockEntity;
import dev.rawrland.constructionsite.registry.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;

/**
 * Ponder scenes of the Dump Body. Structure files: assets/create_construction_site/ponder/dump_body/
 * (written by tools/ponder/generate_schematics.py).
 *
 * Scripted like the other scenes: what the dump body holds, which gate is open
 * and how its sides are set is changed by the script, in the same data a saved
 * world keeps; the dump body's own renderer draws it. A dump body's anchor is
 * its corner with the lowest x, y and z.
 */
final class DumpBodyScenes {

    /** Direction.get3DDataValue() of west: the gate that opens in the tipping scene. */
    private static final int WEST = 4;

    private DumpBodyScenes() {
    }

    // ------------------------------------------------------------- helpers

    /** Joins the dump body blocks in the selection into one box with the given anchor and sizes. */
    private static void join(SceneBuilder scene, Selection blocks, BlockPos anchor, int sx, int sy, int sz) {
        scene.world().modifyBlockEntityNBT(blocks, DumpBodyBlockEntity.class, tag -> {
            // A block entity's saved data holds its own position as x, y and z.
            tag.putInt("AnchorX", anchor.getX() - tag.getInt("x"));
            tag.putInt("AnchorY", anchor.getY() - tag.getInt("y"));
            tag.putInt("AnchorZ", anchor.getZ() - tag.getInt("z"));
            tag.putInt("SizeX", sx);
            tag.putInt("SizeY", sy);
            tag.putInt("SizeZ", sz);
        });
    }

    /** Puts a dirt block into each dump body cell of the selection. */
    private static void fill(SceneBuilder scene, Selection cells) {
        scene.world().modifyBlockEntityNBT(cells, DumpBodyBlockEntity.class,
            tag -> tag.put("StoredBlock", NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState())));
    }

    /** Empties the dump body cells of the selection. */
    private static void empty(SceneBuilder scene, Selection cells) {
        scene.world().modifyBlockEntityNBT(cells, DumpBodyBlockEntity.class, tag -> {
            tag.remove("StoredBlock");
            tag.remove("StoredBlockEntity");
        });
    }

    /** Opens one gate of the dump body whose anchor is given (side as 3D index, -1 for none), hanging at an angle. */
    private static void openGate(SceneBuilder scene, SceneBuildingUtil util, BlockPos anchor, int side, float angle) {
        scene.world().modifyBlockEntityNBT(util.select().position(anchor),
            DumpBodyBlockEntity.class, tag -> {
                tag.putInt(DumpBodyBlockEntity.TAG_OPEN_SIDE, side);
                tag.putFloat(DumpBodyBlockEntity.TAG_OPEN_ANGLE, angle);
            });
        leanWest(scene, util, anchor, angle);
    }

    /** Tells the dump body it leans this many degrees to the west, so its load is drawn slid that way. */
    private static void leanWest(SceneBuilder scene, SceneBuildingUtil util, BlockPos anchor, float degrees) {
        double radians = Math.toRadians(degrees);
        scene.world().modifyBlockEntityNBT(util.select().position(anchor), DumpBodyBlockEntity.class, tag -> {
            tag.putFloat(DumpBodyBlockEntity.TAG_LEAN_X, (float) -Math.sin(radians));
            tag.putFloat(DumpBodyBlockEntity.TAG_LEAN_Y, (float) -Math.cos(radians));
            tag.putFloat(DumpBodyBlockEntity.TAG_LEAN_Z, 0.0F);
        });
    }

    // -------------------------------------------------------------- scenes

    /**
     * A single dump body block, then a layer 3 wide and 5 long that joins into one
     * dump body, then one more placed on its top adds the whole second layer at once. A new
     * body is closed all round; the wrench opens its south side into a tailgate.
     */
    static void sizes(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("dump_body_sizes", "Sizes and the Wrench");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos single = util.grid().at(1, 1, 4);
        scene.world().showSection(util.select().position(single), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
            .text("A Dump Body carries loose material on a contraption and tips it out")
            .pointAt(util.vector().topOf(single))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        Selection body = util.select().fromTo(4, 1, 2, 6, 2, 6);
        Selection bottom = util.select().fromTo(4, 1, 2, 6, 1, 6);
        scene.world().showSection(bottom, Direction.DOWN);
        scene.idle(10);
        join(scene, bottom, util.grid().at(4, 1, 2), 3, 1, 5);
        scene.effects().indicateSuccess(util.grid().at(5, 1, 4));
        scene.idle(15);
        scene.overlay().showText(90)
            .text("Dump Body blocks placed together join into one open-top box, up to 7 wide, 15 long and 4 high")
            .pointAt(util.vector().topOf(5, 1, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        // One more on top of the full top side: the whole layer at once.
        scene.overlay().showControls(util.vector().topOf(5, 1, 4), Pointing.DOWN, 30)
            .rightClick()
            .withItem(ModItems.DUMP_BODY.get().getDefaultInstance());
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(4, 2, 2, 6, 2, 6), Direction.DOWN);
        scene.idle(5);
        join(scene, body, util.grid().at(4, 1, 2), 3, 2, 5);
        scene.effects().indicateSuccess(util.grid().at(5, 2, 4));
        scene.idle(15);
        scene.overlay().showText(90)
            .text("A Dump Body placed on a full side of one adds that whole layer at once. Sneak to place a single block")
            .pointAt(util.vector().topOf(5, 2, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(80)
            .text("Every block holds one block of material, so this one holds 30")
            .pointAt(util.vector().topOf(5, 2, 3))
            .placeNearTarget();
        scene.idle(90);

        // A new body is closed all round; the wrench opens the south side into a tailgate.
        BlockPos southMiddle = util.grid().at(5, 1, 6);
        scene.overlay().showText(90)
            .text("A new Dump Body is closed on all sides; each shows a locking bar")
            .pointAt(util.vector().blockSurface(southMiddle, Direction.SOUTH))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showControls(util.vector().blockSurface(southMiddle, Direction.SOUTH), Pointing.DOWN, 30)
            .rightClick()
            .withItem(AllItems.WRENCH.asStack());
        scene.idle(10);
        scene.world().modifyBlocks(body, state -> state.setValue(DumpBodyBlock.GATE_SOUTH, true), false);
        scene.idle(20);
        scene.overlay().showText(100)
            .text("Use a Wrench on a side to make it a tailgate, which opens when the body is tipped toward it. Wrench it again to close it")
            .pointAt(util.vector().blockSurface(southMiddle, Direction.SOUTH))
            .placeNearTarget();
        scene.idle(110);
        scene.markAsFinished();
    }

    /**
     * A dump body 3 by 3 on the ground and a full bucket above its east column.
     * The bucket tips and its load ends up in the dump body, not on the ground.
     */
    static void loading(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("dump_body_loading", "Loading");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(3, 1, 3, 5, 1, 5), Direction.DOWN);
        scene.idle(10);
        Selection bucket = util.select().fromTo(6, 3, 3, 6, 3, 5);
        ElementLink<WorldSectionElement> bucketLink = scene.world().showIndependentSection(bucket, Direction.DOWN);
        scene.world().configureCenterOfRotation(bucketLink, util.vector().of(6, 3, 4.5));
        scene.idle(15);

        scene.overlay().showText(90)
            .text("Material that falls into a Dump Body is stored in it, also when it stands on the ground")
            .pointAt(util.vector().topOf(4, 1, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        scene.world().rotateSection(bucketLink, 0, 0, 30, 20);
        scene.idle(22);
        int[] order = {5, 4, 3};
        for (int z : order) {
            ToolSceneHelper.emptyBuckets(scene, util.select().position(6, 3, z));
            fill(scene, util.select().position(5, 1, z));
            scene.world().rotateSection(bucketLink, 0, 0, 5, 8);
            scene.idle(10);
        }
        scene.idle(10);
        scene.overlay().showText(90)
            .text("It is shown inside the body, never higher than its walls. In a deep body, material heaps and then spreads")
            .pointAt(util.vector().topOf(5, 1, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.world().rotateSection(bucketLink, 0, 0, -45, 25);
        scene.idle(30);
        scene.overlay().showText(80)
            .text("Engineer's Goggles show how full it is and what it holds")
            .pointAt(util.vector().topOf(4, 1, 4))
            .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    /**
     * A full dump body 3 by 3 on a Mechanical Bearing, closed on all sides as it is
     * placed. Tipped to the west, nothing leaves: the dirt only slides toward the west
     * wall. Back to level, the wrench opens the west side into a tailgate. Tipped again,
     * from about 20 degrees the tailgate swings open, hanging straight down, and the load
     * streams out from the low end, until at 45 degrees it is empty.
     */
    static void tipping(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("dump_body_tipping", "Tipping");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos bearing = util.grid().at(4, 2, 6);
        BlockPos anchor = util.grid().at(3, 2, 3);
        Selection body = util.select().fromTo(3, 2, 3, 5, 2, 5);
        Selection drive = util.select().fromTo(4, 1, 6, 4, 2, 7);
        scene.world().showSection(drive, Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> bodyLink = scene.world().showIndependentSection(body, Direction.DOWN);
        scene.world().configureCenterOfRotation(bodyLink, util.vector().of(4.5, 2.5, 4.5));
        scene.idle(15);

        scene.overlay().showText(90)
            .text("On a moving contraption, such as this Mechanical Bearing, a Dump Body empties when it is tilted")
            .pointAt(util.vector().topOf(4, 2, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        // First with every side closed, as it is placed: tipped to 30 degrees, it keeps its load.
        BlockPos westMiddle = util.grid().at(3, 2, 4);
        scene.world().setKineticSpeed(drive, 8);
        scene.world().rotateBearing(bearing, 30, 25);
        scene.world().rotateSection(bodyLink, 0, 0, 30, 25);
        scene.idle(25);
        scene.world().setKineticSpeed(drive, 0);
        leanWest(scene, util, anchor, 30.0F);
        scene.idle(10);
        scene.overlay().showText(90)
            .text("With the low side closed, nothing comes out: the load only slides against the wall")
            .pointAt(util.vector().blockSurface(westMiddle, Direction.WEST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        // Back to level, and the wrench opens the west side.
        scene.world().setKineticSpeed(drive, -8);
        scene.world().rotateBearing(bearing, -30, 25);
        scene.world().rotateSection(bodyLink, 0, 0, -30, 25);
        scene.idle(25);
        scene.world().setKineticSpeed(drive, 0);
        leanWest(scene, util, anchor, 0.0F);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(westMiddle, Direction.WEST), Pointing.DOWN, 30)
            .rightClick()
            .withItem(AllItems.WRENCH.asStack());
        scene.idle(10);
        scene.world().modifyBlocks(body, state -> state.setValue(DumpBodyBlock.GATE_WEST, true), false);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("Use a Wrench to open that side into a tailgate")
            .pointAt(util.vector().blockSurface(westMiddle, Direction.WEST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        // Tipped again: from 20 degrees the tailgate opens and the low lane leaves first.
        scene.world().setKineticSpeed(drive, 8);
        scene.world().rotateBearing(bearing, 20, 20);
        scene.world().rotateSection(bodyLink, 0, 0, 20, 20);
        scene.idle(20);
        openGate(scene, util, anchor, WEST, 20.0F);
        scene.idle(6);
        BlockPos[][] heap = {
            {util.grid().at(2, 1, 3), util.grid().at(2, 1, 4), util.grid().at(2, 1, 5)},
            {util.grid().at(1, 1, 3), util.grid().at(1, 1, 4), util.grid().at(1, 1, 5)},
            {util.grid().at(2, 2, 3), util.grid().at(2, 2, 4), util.grid().at(2, 2, 5)},
        };
        for (int lane = 0; lane < 3; lane++) {
            empty(scene, util.select().fromTo(3 + lane, 2, 3, 3 + lane, 2, 5));
            for (BlockPos landed : heap[lane]) {
                scene.world().setBlock(landed, Blocks.DIRT.defaultBlockState(), false);
            }
            scene.world().showSection(util.select().fromTo(heap[lane][0], heap[lane][2]), Direction.DOWN);
            if (lane < 2) {
                scene.world().rotateBearing(bearing, 12.5F, 10);
                scene.world().rotateSection(bodyLink, 0, 0, 12.5, 10);
                openGate(scene, util, anchor, WEST, 20.0F + 12.5F * (lane + 1));
            }
            scene.idle(14);
        }
        scene.world().setKineticSpeed(drive, 0);
        scene.overlay().showText(90)
            .text("Now the tailgate swings open, hanging straight down, and the load streams out from the low end")
            .pointAt(util.vector().topOf(2, 2, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90)
            .text("A full body starts at about 20 degrees; the last blocks leave at about 45. The side that points down the most is the one that opens")
            .pointAt(util.vector().topOf(4, 2, 4))
            .placeNearTarget();
        scene.idle(100);

        // Back to level, gate shut.
        scene.world().setKineticSpeed(drive, -8);
        scene.world().rotateBearing(bearing, -45, 30);
        scene.world().rotateSection(bodyLink, 0, 0, -45, 30);
        openGate(scene, util, anchor, -1, 0.0F);
        scene.idle(30);
        scene.world().setKineticSpeed(drive, 0);
        scene.markAsFinished();
    }
}
