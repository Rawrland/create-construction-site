package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.content.tool.ToolPaint;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/** The items of a {@link PaintedBlocks}, in the same order. */
public final class PaintedItems<T extends Item> {

    private final List<DeferredItem<T>> versions;

    PaintedItems(List<DeferredItem<T>> versions) {
        this.versions = List.copyOf(versions);
    }

    public DeferredItem<T> bare() {
        return versions.get(0);
    }

    public DeferredItem<T> get(@Nullable DyeColor colour) {
        return versions.get(ToolPaint.VERSIONS.indexOf(colour));
    }

    public List<DeferredItem<T>> all() {
        return versions;
    }

    /** The registered items. Only call once registration is done. */
    public Item[] items() {
        return versions.stream().map(DeferredItem::get).toArray(Item[]::new);
    }
}
