package dev.rawrland.constructionsite.content.console;

import dev.rawrland.constructionsite.registry.ModMenus;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The console's frequencies window, laid out like Create's Linked Controller: per control a
 * pair of ghost slots on top of each other (copies, the item is not used up). Top row: the
 * cross and the bonus buttons; bottom row: the levers. A click with an item puts a copy in, a
 * click with an empty hand clears it. The server applies each change at once.
 */
public class OperatorConsoleMenu extends AbstractContainerMenu {

    public static final int GHOSTS = ConsoleControl.COUNT * 2;

    /** Window background size (the player's inventory is drawn below it). */
    public static final int WIDTH = 226;
    public static final int HEIGHT = 160;
    /** Column step between controls, and the gap between the cross and the bonus buttons. */
    public static final int COLUMN = 24;
    public static final int GROUP_GAP = 11;
    public static final int TOP_ROW_X = 14;
    public static final int TOP_ROW_Y = 31;
    public static final int BOTTOM_ROW_X = (WIDTH - (4 * COLUMN + 18)) / 2;
    public static final int BOTTOM_ROW_Y = 87;
    /** Where Create's player inventory picture goes, relative to the window, and its slots. */
    public static final int INVENTORY_X = (WIDTH - 176) / 2;
    public static final int INVENTORY_Y = HEIGHT + 4;

    @Nullable
    private final OperatorConsoleBlockEntity console;
    private final Container ghosts = new SimpleContainer(GHOSTS);

    public OperatorConsoleMenu(int id, Inventory inventory, @Nullable OperatorConsoleBlockEntity console) {
        super(ModMenus.OPERATOR_CONSOLE.get(), id);
        this.console = console;
        if (console != null) {
            for (int i = 0; i < GHOSTS; i++) {
                ghosts.setItem(i, console.frequency(i).copy());
            }
        }
        for (int control = 0; control < ConsoleControl.COUNT; control++) {
            int x = slotX(control);
            int y = slotY(control);
            addSlot(new GhostSlot(ghosts, control * 2, x, y));
            addSlot(new GhostSlot(ghosts, control * 2 + 1, x, y + 18));
        }
        int invX = INVENTORY_X + 8;
        int invY = INVENTORY_Y + 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, invX + column * 18, invY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, invX + column * 18, invY + 58));
        }
    }

    /** Left edge of a control's slot pair in the window. */
    public static int slotX(int control) {
        if (ConsoleControl.isLever(control)) {
            return BOTTOM_ROW_X + (control - ConsoleControl.FIRST_LEVER) * COLUMN;
        }
        return TOP_ROW_X + control * COLUMN + (control >= ConsoleControl.CROSS ? GROUP_GAP : 0);
    }

    /** Top edge of a control's upper slot. */
    public static int slotY(int control) {
        return ConsoleControl.isLever(control) ? BOTTOM_ROW_Y : TOP_ROW_Y;
    }

    /** Client side, from the message that opens the window. */
    public static OperatorConsoleMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        OperatorConsoleBlockEntity console =
            inventory.player.level().getBlockEntity(pos) instanceof OperatorConsoleBlockEntity found ? found : null;
        return new OperatorConsoleMenu(id, inventory, console);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < GHOSTS) {
            ItemStack carried = getCarried();
            ItemStack copy = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
            ghosts.setItem(slotId, copy);
            if (console != null && !player.level().isClientSide) {
                console.setFrequency(slotId, copy);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-click from the inventory: no ghost slot takes it (pick it up and click instead).
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return console != null && console.inReach(player);
    }

    /** A slot that only shows its item; clicks are handled by the menu. */
    private static final class GhostSlot extends Slot {
        GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
