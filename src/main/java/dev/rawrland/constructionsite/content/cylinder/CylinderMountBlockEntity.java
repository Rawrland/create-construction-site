package dev.rawrland.constructionsite.content.cylinder;

import dev.rawrland.constructionsite.Config;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One end of a hydraulic cylinder.
 *
 * Both ends carry the same settings (add-cylinder-settings): decorative or driven,
 * tube length, speed and redstone direction. The tube (barrel) and the rod are both
 * as long as the tube length, so the cylinder reaches from one tube length (fully in)
 * to two (fully out).
 *
 * A decorative cylinder is only drawn. A driven one pushes the bodies its mounts sit on
 * toward the length redstone asks for, each end its own body, with a limited force
 * (add-cylinder-push). The barrel end draws the whole cylinder.
 */
public class CylinderMountBlockEntity extends BlockEntity {

    /** The shortest cylinder: shorter than this, the two mounts would overlap. */
    public static final double MIN_LENGTH = 1.5;
    /** The longest cylinder that can be linked. */
    public static final double MAX_LENGTH = 24.0;

    /** Tube length limits and step in the settings window, in blocks. */
    public static final float MIN_TUBE = 1.0F;
    public static final float MAX_TUBE = 12.0F;
    public static final float TUBE_STEP = 0.5F;
    /** Speed limits and step in the settings window, in blocks per second. */
    public static final float MIN_SPEED = 0.5F;
    public static final float MAX_SPEED = 8.0F;
    public static final float SPEED_STEP = 0.5F;
    public static final float DEFAULT_SPEED = 2.0F;

    /** How quickly the cylinder slows down toward its target: per second. */
    private static final double APPROACH = 1.5;
    /** Within this many blocks of its target the cylinder only holds still. */
    private static final double DEADBAND = 0.08;
    /**
     * Share of the speed difference corrected in each physics step. With real masses
     * (15:20) this can be high: it is what damps the swinging. (Was 0.4; 15:37 test swung
     * between 2.4 and 3.3 every 4 seconds.)
     */
    private static final double CORRECTION = 0.8;
    /**
     * How fast the holding change builds up: per second, per block per second of lag. Low,
     * or it drives the slow swinging itself. (Was 0.8.)
     */
    private static final double HOLDING_RATE = 0.3;
    /**
     * The same while stuck: faster, so a load that does not move yet gets its push soon.
     * (15:45 test: past its end it hung for 6 seconds before the slow rate had built enough.)
     */
    private static final double STUCK_RATE = 1.5;
    /**
     * The holding change never grows past this (blocks per second per step). The test boom
     * needed about 1.1. (15:35 test: blocked at 4.13 it grew to 25 and then pushed the wrong
     * way for 12 seconds after the target changed.)
     */
    private static final double HOLDING_MAX = 4.0;
    /** The holding change only builds up within this many blocks of the target. */
    private static final double HOLDING_ZONE = 0.75;
    /**
     * The end stops look this far ahead, in seconds: toward an end the cylinder may move at
     * most as fast as would reach it in this time, so it brakes before it, not after.
     */
    private static final double STOP_LOOKAHEAD = 0.15;
    /**
     * The end stops correct this much more than the computed impulse: the hinges carry part of
     * every push away (30 October 2026, 15:24 test: squished to 2.18 of 2.5).
     */
    private static final double STOP_GAIN = 1.5;
    /** Slower than this (blocks per second) while it should move, the cylinder counts as stuck. */
    private static final double STALL_SPEED = 0.15;
    /** Past an end stop the cylinder is pulled back at this many blocks per second per block. */
    private static final double STOP_RETURN = 10.0;
    /** Past either end of its reach, it moves back at this many blocks per second per block outside. */
    private static final double RETURN = 4.0;
    /** Seconds over which the measured speed is smoothed. */
    private static final double SMOOTHING = 0.05;

    @Nullable
    private UUID linkId;
    private boolean barrelEnd;
    /** Tube length = barrel length = rod length. */
    private float tubeLength;
    private boolean driven;
    private float speed = DEFAULT_SPEED;
    /** True: 15 pulls the cylinder in, 0 pushes it out. */
    private boolean inverted;
    /** Barrel end only: the strongest redstone signal around it, 0 to 15. */
    private int signal;

    /** This end's last measured length; not saved. Negative: not measured yet. */
    private double lastLength = -1.0;
    /** This end's speed of lengthening, smoothed over about a tenth of a second; not saved. */
    private double movingSmooth;
    /**
     * Extra speed added every step that has built up while the cylinder lagged behind: what it
     * takes to hold a load against gravity. Not saved. (30 October 2026: without it a cylinder
     * stopped short of its target under load, and low speed settings could not lift at all.)
     */
    private double holdingChange;

