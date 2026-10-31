package dev.rawrland.constructionsite.content.barrier;

import net.minecraft.util.StringRepresentable;

/**
 * The lamp on top of a traffic cone (owner's idea, 29 October 2026): none, the flasher (a small
 * round lens, like the warning lights clipped onto real cones) or the beacon (a small amber dome
 * that lights all round). Both blink amber and give light.
 */
public enum ConeLamp implements StringRepresentable {
    NONE("none"), FLASHER("flasher"), BEACON("beacon");

    private final String name;

    ConeLamp(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
