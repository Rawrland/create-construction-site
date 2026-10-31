package dev.rawrland.constructionsite.network;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.console.OperatorConsoleBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent when a player scrolls while looking at a lever of an Operator Console.
 *
 * @param pos   the console
 * @param lever 0 to 4
 * @param steps signal steps, positive forward
 */
public record ConsoleScrollPayload(BlockPos pos, int lever, int steps) implements CustomPacketPayload {

    public static final Type<ConsoleScrollPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, "console_scroll"));

    public static final StreamCodec<ByteBuf, ConsoleScrollPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ConsoleScrollPayload::pos,
        ByteBufCodecs.VAR_INT, ConsoleScrollPayload::lever,
        ByteBufCodecs.VAR_INT, ConsoleScrollPayload::steps,
        ConsoleScrollPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server. */
    public static void handle(ConsoleScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.level().isLoaded(payload.pos())
                && player.level().getBlockEntity(payload.pos()) instanceof OperatorConsoleBlockEntity console) {
                console.scroll(player, payload.lever(), payload.steps());
            }
        });
    }
}
