package dev.rawrland.constructionsite.content.tool;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.network.ContraptionToolDataPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * The stand-in for a tool block on one of Create's own contraptions.
 *
 * While Create's contraption moves, its blocks have no block entities. Each
 * block only keeps its saved data (the movement context's
 * {@code blockEntityData}). To let the bucket and blade do the same work as on a
 * simulated contraption, a copy of the block entity is made from that data: a
 * "shadow". It lives in the movement context's {@code temporaryData}, so it is
 * never saved itself, and it has no level.
 *
 * After every tick its data is written back into the block's saved data. When
 * the contraption puts its blocks down again, the real block entity loads that
 * data, so the tool keeps its load. The players' games draw the moving tool from
 * their own copy, which is updated by {@link ContraptionToolDataPayload}.
 *
 * Only made on the server.
 */
public final class ContraptionToolShadow {

    /**
     * The key under which Create Aeronautics saves a rope tied to a block. A rope
     * cannot come along on Create's contraptions, and loading it needs a real level,
     * so the shadow never loads it. It stays in the block's saved data untouched.
     */
    private static final String TAG_ROPE_STRAND = "Strand";

    /**
     * Create keeps a second copy of each block's data for the players' games
     * (used when a player comes near the contraption, and after loading the world).
     * There is no way to reach it from outside, so it is looked up by name once.
     * If that ever fails, players who come near later only see the load once it changes.
     */
    @Nullable
    private static final Field UPDATE_TAGS = findUpdateTags();

    private final BlockEntity blockEntity;
    private final ContraptionTool tool;

    /** The data last written back. Only what changed since is written again. */
    private CompoundTag lastWritten;

    private ContraptionToolShadow(BlockEntity blockEntity, ContraptionTool tool, CompoundTag lastWritten) {
        this.blockEntity = blockEntity;
        this.tool = tool;
        this.lastWritten = lastWritten;
    }

    public BlockEntity blockEntity() {
        return blockEntity;
    }

    public ContraptionTool tool() {
        return tool;
    }

    /**
     * The shadow of the block of this movement context, made on first use.
     * Null in the players' games, and for a block that is not a tool.
     */
    @Nullable
    public static ContraptionToolShadow of(MovementContext context) {
        if (context.temporaryData instanceof ContraptionToolShadow shadow) {
            return shadow;
        }
        if (context.world.isClientSide) {
            return null;
        }
        ContraptionToolShadow shadow = create(context);
        if (shadow != null) {
            context.temporaryData = shadow;
        }
        return shadow;
    }

    @Nullable
    private static ContraptionToolShadow create(MovementContext context) {
        if (!(context.state.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }
        BlockEntity blockEntity = entityBlock.newBlockEntity(context.localPos, context.state);
        if (!(blockEntity instanceof ContraptionTool tool)) {
            return null;
        }
        HolderLookup.Provider registries = context.world.registryAccess();
        if (context.blockEntityData != null) {
            CompoundTag data = context.blockEntityData.copy();
            data.remove(TAG_ROPE_STRAND);
            blockEntity.loadWithComponents(data, registries);
        }
        tool.setHost(new ContraptionToolHost(context.contraption));
        return new ContraptionToolShadow(blockEntity, tool, blockEntity.saveWithoutMetadata(registries));
    }

    /**
     * Writes the shadow's data back into the block's saved data, if it changed.
     *
     * @param tellPlayers true to also send it to the players who see the contraption
     */
    public void writeBack(MovementContext context, boolean tellPlayers) {
        HolderLookup.Provider registries = context.world.registryAccess();
        CompoundTag now = blockEntity.saveWithoutMetadata(registries);
        if (now.equals(lastWritten)) {
            return;
        }

        // Changed in place: this compound is the block's data inside the contraption.
        // Keys the shadow no longer writes (an emptied bucket has no "StoredBlock") are removed.
        CompoundTag saved = context.blockEntityData;
        if (saved != null) {
            for (String key : lastWritten.getAllKeys()) {
                if (!now.contains(key)) {
                    saved.remove(key);
                }
            }
            for (String key : now.getAllKeys()) {
                saved.put(key, now.get(key).copy());
            }
        }
        lastWritten = now;

        CompoundTag forPlayers = blockEntity.getUpdateTag(registries);
        replaceUpdateTag(context.contraption, context.localPos, forPlayers);
        if (tellPlayers && context.contraption.entity != null) {
            PacketDistributor.sendToPlayersTrackingEntity(context.contraption.entity,
                new ContraptionToolDataPayload(context.contraption.entity.getId(), context.localPos, forPlayers));
        }
    }

    /** Replaces Create's copy of a block's data for the players' games, if it has one. */
    private static void replaceUpdateTag(Contraption contraption, BlockPos localPos, CompoundTag data) {
        if (UPDATE_TAGS == null) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<BlockPos, CompoundTag> updateTags = (Map<BlockPos, CompoundTag>) UPDATE_TAGS.get(contraption);
            CompoundTag existing = updateTags == null ? null : updateTags.get(localPos);
            if (existing != null) {
                replaceContent(existing, data);
            }
        } catch (IllegalAccessException | ClassCastException e) {
            // Not fatal: see UPDATE_TAGS.
        }
    }

    /** Makes {@code target} hold exactly what {@code data} holds, keeping the same compound. */
    public static void replaceContent(CompoundTag target, CompoundTag data) {
        for (String key : target.getAllKeys().toArray(new String[0])) {
            target.remove(key);
        }
        for (String key : data.getAllKeys()) {
            target.put(key, data.get(key).copy());
        }
    }

    @Nullable
    private static Field findUpdateTags() {
        try {
            Field field = Contraption.class.getDeclaredField("updateTags");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            CreateConstructionSite.LOGGER.warn("Could not reach Create's contraption update tags; "
                + "tools on Create contraptions may show an old load to players who arrive later", e);
            return null;
        }
    }
}
