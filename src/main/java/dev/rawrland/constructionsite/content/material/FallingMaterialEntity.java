package dev.rawrland.constructionsite.content.material;

import dev.rawrland.constructionsite.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A block that was tipped out of a bucket and is on its way to the ground.
 *
 * It falls, and where it lands it becomes the same block again. If there is a
 * lower place right beside its landing place it slides there first, so that a
 * dumped load forms a heap and not a tower.
 */
public class FallingMaterialEntity extends Entity {

    /** Sent to the players' games, so they can draw the right block. */
    private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE =
        SynchedEntityData.defineId(FallingMaterialEntity.class, EntityDataSerializers.BLOCK_STATE);

    /** Downward speed gained per tick, the same as vanilla falling sand. */
    private static final double GRAVITY = 0.04;
    /** Air resistance per tick, the same as vanilla falling sand. */
    private static final double DRAG = 0.98;

    /** How often one block may slide to a lower place before it has to stay. Set by the spec. */
    private static final int MAX_SLIDES = 16;
    /** A block that has not landed after this many ticks (30 seconds) drops as an item. */
    private static final int MAX_AGE_TICKS = 600;
    /** How many places upward are tried when the block ends up inside a solid block. */
    private static final int MAX_STEPS_UP = 3;

    private static final String TAG_BLOCK_STATE = "BlockState";
    private static final String TAG_BLOCK_DATA = "BlockData";
    private static final String TAG_SLIDES = "Slides";

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** Extra data of the carried block (for example the items in a chest). Null if it has none. */
    @Nullable
    private CompoundTag blockData;

    private int slides;

    public FallingMaterialEntity(EntityType<? extends FallingMaterialEntity> type, Level level) {
        super(type, level);
    }

    /** Lets a block fall from the given place in the world. */
    public static void spawn(ServerLevel level, BlockPos place, BlockState state, @Nullable CompoundTag blockData) {
        FallingMaterialEntity entity = new FallingMaterialEntity(ModEntities.FALLING_MATERIAL.get(), level);
        entity.setPos(place.getX() + 0.5, place.getY() + 0.01, place.getZ() + 0.5);
        entity.setCarriedState(state);
        entity.blockData = blockData == null ? null : blockData.copy();
        level.addFreshEntity(entity);
    }

    public BlockState getCarriedState() {
        return entityData.get(DATA_BLOCK_STATE);
    }

    private void setCarriedState(BlockState state) {
        entityData.set(DATA_BLOCK_STATE, state);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BLOCK_STATE, Blocks.AIR.defaultBlockState());
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    // ------------------------------------------------------------------ falling

    @Override
    public void tick() {
        super.tick();

        // Fall. This runs on the server and in the players' games alike, so the fall looks smooth.
        setDeltaMovement(getDeltaMovement().add(0.0, -GRAVITY, 0.0));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(DRAG));

        if (level().isClientSide) {
            return;
        }
        if (getCarriedState().isAir()) {
            discard();
            return;
        }
        if (onGround()) {
            land();
        } else if (tickCount > MAX_AGE_TICKS) {
            dropAsItem();
        }
    }

    // ------------------------------------------------------------------ landing

    private void land() {
        Level level = level();
        BlockPos place = blockPosition();

        // Two blocks released into the same spot land in the same place. The
        // second one then sits inside the first and has to go on top of it.
        int stepsUp = 0;
        while (!isFree(level, place) && isSolidBlock(level, place) && stepsUp < MAX_STEPS_UP) {
            place = place.above();
            stepsUp++;
        }
        if (!isFree(level, place)) {
            dropAsItem();
            return;
        }

        // Heap rule: if a place beside this one is free and has free room below
        // it, slide over there and fall on. Sides are tried in random order.
        if (slides < MAX_SLIDES) {
            List<Direction> sides = new ArrayList<>(List.of(SIDES));
            Collections.shuffle(sides, new java.util.Random(random.nextLong()));
            for (Direction side : sides) {
                BlockPos beside = place.relative(side);
                if (isFree(level, beside) && isFree(level, beside.below())) {
                    slides++;
                    setPos(beside.getX() + 0.5, place.getY() + 0.01, beside.getZ() + 0.5);
                    setDeltaMovement(0.0, 0.0, 0.0);
                    setOnGround(false);
                    return;
                }
            }
        }

        becomeBlock(level, place);
    }

    /** True if a block could be put here: the place is loaded and holds air or something replaceable like grass or water. */
    private static boolean isFree(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
            return false;
        }
        return level.getBlockState(pos).canBeReplaced();
    }

    private static boolean isSolidBlock(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
            return false;
        }
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    private void becomeBlock(Level level, BlockPos place) {
        BlockState state = getCarriedState();
        if (!state.canSurvive(level, place) || !level.setBlock(place, state, Block.UPDATE_ALL)) {
            dropAsItem();
            return;
        }
        if (blockData != null) {
            BlockEntity blockEntity = level.getBlockEntity(place);
            if (blockEntity != null) {
                blockEntity.loadWithComponents(blockData, level.registryAccess());
                blockEntity.setChanged();
            }
        }
        discard();
    }

    /** Last resort when the block cannot be put anywhere: it drops as an item, with what it contained. */
    private void dropAsItem() {
        Level level = level();
        BlockState state = getCarriedState();
        BlockPos pos = blockPosition();

        spawnAtLocation(state.getBlock());

        if (blockData != null && !(state.getBlock() instanceof ShulkerBoxBlock)) {
            BlockEntity blockEntity = BlockEntity.loadStatic(pos, state, blockData, level.registryAccess());
            if (blockEntity instanceof Container container) {
                Containers.dropContents(level, pos, container);
            }
        }
        discard();
    }

    // ------------------------------------------------------------------- saving

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put(TAG_BLOCK_STATE, NbtUtils.writeBlockState(getCarriedState()));
        if (blockData != null) {
            tag.put(TAG_BLOCK_DATA, blockData.copy());
        }
        tag.putInt(TAG_SLIDES, slides);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains(TAG_BLOCK_STATE, Tag.TAG_COMPOUND)) {
            setCarriedState(NbtUtils.readBlockState(
                level().holderLookup(Registries.BLOCK), tag.getCompound(TAG_BLOCK_STATE)
            ));
        }
        blockData = tag.contains(TAG_BLOCK_DATA, Tag.TAG_COMPOUND) ? tag.getCompound(TAG_BLOCK_DATA).copy() : null;
        slides = tag.getInt(TAG_SLIDES);
    }
}
