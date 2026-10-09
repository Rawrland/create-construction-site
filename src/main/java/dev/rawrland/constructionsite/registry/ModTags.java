package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.CreateConstructionSite;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {

    /**
     * Blocks an excavator bucket may dig. The content is defined in
     * data/create_construction_site/tags/block/bucket_diggable.json,
     * so data packs can change it.
     */
    public static final TagKey<Block> BUCKET_DIGGABLE = TagKey.create(
        Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, "bucket_diggable")
    );

    private ModTags() {
    }
}
