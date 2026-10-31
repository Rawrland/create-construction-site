package dev.rawrland.constructionsite.content.barrier;

import javax.annotation.Nullable;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

/** The privacy tarp on a site fence: none, or a carpet of one of the 16 dye colours. */
public enum FenceTarp implements StringRepresentable {
    NONE(null),
    WHITE(DyeColor.WHITE), ORANGE(DyeColor.ORANGE), MAGENTA(DyeColor.MAGENTA), LIGHT_BLUE(DyeColor.LIGHT_BLUE),
    YELLOW(DyeColor.YELLOW), LIME(DyeColor.LIME), PINK(DyeColor.PINK), GRAY(DyeColor.GRAY),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY), CYAN(DyeColor.CYAN), PURPLE(DyeColor.PURPLE), BLUE(DyeColor.BLUE),
    BROWN(DyeColor.BROWN), GREEN(DyeColor.GREEN), RED(DyeColor.RED), BLACK(DyeColor.BLACK);

    @Nullable
    private final DyeColor colour;

    FenceTarp(@Nullable DyeColor colour) {
        this.colour = colour;
    }

    @Nullable
    public DyeColor colour() {
        return colour;
    }

    public static FenceTarp of(DyeColor colour) {
        for (FenceTarp tarp : values()) {
            if (tarp.colour == colour) {
                return tarp;
            }
        }
        return NONE;
    }

    @Override
    public String getSerializedName() {
        return colour == null ? "none" : colour.getSerializedName();
    }
}
