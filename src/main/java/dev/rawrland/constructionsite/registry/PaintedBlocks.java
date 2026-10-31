package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.content.tool.ToolPaint;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

/** One block in every paint version (see {@link ToolPaint}), in the order of {@link ToolPaint#VERSIONS}. */
public final class PaintedBlocks<T extends Block> {

    private final List<DeferredBlock<T>> versions;

    PaintedBlocks(List<DeferredBlock<T>> versions) {
        this.versions = List.copyOf(versions);
    }

    public DeferredBlock<T> bare() {
        return versions.get(0);
    }

    public DeferredBlock<T> get(@Nullable DyeColor colour) {
        return versions.get(ToolPaint.VERSIONS.indexOf(colour));
    }

    public List<DeferredBlock<T>> all() {
        return versions;
    }

    /** The registered blocks. Only call once registration is done. */
    public Block[] blocks() {
        return versions.stream().map(DeferredBlock::get).toArray(Block[]::new);
    }
}
