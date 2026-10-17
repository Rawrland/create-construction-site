package dev.rawrland.constructionsite.compat.aeronautics.client;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyBlockEntity;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * The "On Simulated Contraptions" scenes, one per tool, only registered when
 * Create Aeronautics is installed. Modelled on Create: Airwaves' "Airships" scene:
 * a still model of a vehicle builds up step by step, then texts point at the tool.
 * Nothing moves and nothing is simulated.
 *
 * The vehicle is the owner's design (17 October 2026); its layout is described in
 * tools/ponder/generate_schematics.py, function vehicle(). Structure files:
 * excavator_bucket/simulated and bulldozer_blade/simulated.
 */
public final class SimulatedContraptionScenes {

    private SimulatedContraptionScenes() {
    }

    public static void bucket(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("excavator_bucket_simulated", "On Simulated Contraptions");
        buildVehicle(scene, util);

        scene.overlay().showText(90)
            .text("Excavator Buckets also work on Create Aeronautics' simulated contraptions, such as vehicles")
            .pointAt(util.vector().topOf(2, 2, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90)
            .text("They dig, carry and empty the same way, by how the contraption moves them")
            .pointAt(util.vector().topOf(2, 2, 3))
            .placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void blade(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("bulldozer_blade_simulated", "On Simulated Contraptions");
        buildVehicle(scene, util);

        scene.overlay().showText(90)
            .text("Bulldozer Blades also work on Create Aeronautics' simulated contraptions, such as vehicles")
            .pointAt(util.vector().topOf(2, 2, 4))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90)
            .text("They push, fill gaps and leave their pile the same way, by how the contraption moves them")
            .pointAt(util.vector().topOf(2, 2, 3))
            .placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    /**
     * The Dump Body's scene: a full body held by a Create Aeronautics Swivel Bearing
     * (owner's choice for tipping on a vehicle, 17 October 2026), its west side opened.
     * The bearing turns, the body tips and empties over the west side. Scripted like
     * the other scenes; structure file dump_body/simulated.
     */
    public static void dumpBody(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("dump_body_simulated", "On Simulated Contraptions");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos anchor = util.grid().at(3, 2, 4);
        Selection drive = util.select().fromTo(4, 1, 7, 5, 2, 7);
        Selection body = util.select().fromTo(3, 2, 4, 5, 2, 6);
        scene.world().showSection(drive, Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> bodyLink = scene.world().showIndependentSection(body, Direction.DOWN);
        scene.world().configureCenterOfRotation(bodyLink, util.vector().of(4.5, 2.5, 5.0));
        scene.idle(15);

        scene.overlay().showText(90)
            .text("A Dump Body also works on Create Aeronautics' simulated contraptions. Here, as one example of many, a Swivel Bearing tips it, as on a dump truck")
            .pointAt(util.vector().topOf(4, 2, 7))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        scene.world().setKineticSpeed(drive, 8);
        scene.world().rotateSection(bodyLink, 0, 0, 20, 20);
        scene.idle(20);
        setGate(scene, util, anchor, 20.0F);
        scene.idle(6);
        for (int lane = 0; lane < 3; lane++) {
            scene.world().modifyBlockEntityNBT(util.select().fromTo(3 + lane, 2, 4, 3 + lane, 2, 6),
                DumpBodyBlockEntity.class, tag -> tag.remove("StoredBlock"));
            Selection landed = lane == 2 ? util.select().fromTo(2, 2, 4, 2, 2, 6)
                : util.select().fromTo(2 - lane, 1, 4, 2 - lane, 1, 6);
            scene.world().setBlocks(landed, Blocks.DIRT.defaultBlockState(), false);
            scene.world().showSection(landed, Direction.DOWN);
            if (lane < 2) {
                scene.world().rotateSection(bodyLink, 0, 0, 12.5, 10);
                setGate(scene, util, anchor, 20.0F + 12.5F * (lane + 1));
            }
            scene.idle(14);
        }
        scene.world().setKineticSpeed(drive, 0);
        scene.overlay().showText(90)
            .text("On a simulated contraption it catches, holds and empties the same way: over its lowest open side")
            .pointAt(util.vector().topOf(2, 2, 5))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);
        scene.world().setKineticSpeed(drive, -8);
        scene.world().rotateSection(bodyLink, 0, 0, -45, 30);
        setGate(scene, util, anchor, 0.0F);
        scene.idle(35);
        scene.world().setKineticSpeed(drive, 0);
        scene.markAsFinished();
    }

    /** The west gate open at this tilt (0: shut), and the load drawn slid that way. */
    private static void setGate(SceneBuilder scene, SceneBuildingUtil util, BlockPos anchor, float degrees) {
        double radians = Math.toRadians(degrees);
        scene.world().modifyBlockEntityNBT(util.select().position(anchor), DumpBodyBlockEntity.class, tag -> {
            tag.putInt(DumpBodyBlockEntity.TAG_OPEN_SIDE, degrees > 0 ? Direction.WEST.get3DDataValue() : -1);
            tag.putFloat(DumpBodyBlockEntity.TAG_OPEN_ANGLE, degrees);
            tag.putFloat(DumpBodyBlockEntity.TAG_LEAN_X, (float) -Math.sin(radians));
            tag.putFloat(DumpBodyBlockEntity.TAG_LEAN_Y, (float) -Math.cos(radians));
            tag.putFloat(DumpBodyBlockEntity.TAG_LEAN_Z, 0.0F);
        });
    }

    /** Shows the vehicle step by step: frame, wheels, engine, and last the tool at its front. */
    private static void buildVehicle(SceneBuilder scene, SceneBuildingUtil util) {
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        Selection frame = util.select().fromTo(3, 2, 4, 7, 2, 4);
        Selection wheels = util.select().fromTo(3, 2, 3, 6, 2, 3).add(util.select().fromTo(3, 2, 5, 6, 2, 5));
        Selection engine = util.select().fromTo(6, 3, 4, 7, 3, 4);
        Selection tool = util.select().fromTo(2, 2, 3, 2, 2, 5);

        ElementLink<WorldSectionElement> vehicle = scene.world().showIndependentSection(frame, Direction.DOWN);
        scene.idle(10);
        scene.world().showSectionAndMerge(wheels, Direction.DOWN, vehicle);
        scene.idle(10);
        scene.world().showSectionAndMerge(engine, Direction.DOWN, vehicle);
        scene.idle(10);
        scene.world().showSectionAndMerge(tool, Direction.EAST, vehicle);
        scene.idle(20);
    }
}
