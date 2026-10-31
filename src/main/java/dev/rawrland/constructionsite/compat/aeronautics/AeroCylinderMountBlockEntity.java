package dev.rawrland.constructionsite.compat.aeronautics;

import dev.rawrland.constructionsite.content.cylinder.CylinderMountBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * The Cylinder Mount's block entity when Create Aeronautics is installed.
 *
 * Sable calls {@link #sable$physicsTick} on every physics step while the mount sits on a
 * simulated contraption. A driven cylinder's end then pushes its own body at its pin.
 *
 * How hard (30 October 2026, owner: "a real HARD limit"): {@link #push} says by how much the
 * pins' speed apart should change. From each body's mass and inertia (Sable's MassData,
 * {@code getInverseNormalMass}) it is worked out how easily each body gives way at its pin
 * along the cylinder; the impulse J = change / (give here + give there) is exactly what makes
 * the two pins reach that speed, as a real rod between them would. The rod end gets +J along
 * the cylinder, the barrel end -J. An end on the ground does not give way at all.
 *
 * Names Sable classes, so it is only ever loaded when Create Aeronautics is installed
 * (ModBlockEntities chooses it after checking {@code Mods.AERONAUTICS}).
 */
public class AeroCylinderMountBlockEntity extends CylinderMountBlockEntity implements BlockEntitySubLevelActor {

    public AeroCylinderMountBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        // Nothing per game tick; the work happens in the physics steps.
    }

    /**
     * RigidBodyHandle (owner looked it up in Sable 2.0.5): {@code applyImpulseAtPoint(position,
     * force)} takes the point in plot coordinates and the impulse in the body's own directions.
     * MassData.getInverseNormalMass takes the same two (point in plot, direction local).
     */
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        Push push = push(timeStep);
        if (push == null) {
            giveAtPin = -1.0;
            return;
        }
        // A body lying still may be asleep; a zero impulse with wakeUp = true wakes it first.
        handle.applyLinearAndAngularImpulse(new Vector3d(), new Vector3d(), true);

        MassData mass = SableMass.of(subLevel);
        SubLevelAccess own = SableCompanion.INSTANCE.getContaining(this);
        if (mass == null || own == null || mass.getCenterOfMass() == null || mass.getInverseMass() <= 0) {
            giveAtPin = -1.0;
            pushBySpeed(handle, push);
            return;
        }

        // The pin, and the cylinder's direction in this body's own frame (plot): two points
        // taken back through the pose, so no assumption about how it turns directions.
        Vec3 pin = Vec3.atCenterOf(getBlockPos());
        Vec3 worldPin = own.logicalPose().transformPosition(pin);
        Vec3 localDir = own.logicalPose().transformPositionInverse(worldPin.add(push.dir())).subtract(pin);
        if (localDir.lengthSqr() < 1.0E-8) {
            return;
        }
        localDir = localDir.normalize();
        Vector3d plotPin = new Vector3d(pin.x, pin.y, pin.z);
        Vector3d dirLocal = new Vector3d(localDir.x, localDir.y, localDir.z);

        double give = mass.getInverseNormalMass(plotPin, dirLocal);
        giveAtPin = give;
        double otherGive;
        if (SableCompanion.INSTANCE.getContaining(push.other()) == null) {
            otherGive = 0.0;                  // on the ground: does not give way
        } else if (push.other().giveAtPin >= 0) {
            otherGive = push.other().giveAtPin;
        } else {
            otherGive = give;                 // not known yet (first step): assume alike
        }
        double total = give + otherGive;
        if (total <= 1.0E-12) {
            return;
        }
        double impulse = push.change() / total;
        double sign = isBarrelEnd() ? -1.0 : 1.0;
        handle.applyImpulseAtPoint(plotPin, new Vector3d(dirLocal).mul(impulse * sign));
    }

    /** Without mass data: the old way, changing the body's speed directly. */
    private void pushBySpeed(RigidBodyHandle handle, Push push) {
        Move move = speedMove(push);
        Vec3 linear = move.linear();
        Vec3 angular = move.angular();
        handle.addLinearAndAngularVelocity(new Vector3d(linear.x, linear.y, linear.z),
            new Vector3d(angular.x, angular.y, angular.z));
    }
}
