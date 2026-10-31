package dev.rawrland.constructionsite.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.console.ConsoleControl;
import dev.rawrland.constructionsite.content.console.OperatorConsoleBlock;
import dev.rawrland.constructionsite.content.console.OperatorConsoleBlockEntity;
import dev.rawrland.constructionsite.network.ConsoleInputPayload;
import dev.rawrland.constructionsite.network.ConsoleScrollPayload;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The player's side of the Operator Console (add-operator-console), modelled on Create's
 * Linked Controller (see docs/notes/create-redstone-link.md).
 *
 * In control: the cross and bonus button keys press the console's buttons while held, the
 * lever key pairs move the levers; the player does not walk, and other uses of those keys
 * (inventory, dropping, chat ...) are held back. The held keys go to the server when they
 * change and every 5 ticks; the server does the rest. Sneak, Escape, any open window or
 * walking out of reach gives control back.
 *
 * Defaults are physical key positions (GLFW key codes are named after the US layout), so on a
 * German keyboard lever 3's default is the key labelled Z. Names shown to the player always
 * come from the bound key, so they match any layout and any rebinding.
 *
 * Scroll: looking at a lever and scrolling moves it one signal step per notch, also when not
 * in control.
 */
public final class OperatorConsoleControls {

    public static final String CATEGORY = "key.categories." + CreateConstructionSite.MODID;

