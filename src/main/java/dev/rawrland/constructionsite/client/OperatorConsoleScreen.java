package dev.rawrland.constructionsite.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.console.ConsoleControl;
import dev.rawrland.constructionsite.content.console.OperatorConsoleMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Operator Console's frequencies window, in Create's style (see
 * tools/gui/generate_console_gui.py for the background): a slot pair per control, the key that
 * works it written above, Create's inventory picture below and Create's confirm button.
 */
public class OperatorConsoleScreen extends AbstractContainerScreen<OperatorConsoleMenu> {

    private static final ResourceLocation BACKGROUND =
        ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, "textures/gui/operator_console.png");

    /** Knob colours of the levers, the same as on the block. */
    public static final int[] LEVER_COLOURS = {0xFFD33A2C, 0xFFE8B02A, 0xFF4CAF50, 0xFF3B82D6, 0xFFE6E6E6};

    public OperatorConsoleScreen(OperatorConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = OperatorConsoleMenu.WIDTH;
        imageHeight = OperatorConsoleMenu.INVENTORY_Y + AllGuiTextures.PLAYER_INVENTORY.getHeight();
    }

    @Override
    protected void init() {
        super.init();
        IconButton confirm = new IconButton(leftPos + OperatorConsoleMenu.WIDTH - 33,
            topPos + OperatorConsoleMenu.HEIGHT - 24, AllIcons.I_CONFIRM);
        confirm.withCallback(() -> minecraft.player.closeContainer());
        addRenderableWidget(confirm);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, OperatorConsoleMenu.WIDTH, OperatorConsoleMenu.HEIGHT, 256, 256);
        AllGuiTextures.PLAYER_INVENTORY.render(graphics, leftPos + OperatorConsoleMenu.INVENTORY_X,
            topPos + OperatorConsoleMenu.INVENTORY_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 15, 4, 0x592424, false);
        graphics.drawString(font, playerInventoryTitle, OperatorConsoleMenu.INVENTORY_X + 8,
            OperatorConsoleMenu.INVENTORY_Y + 6, 0x404040, false);

        // The key working each control, above its slot pair (small, so "Space" fits).
        for (int control = 0; control < ConsoleControl.COUNT; control++) {
            int x = OperatorConsoleMenu.slotX(control);
            int y = OperatorConsoleMenu.slotY(control) - 10;
            String label = OperatorConsoleControls.keyLabel(control);
            graphics.pose().pushPose();
            graphics.pose().translate(x + 9, y + 2, 0);
            graphics.pose().scale(0.75F, 0.75F, 1.0F);
            int width = font.width(label);
            graphics.drawString(font, label, -width / 2, 0, 0xFFFFFF, true);
            graphics.pose().popPose();
            if (ConsoleControl.isLever(control)) {
                int colour = LEVER_COLOURS[control - ConsoleControl.FIRST_LEVER];
                graphics.fill(x - 4, y + 2, x - 1, y + 5, colour);
            }
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (!menu.getCarried().isEmpty() || hoveredSlot == null || hoveredSlot.index >= OperatorConsoleMenu.GHOSTS) {
            super.renderTooltip(graphics, x, y);
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (hoveredSlot.hasItem()) {
            lines.addAll(getTooltipFromContainerItem(hoveredSlot.getItem()));
        }
        int control = hoveredSlot.index / 2;
        lines.add(Component.translatable("gui.create_construction_site.operator_console.slot_" + (hoveredSlot.index % 2 + 1),
                Component.translatable("gui.create_construction_site.operator_console." + ConsoleControl.id(control)),
                OperatorConsoleControls.keyLabel(control))
            .withStyle(ChatFormatting.GOLD));
        graphics.renderComponentTooltip(font, lines, x, y);
    }
}
