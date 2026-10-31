package dev.rawrland.constructionsite.content.tool;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;

/**
 * The paint of the mod's tools and metal blocks: bare metal, or one of the 16 dye colours.
 * Each version is its own block, like Create's seats: the bare one keeps the plain id
 * ("excavator_bucket"), a painted one has the colour in front ("yellow_excavator_bucket").
 */
public final class ToolPaint {

    /** Every version, in the order of the creative tab: bare (null) first, then the colours like vanilla's wool. */
    public static final List<DyeColor> VERSIONS = Collections.unmodifiableList(Arrays.asList(
        null,
        DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK,
        DyeColor.BROWN, DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW,
        DyeColor.LIME, DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE,
        DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK
    ));

    private static final Map<Block, Optional<DyeColor>> COLOURS = new ConcurrentHashMap<>();

    private ToolPaint() {
    }

    /** The id of one version: the colour in front, or the plain name for bare metal. */
    public static String id(@Nullable DyeColor colour, String name) {
        return colour == null ? name : colour.getSerializedName() + "_" + name;
    }

    /** The colour of a block of this mod, read from its id. Null for bare metal. */
    @Nullable
    public static DyeColor of(Block block) {
        return COLOURS.computeIfAbsent(block, b -> {
            String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
            DyeColor found = null;
            for (DyeColor colour : DyeColor.values()) {
                // "light_gray_" is longer than "gray_", so the longest match wins.
                String prefix = colour.getSerializedName() + "_";
                if (path.startsWith(prefix) && (found == null || prefix.length() > found.getSerializedName().length() + 1)) {
                    found = colour;
                }
            }
            return Optional.ofNullable(found);
        }).orElse(null);
    }
}
