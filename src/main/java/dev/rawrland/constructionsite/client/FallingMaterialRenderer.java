package dev.rawrland.constructionsite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rawrland.constructionsite.content.material.FallingMaterialEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Draws falling material as the block it carries. */
public class FallingMaterialRenderer extends EntityRenderer<FallingMaterialEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public FallingMaterialRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(FallingMaterialEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        BlockState state = entity.getCarriedState();
        if (state.getRenderShape() == RenderShape.MODEL) {
            poseStack.pushPose();
            // The entity's position is the middle of its underside; a block is drawn from its corner.
            poseStack.translate(-0.5, 0.0, -0.5);
            blockRenderer.renderSingleBlock(state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FallingMaterialEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
