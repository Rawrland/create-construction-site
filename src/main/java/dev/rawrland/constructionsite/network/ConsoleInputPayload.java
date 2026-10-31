package dev.rawrland.constructionsite.network;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.console.OperatorConsoleBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the game of the player in control of an Operator Console (add-operator-console):
 * the keys held, when they change and every few ticks while held.
 *
 * @param pos   the console
 * @param keys  bit 2i: lever i forward, bit 2i+1: lever i back
 * @param leave the player gives control back
 */
public record ConsoleInputPayload(BlockPos pos, int keys, boolean leave) implements CustomPacketPayload {

    public static final Type<ConsoleInputPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, "console_input"));

    public static final StreamCodec<ByteBuf, ConsoleInputPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ConsoleInputPayload::pos,
        ByteBufCodecs.VAR_INT, ConsoleInputPayload::keys,
        ByteBufCodecs.BOOL, ConsoleInputPayload::leave,
        ConsoleInputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server. */
    public static void handle(ConsoleInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.level().isLoaded(payload.pos())
                && player.level().getBlockEntity(payload.pos()) instanceof OperatorConsoleBlockEntity console
                && !console.receiveInput(player, payload.keys(), payload.leave()) && !payload.leave()) {
                player.displayClientMessage(Component.translatable(
                    "message.create_construction_site.operator_console.in_use"), true);
            }
        });
    }
}
