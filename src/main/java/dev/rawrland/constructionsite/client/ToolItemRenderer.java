package dev.rawrland.constructionsite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rawrland.constructionsite.content.blade.BladeMount;
import dev.rawrland.constructionsite.content.bucket.BucketGroup;
import dev.rawrland.constructionsite.content.bucket.BucketMount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import dev.rawrland.constructionsite.content.tool.ToolPaint;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Draws the bucket, blade and dump body items with the same code as the placed tools, so
 * the item in the hand, the inventory and the creative tab looks exactly like
 * a placed single bucket (mount on the back) or single blade (mount in the middle),
 * frames included.
 *
 * Minecraft calls this for items whose model is "builtin/entity". The model's
 * display settings have already turned and scaled the view for the hand or the
 * inventory slot, and moved it so that 0 to 1 is the item's block space.
 */
public final class ToolItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** Which tool an item shows. */
    public enum Tool {
        BUCKET,
        BLADE,
        DUMP_BODY
    }

    /** The item faces like a tool placed facing north: open or working side toward north, back toward south. */
    private static final BucketGroup.Axes ITEM_AXES = new BucketGroup.Axes(Direction.EAST, Direction.UP, Direction.SOUTH);

    private final Tool tool;

    private ToolItemRenderer(Tool tool) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.tool = tool;
    }

    /** What NeoForge needs to know about an item drawn this way. The renderer is made when it is first needed. */
    public static IClientItemExtensions extensions(Tool tool) {
        return new IClientItemExtensions() {
            private ToolItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new ToolItemRenderer(tool);
                }
                return renderer;
            }
        };
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ToolPainter painter = new ToolPainter(
            bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)),
            poseStack.last(), ITEM_AXES, packedLight);
        DyeColor colour = stack.getItem() instanceof BlockItem item ? ToolPaint.of(item.getBlock()) : null;
        switch (tool) {
            case BUCKET -> ExcavatorBucketRenderer.drawBucket(painter, 1, 1, BucketMount.BACK, colour);
            case BLADE -> BulldozerBladeRenderer.drawBlade(painter, 1, 1, BladeMount.CENTRE, colour);
            case DUMP_BODY -> DumpBodyRenderer.drawEmptyBody(painter, 1, 1, 1, colour);
        }
    }
}
