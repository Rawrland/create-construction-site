package dev.rawrland.constructionsite.content.console;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import dev.ryanhcode.sable.companion.SableCompanion;
import java.util.Arrays;
import java.util.UUID;
import javax.annotation.Nullable;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Operator Console (add-operator-console): a cross of 4 buttons, 4 bonus buttons and 5 throttle
 * levers, each on its own Redstone Link frequency (see {@link ConsoleControl}).
 *
 * Buttons send 15 while the player in control holds their key. Levers send 0 to 15 and stay
 * where they are left (owner, 30 October 2026); held keys move them, scrolling steps them.
 * The server does the moving and tells the clients. Each control takes part in Create's
 * Redstone Link network as a transmitter (see docs/notes/create-redstone-link.md).
 */
public class OperatorConsoleBlockEntity extends BlockEntity {

    /** Seconds a held key takes to move a lever from one end to the other. */
    static final double FULL_TRAVEL_SECONDS = 1.5;
    /** Ticks without a key message from the player in control before he counts as gone. */
    static final int INPUT_TIMEOUT = 30;
    /** How far a player may be from the console to use it, in blocks. */
    public static final double REACH = 8.0;

    /** Lever positions, 0 (back) to 1 (forward). */
    private final float[] levers = new float[ConsoleControl.LEVERS];
    /** Buttons held right now, one bit each. */
    private int buttons;
    /** Two frequency items per control: slot 2c and 2c+1 belong to control c. */
    private final ItemStack[] frequencies = new ItemStack[ConsoleControl.COUNT * 2];
    private final ControlLink[] links = new ControlLink[ConsoleControl.COUNT];
    /** Server: the controls are in Create's network. */
    private boolean inNetwork;

    /** Server: the player in control, the keys held (see ConsoleControl's bits), when last heard. */
    @Nullable
    private UUID controller;
    private int held;
    private long lastInput;

