package dev.rawrland.constructionsite.network;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.tool.ContraptionToolShadow;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the server when a tool block on one of Create's own contraptions has
 * changed its data (it dug something, let something go, joined differently).
 *
 * The players' games draw a moving contraption from their own copies of its
 * block entities, made once when the contraption appeared. Without this message
 * they would keep showing the load the tool had when it started moving.
 *
 * @param entityId the contraption entity's id
 * @param localPos the tool block's position in the contraption's own grid
 * @param data     the tool block's data, as it is sent to the players' games
 */
public record ContraptionToolDataPayload(int entityId, BlockPos localPos, CompoundTag data)
    implements CustomPacketPayload {

    public static final Type<ContraptionToolDataPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, "contraption_tool_data"));

    public static final StreamCodec<ByteBuf, ContraptionToolDataPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ContraptionToolDataPayload::entityId,
        BlockPos.STREAM_CODEC, ContraptionToolDataPayload::localPos,
        ByteBufCodecs.COMPOUND_TAG, ContraptionToolDataPayload::data,
        ContraptionToolDataPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs in the player's game: updates the contraption's copy of the block. */
    public static void handle(ContraptionToolDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (!(level.getEntity(payload.entityId()) instanceof AbstractContraptionEntity entity)) {
                return;
            }
            Contraption contraption = entity.getContraption();
            if (contraption == null) {
                return;
            }
            // The data the copy is made from again, should the game rebuild it.
            StructureBlockInfo info = contraption.getBlocks().get(payload.localPos());
            if (info != null && info.nbt() != null) {
                ContraptionToolShadow.replaceContent(info.nbt(), payload.data());
            }
            // The copy that is drawn.
            BlockEntity copy = contraption.getBlockEntityClientSide(payload.localPos());
            if (copy != null) {
                copy.handleUpdateTag(payload.data(), level.registryAccess());
            }
        });
    }
}
