package dev.rawrland.constructionsite.content.console;

/**
 * The 13 controls of the Operator Console, in the order of their frequency slots and key
 * bits: the cross (4 buttons), 4 bonus buttons, then 5 levers. Buttons send 15 while held;
 * levers send 0 to 15 and stay where they are left.
 */
public final class ConsoleControl {

    public static final int CROSS = 4;
    public static final int BONUS = 4;
    public static final int BUTTONS = CROSS + BONUS;
    public static final int LEVERS = 5;
    public static final int COUNT = BUTTONS + LEVERS;

    /** Index of the first lever among the controls. */
    public static final int FIRST_LEVER = BUTTONS;

    /** Cross order: forward, left, back, right (W, A, S, D by default). */
    public static final String[] CROSS_NAMES = {"forward", "left", "back", "right"};

    private ConsoleControl() {
    }

    public static boolean isLever(int control) {
        return control >= FIRST_LEVER;
    }

    /** Key bit of a button (0..7). */
    public static int buttonBit(int button) {
        return 1 << button;
    }

    /** Key bit for moving a lever (0..4) forward. */
    public static int leverForwardBit(int lever) {
        return 1 << (BUTTONS + 2 * lever);
    }

    /** Key bit for moving a lever (0..4) back. */
    public static int leverBackBit(int lever) {
        return 1 << (BUTTONS + 2 * lever + 1);
    }

    /** Name used in language keys: cross_forward, button1, lever1 ... */
    public static String id(int control) {
        if (control < CROSS) {
            return "cross_" + CROSS_NAMES[control];
        }
        if (control < BUTTONS) {
            return "button" + (control - CROSS + 1);
        }
        return "lever" + (control - FIRST_LEVER + 1);
    }
}