    public OperatorConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OPERATOR_CONSOLE.get(), pos, state);
        Arrays.fill(frequencies, ItemStack.EMPTY);
        for (int i = 0; i < ConsoleControl.COUNT; i++) {
            links[i] = new ControlLink(i);
        }
    }

    // ------------------------------------------------------------------ values

    /** A lever's position, 0 to 1. */
    public float lever(int lever) {
        return levers[lever];
    }

    public boolean buttonDown(int button) {
        return (buttons & ConsoleControl.buttonBit(button)) != 0;
    }

    /** What a control sends: 0 to 15. */
    public int signal(int control) {
        if (ConsoleControl.isLever(control)) {
            return Math.round(levers[control - ConsoleControl.FIRST_LEVER] * 15.0F);
        }
        return buttonDown(control) ? 15 : 0;
    }

    public ItemStack frequency(int slot) {
        return frequencies[slot];
    }

    /** True if the player is close enough, also when the console sits on a moving contraption. */
    public boolean inReach(Player player) {
        if (level == null || isRemoved()) {
            return false;
        }
        return SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level, player.getEyePosition(),
            Vec3.atCenterOf(worldPosition)) <= REACH * REACH;
    }

    // ------------------------------------------------------------- server input

    /**
     * Keys from the player in control. Returns false if another player is in control.
     * {@code leave}: the player gives control back.
     */
    public boolean receiveInput(Player player, int keys, boolean leave) {
        long now = level.getGameTime();
        boolean someoneElse = controller != null && !controller.equals(player.getUUID())
            && now - lastInput <= INPUT_TIMEOUT;
        if (someoneElse || !inReach(player)) {
            return false;
        }
        if (leave) {
            release();
            return true;
        }
        controller = player.getUUID();
        held = keys;
        lastInput = now;
        setButtons(keys & ((1 << ConsoleControl.BUTTONS) - 1));
        return true;
    }

    private void release() {
        controller = null;
        held = 0;
        setButtons(0);
    }

    /** Scroll on a lever: one signal step per notch. */
    public void scroll(Player player, int lever, int steps) {
        if (lever < 0 || lever >= ConsoleControl.LEVERS || !inReach(player)) {
            return;
        }
        int signal = Mth.clamp(signal(ConsoleControl.FIRST_LEVER + lever) + steps, 0, 15);
        setLever(lever, signal / 15.0F);
        changed();
    }

    private void setButtons(int newButtons) {
        if (newButtons == buttons) {
            return;
        }
        int changedBits = newButtons ^ buttons;
        buttons = newButtons;
        if (inNetwork) {
            for (int b = 0; b < ConsoleControl.BUTTONS; b++) {
                if ((changedBits & ConsoleControl.buttonBit(b)) != 0) {
                    Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, links[b]);
                }
            }
        }
        changed();
    }

    private void setLever(int lever, float value) {
        int control = ConsoleControl.FIRST_LEVER + lever;
        int before = signal(control);
        levers[lever] = Mth.clamp(value, 0.0F, 1.0F);
        if (signal(control) != before && inNetwork) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, links[control]);
        }
    }

    /** Server: sets one frequency item (a copy of one). Slot 2c and 2c+1 belong to control c. */
    public void setFrequency(int slot, ItemStack stack) {
        ItemStack item = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (ItemStack.isSameItemSameComponents(item, frequencies[slot])) {
            return;
        }
        ControlLink link = links[slot / 2];
        // Create finds the network by the frequency: out with the old one, in with the new.
        if (inNetwork) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, link);
        }
        frequencies[slot] = item;
        link.updateKey();
        if (inNetwork) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, link);
        }
        changed();
    }

    // ------------------------------------------------------------------ ticking

    /** Server, every tick. */
    void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!inNetwork) {
            joinNetwork();
        }
        if (controller != null && level.getGameTime() - lastInput > INPUT_TIMEOUT) {
            release();
        }
        if (held == 0) {
            return;
        }
        float step = (float) (1.0 / (FULL_TRAVEL_SECONDS * 20.0));
        boolean moved = false;
        for (int i = 0; i < ConsoleControl.LEVERS; i++) {
            boolean forward = (held & ConsoleControl.leverForwardBit(i)) != 0;
            boolean back = (held & ConsoleControl.leverBackBit(i)) != 0;
            int direction = forward == back ? 0 : forward ? 1 : -1;
            if (direction == 0) {
                continue;
            }
            float before = levers[i];
            setLever(i, before + direction * step);
            moved |= levers[i] != before;
        }
        if (moved) {
            changed();
        }
    }

    private void joinNetwork() {
        for (ControlLink link : links) {
            link.updateKey();
        }
        inNetwork = true;
        for (ControlLink link : links) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, link);
        }
    }

    private void leaveNetwork() {
        if (!inNetwork || level == null || level.isClientSide) {
            return;
        }
        // While still alive, so the receivers are told their signal is gone.
        for (ControlLink link : links) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, link);
        }
        inNetwork = false;
    }

    @Override
    public void setRemoved() {
        leaveNetwork();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        leaveNetwork();
        super.onChunkUnloaded();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------ saving, syncing

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < ConsoleControl.LEVERS; i++) {
            tag.putFloat("Lever" + i, levers[i]);
        }
        tag.putInt("Buttons", buttons);
        for (int slot = 0; slot < frequencies.length; slot++) {
            if (!frequencies[slot].isEmpty()) {
                tag.put(frequencyTag(slot), frequencies[slot].save(registries));
            }
        }
    }

    /** Named by control, so a later change of the layout does not shuffle saved frequencies. */
    private static String frequencyTag(int slot) {
        return "Frequency_" + ConsoleControl.id(slot / 2) + "_" + (slot % 2 + 1);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean rejoin = inNetwork;
        leaveNetwork();
        for (int i = 0; i < ConsoleControl.LEVERS; i++) {
            levers[i] = Mth.clamp(tag.getFloat("Lever" + i), 0.0F, 1.0F);
        }
        // Held buttons only matter while someone holds them; the server starts released.
        buttons = level != null && level.isClientSide ? tag.getInt("Buttons") : 0;
        for (int slot = 0; slot < frequencies.length; slot++) {
            String key = frequencyTag(slot);
            frequencies[slot] = tag.contains(key) ? ItemStack.parseOptional(registries, tag.getCompound(key))
                : ItemStack.EMPTY;
        }
        for (ControlLink link : links) {
            link.updateKey();
        }
        if (rejoin) {
            joinNetwork();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------- one control's link

    /** One control as a transmitter in Create's Redstone Link network. */
    private final class ControlLink implements IRedstoneLinkable {
        private final int control;
        private Couple<Frequency> key = Couple.create(Frequency.EMPTY, Frequency.EMPTY);

        ControlLink(int control) {
            this.control = control;
        }

        void updateKey() {
            key = Couple.create(Frequency.of(frequencies[2 * control]), Frequency.of(frequencies[2 * control + 1]));
        }

        @Override
        public int getTransmittedStrength() {
            return signal(control);
        }

        @Override
        public void setReceivedStrength(int power) {
            // Sends only.
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return inNetwork && !isRemoved();
        }

        @Override
        public Couple<Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    }
}
