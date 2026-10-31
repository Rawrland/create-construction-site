package dev.rawrland.constructionsite.client;

import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.registry.ModBlocks;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.item.DyeColor;
import dev.rawrland.constructionsite.content.tool.ToolPaint;

/**
 * Create's connected textures for Brass Plating (bare and painted), Dark Steel Plating and Polished Iron Plating, the way Create's casings get them:
 * blocks of the same colour side by side share one frame around the whole surface. Their slabs and
 * stairs share it too (add-metal-slabs-and-stairs): MetalCTModel works the frame out per half block.
 * The sheets "painted_steel_&lt;colour&gt;_connected" are made by tools/textures/generate_textures.py.
 */
public final class MetalBlockTextures {

    private MetalBlockTextures() {
    }

    /** Called from the client entry point, before models are baked. */
    public static void register() {
        for (int i = 0; i < ToolPaint.VERSIONS.size(); i++) {
            DyeColor colour = ToolPaint.VERSIONS.get(i);
            connect(brassPlating(colour), ModBlocks.BRASS_PLATING.all().get(i),
                ModBlocks.BRASS_PLATING_SLABS.all().get(i), ModBlocks.BRASS_PLATING_STAIRS.all().get(i));
        }
        connect(DARK_STEEL, ModBlocks.DARK_STEEL_PLATING, ModBlocks.DARK_STEEL_PLATING_SLAB,
            ModBlocks.DARK_STEEL_PLATING_STAIRS);
        connect(POLISHED_IRON, ModBlocks.POLISHED_IRON_PLATING, ModBlocks.POLISHED_IRON_PLATING_SLAB,
            ModBlocks.POLISHED_IRON_PLATING_STAIRS);
    }

    /** Block id to the plating it belongs to (its texture name): a plating and its slab and stairs. */
    private static final Map<ResourceLocation, String> FAMILY = new HashMap<>();

    private static Object family(BlockState state) {
        return FAMILY.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /** The texture of Dark Steel Plating (in textures/block). The tools use it too. */
    public static final String DARK_STEEL = "dark_steel";
    /** The texture of Polished Iron Plating. The tools use it too. */
    public static final String POLISHED_IRON = "polished_iron";

    /** The texture of Brass Plating, bare (null) or in a colour. The tools' bodies use it too. */
    public static String brassPlating(DyeColor colour) {
        return colour == null ? "brass" : "painted_steel_" + colour.getSerializedName();
    }

    @SafeVarargs
    private static void connect(String name, DeferredBlock<? extends net.minecraft.world.level.block.Block>... blocks) {
        CTSpriteShiftEntry shift = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            id("block/" + name), id("block/" + name + "_connected"));
        for (DeferredBlock<?> block : blocks) {
            FAMILY.put(block.getId(), name);
            CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                .register(block.getId(), model -> new MetalCTModel(model, shift, MetalBlockTextures::family));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, path);
    }
}