    public CylinderMountBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CYLINDER_MOUNT.get(), pos, state);
    }

    // ------------------------------------------------------------------ the link

    public boolean isLinked() {
        return linkId != null;
    }

    @Nullable
    public UUID linkId() {
        return linkId;
    }

    /** True for the end that draws the barrel (the first mount clicked). */
    public boolean isBarrelEnd() {
        return barrelEnd;
    }

    public float tubeLength() {
        return tubeLength;
    }

    /** The barrel is as long as the tube length. */
    public float barrelLength() {
        return tubeLength;
    }

    /** The rod is as long as the tube length. */
    public float rodLength() {
        return tubeLength;
    }

    public boolean isDriven() {
        return driven;
    }

    public float speed() {
        return speed;
    }

    public boolean isInverted() {
        return inverted;
    }

    public int signal() {
        return signal;
    }

    /** Fully in: one tube length. */
    public double shortestLength() {
        return tubeLength;
    }

    /** Fully out: two tube lengths. */
    public double longestLength() {
        return 2.0 * tubeLength;
    }

    /** Barrel end: the length the redstone asks for. 0 fully in, 15 fully out (or the other way when inverted). */
    public double targetLength() {
        int s = inverted ? 15 - signal : signal;
        return tubeLength + s / 15.0 * tubeLength;
    }

    /** The loaded other end, or null. */
    @Nullable
    public CylinderMountBlockEntity partner() {
        // Safety net in case the game loaded this block without calling onLoad.
        CylinderLinks.register(this);
        return CylinderLinks.partner(this);
    }

    /** Ties this end to a cylinder. Server side. */
    void link(UUID id, boolean barrel, float tube) {
        CylinderLinks.unregister(this);
        linkId = id;
        barrelEnd = barrel;
        tubeLength = tube;
        driven = false;
        speed = DEFAULT_SPEED;
        inverted = false;
        CylinderLinks.register(this);
        changed();
    }

    /** Loosens this end: it shows no cylinder any more. Server side. */
    void unlink() {
        CylinderLinks.unregister(this);
        linkId = null;
        barrelEnd = false;
        driven = false;
        changed();
    }

    /** New settings from the settings window. Server side; call on both ends. */
    void applySettings(boolean newDriven, float newTube, float newSpeed, boolean newInverted) {
        driven = newDriven;
        // A decorative cylinder keeps its other settings (owner: decorative makes them not configurable).
        if (newDriven) {
            tubeLength = snap(newTube, MIN_TUBE, MAX_TUBE, TUBE_STEP);
            speed = snap(newSpeed, MIN_SPEED, MAX_SPEED, SPEED_STEP);
            inverted = newInverted;
        }
        lastLength = -1.0;
        movingSmooth = 0.0;
        holdingChange = 0.0;
        changed();
    }

    /**
     * Settings from a player's settings window (add-cylinder-settings). Server side.
     * Applies them to both ends, if the player is within 10 blocks and both ends are loaded.
     */
    public static void configure(Player player, BlockPos pos, boolean driven, float tube, float speed,
                                 boolean inverted) {
        Level level = player.level();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof CylinderMountBlockEntity end)
            || !end.isLinked()) {
            return;
        }
        double reach = SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level, player.getEyePosition(),
            Vec3.atCenterOf(pos));
        if (reach > 10.0 * 10.0) {
            return;
        }
        CylinderMountBlockEntity other = end.partner();
        if (other == null) {
            CylinderMountBlock.message(player, "message.create_construction_site.hydraulic_cylinder.other_end_missing");
            return;
        }
        end.applySettings(driven, tube, speed, inverted);
        other.applySettings(driven, tube, speed, inverted);
        (end.isBarrelEnd() ? end : other).readSignal();
    }

    /** The tube length a new cylinder gets: its length is then in the middle of its reach. */
    static float defaultTube(double length) {
        return snap((float) (length / 1.5), MIN_TUBE, MAX_TUBE, TUBE_STEP);
    }

    static float snap(float value, float min, float max, float step) {
        return Mth.clamp(Math.round(value / step) * step, min, max);
    }

    /** Barrel end: reads the strongest redstone signal around the mount. Server side. */
    void readSignal() {
        if (level == null || level.isClientSide) {
            return;
        }
        int now = level.getBestNeighborSignal(worldPosition);
        if (now != signal) {
            signal = now;
            changed();
        }
    }

    // ------------------------------------------------------------------ pushing

    /**
     * The change of speed this end gives its own body in one physics step, or null for none,
     * as a world-space velocity in blocks per second for Sable's
     * {@code RigidBodyHandle.addLinearAndAngularVelocity}. Called from Sable's physics tick
     * (see AeroCylinderMountBlockEntity).
     *
     * Rule (owner, 29 October 2026: "move towards the desired length, not just jump"): the
     * cylinder wants to move toward its target length at its speed setting, slowing down
     * near it and standing still there. Each step it corrects part of the difference between
     * that wanted speed and the speed it really has. The change per step is limited by the
     * maximum acceleration (config), except at the end stops. Past either end of its reach it
     * moves back into it.
     *
     * Returns what is wanted; AeroCylinderMountBlockEntity turns it into an impulse at the pin
     * from both bodies' real masses, so a light and a heavy part behave the same.
     */
    @Nullable
    protected Push push(double timeStep) {
        if (!driven || linkId == null || level == null || level.isClientSide || timeStep <= 0) {
            return null;
        }
        SubLevelAccess own = SableCompanion.INSTANCE.getContaining(this);
        CylinderMountBlockEntity other = partner();
        if (own == null || other == null) {
            lastLength = -1.0;
            movingSmooth = 0.0;
        holdingChange = 0.0;
            return null;
        }
        CylinderMountBlockEntity barrel = barrelEnd ? this : other;
        Vec3 barrelPin = worldCentre(barrel);
        Vec3 rodPin = worldCentre(barrelEnd ? other : this);
        Vec3 line = rodPin.subtract(barrelPin);
        double length = line.length();
        if (length < 1.0E-3) {
            return null;
        }
        Vec3 dir = line.scale(1.0 / length);
        double rawMoving = lastLength < 0 ? 0.0 : (length - lastLength) / timeStep;
        lastLength = length;
        // A part resting on the ground shakes a little at every physics step; smoothed slightly.
        movingSmooth += (rawMoving - movingSmooth) * Math.min(1.0, timeStep / SMOOTHING);
        double moving = movingSmooth;

        double target = barrel.targetLength();
        double error = target - length;
        // Toward the target at the set speed, slower near it, standing still at it.
        double wanted = Math.abs(error) < DEADBAND ? 0.0 : Mth.clamp(error * APPROACH, -barrel.speed(), barrel.speed());
        // Past either end of its reach: back into it, the further out the faster.
        double over = length - barrel.longestLength();
        double under = barrel.shortestLength() - length;
        if (over > 0) {
            wanted = Math.min(wanted, -over * RETURN);
        } else if (under > 0) {
            wanted = Math.max(wanted, under * RETURN);
        }

        double limit = Config.CYLINDER_MAX_ACCELERATION.get() * timeStep;
        double lag = wanted - moving;
        boolean stuck = Math.abs(moving) < STALL_SPEED && Math.abs(wanted) > STALL_SPEED;
        if (Math.abs(error) < HOLDING_ZONE || stuck) {
            // Builds up close to the target (to hold a load there) and whenever the cylinder is
            // stuck (to lift a load it cannot move yet). Not while it moves freely: built up
            // during a move it carried the arm past its target and made it swing (14:59).
            // (15:24 test: faded out during a move, it stalled at 4.13 of 5 under the boom.)
            // Stuck the wrong way, it builds the other way, so it cannot hold the arm away
            // from its target (15:03).
            double cap = Math.min(limit, HOLDING_MAX);
            double rate = stuck ? STUCK_RATE : HOLDING_RATE;
            holdingChange = Mth.clamp(holdingChange + lag * rate * timeStep, -cap, cap);
        }
        // Otherwise it is kept as it is: the load still needs carrying while it moves.
        double change = Mth.clamp(lag * CORRECTION + holdingChange, -limit, limit);
        // Hard end stops (owner, 30 October 2026: "a real HARD limit for max and min"). At or past
        // either end of the reach, any movement further out is cancelled completely in this step,
        // not limited by the acceleration, and past the end it is pulled straight back. With the
        // bodies' real masses (AeroCylinderMountBlockEntity) the impulse for that is computed
        // exactly; only what the hinges and the ground take away is left over.
        // Fastest allowed speed outward and inward: near an end only as fast as reaches it in
        // STOP_LOOKAHEAD, past it backward.
        double maxOut = over >= 0 ? -over * STOP_RETURN : -over / STOP_LOOKAHEAD;
        double maxIn = under >= 0 ? -under * STOP_RETURN : -under / STOP_LOOKAHEAD;
        if (moving > maxOut) {
            change = Math.min(change, (maxOut - moving) * STOP_GAIN);
        } else if (-moving > maxIn) {
            change = Math.max(change, (-maxIn - moving) * STOP_GAIN);
        }
        return new Push(change, dir, other);
    }

    /**
     * What one physics step asks of the cylinder: change the speed at which the two pins move
     * apart by {@code change} blocks per second (negative: toward each other). {@code dir} points
     * from the barrel pin to the rod pin, in the world.
     */
    protected record Push(double change, Vec3 dir, CylinderMountBlockEntity other) {
    }

    /**
     * Fallback when the body's mass cannot be read: the push as a plain change of this body's
     * speed, aimed at the pin (the way it worked until 30 October 2026, 15:20). Each end moves
     * only its own body; with both ends on moving bodies, each does half.
     */
    protected Move speedMove(Push push) {
        SubLevelAccess own = SableCompanion.INSTANCE.getContaining(this);
        double share = SableCompanion.INSTANCE.getContaining(push.other()) != null ? 0.5 : 1.0;
        Vec3 atPin = push.dir().scale(push.change() * share * (barrelEnd ? -1.0 : 1.0));
        if (own == null) {
            return new Move(atPin, Vec3.ZERO);
        }
        Vec3 pin = worldCentre(this);
        org.joml.Vector3dc centre = own.logicalPose().position();
        Vec3 arm = pin.subtract(centre.x(), centre.y(), centre.z());
        double armSq = arm.lengthSqr();
        if (armSq < 0.01) {
            return new Move(atPin, Vec3.ZERO);
        }
        Vec3 turn = arm.cross(atPin).scale(0.5 / armSq);
        return new Move(atPin.scale(0.5), turn);
    }

    /** A change of the body's speed: plain speed (blocks per second) and turning speed (radians per second), world directions. */
    protected record Move(Vec3 linear, Vec3 angular) {
    }

    /**
     * How easily this end's body gives way at the pin along the cylinder: 1 / effective mass,
     * written by each physics step (see AeroCylinderMountBlockEntity), read by the other end.
     * Negative: not known yet. Not saved. Public because the Aeronautics subclass, in another
     * package, reads it on the other end (protected would not allow that).
     */
    public volatile double giveAtPin = -1.0;

    /** A mount's pin in the world as it is now (server side). */
    private static Vec3 worldCentre(CylinderMountBlockEntity end) {
        Vec3 centre = Vec3.atCenterOf(end.getBlockPos());
        SubLevelAccess body = SableCompanion.INSTANCE.getContaining(end);
        return body == null ? centre : body.logicalPose().transformPosition(centre);
    }

    /**
     * Distance between the centres of two mounts, in the world as the player sees it.
     * Works for mounts on the ground, on one contraption, or on two different ones.
     */
    static double distance(Level level, BlockPos a, BlockPos b) {
        return Math.sqrt(SableCompanion.INSTANCE.distanceSquaredWithSubLevels(
            level, Vec3.atCenterOf(a), Vec3.atCenterOf(b)));
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------- loading state

    @Override
    public void onLoad() {
        super.onLoad();
        CylinderLinks.register(this);
    }

    @Override
    public void setRemoved() {
        CylinderLinks.unregister(this);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        CylinderLinks.unregister(this);
        super.onChunkUnloaded();
    }

    // ------------------------------------------------------------ saving, syncing

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linkId != null) {
            tag.putUUID("CylinderLink", linkId);
            tag.putBoolean("BarrelEnd", barrelEnd);
            tag.putFloat("TubeLength", tubeLength);
            tag.putBoolean("Driven", driven);
            tag.putFloat("Speed", speed);
            tag.putBoolean("Inverted", inverted);
            tag.putInt("Signal", signal);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CylinderLinks.unregister(this);
        linkId = tag.hasUUID("CylinderLink") ? tag.getUUID("CylinderLink") : null;
        barrelEnd = tag.getBoolean("BarrelEnd");
        if (tag.contains("TubeLength")) {
            tubeLength = tag.getFloat("TubeLength");
        } else {
            // Cylinders from before the settings window kept barrel and rod lengths.
            tubeLength = snap(tag.getFloat("BarrelLength"), MIN_TUBE, MAX_TUBE, TUBE_STEP);
        }
        driven = tag.getBoolean("Driven");
        speed = tag.contains("Speed") ? tag.getFloat("Speed") : DEFAULT_SPEED;
        inverted = tag.getBoolean("Inverted");
        signal = tag.getInt("Signal");
        // Moved by a contraption assembly, or updated from the server: known again at once.
        if (level != null) {
            CylinderLinks.register(this);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
