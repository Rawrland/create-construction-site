package dev.rawrland.constructionsite.network;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.cylinder.CylinderMountBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by a player's game when the hydraulic cylinder settings window closes
 * (add-cylinder-settings). The server checks the player is close enough and
 * applies the settings to both ends of the cylinder.
 *
 * @param pos      the mount the window was opened on
 * @param driven   decorative (false) or driven (true)
 * @param tube     tube length in blocks
 * @param speed    speed in blocks per second
 * @param inverted redstone 15 pulls in instead of pushing out
 */
public record CylinderSettingsPayload(BlockPos pos, boolean driven, float tube, float speed, boolean inverted)
    implements CustomPacketPayload {

    public static final Type<CylinderSettingsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, "cylinder_settings"));

    public static final StreamCodec<ByteBuf, CylinderSettingsPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, CylinderSettingsPayload::pos,
        ByteBufCodecs.BOOL, CylinderSettingsPayload::driven,
        ByteBufCodecs.FLOAT, CylinderSettingsPayload::tube,
        ByteBufCodecs.FLOAT, CylinderSettingsPayload::speed,
        ByteBufCodecs.BOOL, CylinderSettingsPayload::inverted,
        CylinderSettingsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server. */
    public static void handle(CylinderSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CylinderMountBlockEntity.configure(context.player(), payload.pos(),
            payload.driven(), payload.tube(), payload.speed(), payload.inverted()));
    }
}