    /** Only "pressed" while the player is in control; never reported as clashing with other keys. */
    private static final IKeyConflictContext WHILE_IN_CONTROL = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return consolePos != null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return false;
        }
    };

    /**
     * One key per bit of the input (see ConsoleControl): 8 buttons (cross forward, left, back,
     * right, bonus 1 to 4), then forward and back for each of the 5 levers.
     */
    public static final KeyMapping[] KEYS = new KeyMapping[ConsoleControl.BUTTONS + ConsoleControl.LEVERS * 2];
    private static final int[] DEFAULTS = {
        GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
        GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_C,
        GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_F,
        GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_G,
        GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_H,
        GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_J,
        GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_K,
    };

    static {
        for (int i = 0; i < KEYS.length; i++) {
            String name;
            if (i < ConsoleControl.BUTTONS) {
                name = ConsoleControl.id(i);
            } else {
                int lever = (i - ConsoleControl.BUTTONS) / 2;
                name = "lever" + (lever + 1) + ((i - ConsoleControl.BUTTONS) % 2 == 0 ? "_forward" : "_back");
            }
            KEYS[i] = new KeyMapping("key." + CreateConstructionSite.MODID + ".console." + name, WHILE_IN_CONTROL,
                InputConstants.Type.KEYSYM, DEFAULTS[i], CATEGORY);
        }
    }

    /** The bound key(s) of a control as shown to the player: "W", or "R/F" for a lever. */
    public static String keyLabel(int control) {
        if (!ConsoleControl.isLever(control)) {
            return KEYS[control].getTranslatedKeyMessage().getString();
        }
        int first = ConsoleControl.BUTTONS + 2 * (control - ConsoleControl.FIRST_LEVER);
        return KEYS[first].getTranslatedKeyMessage().getString() + "/"
            + KEYS[first + 1].getTranslatedKeyMessage().getString();
    }

    private static final int KEEP_ALIVE = 5;

    @Nullable
    private static BlockPos consolePos;
    private static int lastKeys;
    private static int cooldown;

    private OperatorConsoleControls() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : KEYS) {
            event.register(key);
        }
    }

    /** Right-click on a console: take control of it. */
    public static void start(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (consolePos != null) {
            stop();
        }
        consolePos = pos;
        lastKeys = 0;
        cooldown = 0;
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(
                "message.create_construction_site.operator_console.in_control",
                mc.options.keyShift.getTranslatedKeyMessage()), true);
        }
    }

    public static boolean inControl() {
        return consolePos != null;
    }

    /** Gives control back and lets the movement keys work again. */
    public static void stop() {
        if (consolePos == null) {
            return;
        }
        PacketDistributor.sendToServer(new ConsoleInputPayload(consolePos, 0, true));
        consolePos = null;
        lastKeys = 0;
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping movement : movementKeys(mc)) {
            movement.setDown(isActuallyDown(mc, movement));
        }
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(
                "message.create_construction_site.operator_console.left"), true);
        }
    }

    /** Start of every client tick. */
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (consolePos == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.isSpectator()) {
            consolePos = null;
            return;
        }
        boolean stillThere = mc.level.getBlockEntity(consolePos) instanceof OperatorConsoleBlockEntity console
            && console.inReach(mc.player);
        if (!stillThere || mc.screen != null || isActuallyDown(mc, mc.options.keyShift)
            || InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE)) {
            stop();
            return;
        }

        int keys = 0;
        for (int i = 0; i < KEYS.length; i++) {
            if (isActuallyDown(mc, KEYS[i])) {
                keys |= 1 << i;
            }
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (keys != lastKeys || (keys != 0 && cooldown == 0)) {
            PacketDistributor.sendToServer(new ConsoleInputPayload(consolePos, keys, false));
            cooldown = KEEP_ALIVE;
        }
        lastKeys = keys;

        // The player stands still while operating, and the console's keys do nothing else
        // (no inventory on E, no dropping on Q, no chat on T ...).
        for (KeyMapping movement : movementKeys(mc)) {
            movement.setDown(false);
        }
        for (KeyMapping other : mc.options.keyMappings) {
            if (isOurs(other) || other == mc.options.keyShift) {
                continue;
            }
            for (KeyMapping ours : KEYS) {
                if (other.getKey().equals(ours.getKey())) {
                    while (other.consumeClick()) {
                        // Swallowed.
                    }
                    other.setDown(false);
                    break;
                }
            }
        }
    }

    /** Scrolling while looking at a console's lever. */
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null
            || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockEntity(pos) instanceof OperatorConsoleBlockEntity console)) {
            return;
        }
        int lever = OperatorConsoleControls.leverAt(console, hit.getLocation());
        int steps = (int) Math.signum(event.getScrollDeltaY());
        if (lever < 0 || steps == 0) {
            return;
        }
        PacketDistributor.sendToServer(new ConsoleScrollPayload(pos, lever, steps));
        event.setCanceled(true);
    }

    /**
     * Which lever a point on the console is at: 0 at the operator's left to 4 at the right, or
     * -1 if it is not over the lever row (the right half of the desk). The point may come in world coordinates for a console
     * on a contraption; then it is taken into the console's own frame first.
     */
    static int leverAt(OperatorConsoleBlockEntity console, Vec3 point) {
        BlockPos pos = console.getBlockPos();
        Vec3 local = point.subtract(Vec3.atLowerCornerOf(pos));
        if (!inside(local)) {
            ClientSubLevelAccess contraption = SableCompanion.INSTANCE.getContainingClient(console);
            if (contraption == null) {
                return -1;
            }
            local = contraption.renderPose(1.0F).transformPositionInverse(point).subtract(Vec3.atLowerCornerOf(pos));
            if (!inside(local)) {
                return -1;
            }
        }
        // Across the console in its own frame (pixels): x to the operator's right.
        Direction facing = console.getBlockState().getValue(OperatorConsoleBlock.FACING);
        Direction right = facing.getCounterClockWise();
        double across = right.getAxis().choose(local.x, local.y, local.z);
        double x = 16.0 * (right.getAxisDirection() == Direction.AxisDirection.POSITIVE ? across : 1.0 - across);
        int lever = (int) Math.floor((x - OperatorConsoleRenderer.LEVER_ROW_LEFT) / OperatorConsoleRenderer.LEVER_STEP);
        return lever < 0 || lever >= ConsoleControl.LEVERS ? -1 : lever;
    }

    private static boolean inside(Vec3 local) {
        double e = 0.02;
        return local.x > -e && local.x < 1 + e && local.y > -e && local.y < 1 + e && local.z > -e && local.z < 1 + e;
    }

    private static boolean isOurs(KeyMapping mapping) {
        for (KeyMapping ours : KEYS) {
            if (ours == mapping) {
                return true;
            }
        }
        return false;
    }

    private static KeyMapping[] movementKeys(Minecraft mc) {
        return new KeyMapping[] {mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight,
            mc.options.keyJump};
    }

    /** Whether the key bound to this mapping is held right now, read from the keyboard itself. */
    private static boolean isActuallyDown(Minecraft mc, KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key == InputConstants.UNKNOWN) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }
}
