package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.content.barrier.TrafficConeItem;
import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketItem;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyItem;
import dev.rawrland.constructionsite.content.cylinder.HydraulicCylinderItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateConstructionSite.MODID);

    public static final PaintedItems<ExcavatorBucketItem> EXCAVATOR_BUCKETS =
        painted(ModBlocks.EXCAVATOR_BUCKETS, ExcavatorBucketItem::new);
    public static final PaintedItems<BlockItem> BULLDOZER_BLADES =
        painted(ModBlocks.BULLDOZER_BLADES, BlockItem::new);
    public static final PaintedItems<DumpBodyItem> DUMP_BODIES =
        painted(ModBlocks.DUMP_BODIES, DumpBodyItem::new);

    /** The bare versions, under the tools' original ids. */
    public static final DeferredItem<ExcavatorBucketItem> EXCAVATOR_BUCKET = EXCAVATOR_BUCKETS.bare();
    public static final DeferredItem<BlockItem> BULLDOZER_BLADE = BULLDOZER_BLADES.bare();
    public static final DeferredItem<DumpBodyItem> DUMP_BODY = DUMP_BODIES.bare();

    // --- metal blocks ------------------------------------------------------------------

    public static final PaintedItems<BlockItem> BRASS_PLATING = painted(ModBlocks.BRASS_PLATING, BlockItem::new);

    public static final DeferredItem<BlockItem> DARK_STEEL_PLATING = ITEMS.registerSimpleBlockItem(ModBlocks.DARK_STEEL_PLATING);
    public static final DeferredItem<BlockItem> POLISHED_IRON_PLATING = ITEMS.registerSimpleBlockItem(ModBlocks.POLISHED_IRON_PLATING);
    public static final PaintedItems<BlockItem> BRASS_PLATING_SLABS = painted(ModBlocks.BRASS_PLATING_SLABS, BlockItem::new);
    public static final PaintedItems<BlockItem> BRASS_PLATING_STAIRS = painted(ModBlocks.BRASS_PLATING_STAIRS, BlockItem::new);
    public static final DeferredItem<BlockItem> DARK_STEEL_PLATING_SLAB = ITEMS.registerSimpleBlockItem(ModBlocks.DARK_STEEL_PLATING_SLAB);
    public static final DeferredItem<BlockItem> DARK_STEEL_PLATING_STAIRS = ITEMS.registerSimpleBlockItem(ModBlocks.DARK_STEEL_PLATING_STAIRS);
    public static final DeferredItem<BlockItem> POLISHED_IRON_PLATING_SLAB = ITEMS.registerSimpleBlockItem(ModBlocks.POLISHED_IRON_PLATING_SLAB);
    public static final DeferredItem<BlockItem> POLISHED_IRON_PLATING_STAIRS = ITEMS.registerSimpleBlockItem(ModBlocks.POLISHED_IRON_PLATING_STAIRS);

    public static final DeferredItem<BlockItem> TREAD_PLATE = ITEMS.registerSimpleBlockItem(ModBlocks.TREAD_PLATE);
    public static final DeferredItem<BlockItem> TREAD_PLATE_SLAB = ITEMS.registerSimpleBlockItem(ModBlocks.TREAD_PLATE_SLAB);
    public static final DeferredItem<BlockItem> TREAD_PLATE_STAIRS = ITEMS.registerSimpleBlockItem(ModBlocks.TREAD_PLATE_STAIRS);
    public static final DeferredItem<BlockItem> HAZARD_STRIPES = ITEMS.registerSimpleBlockItem(ModBlocks.HAZARD_STRIPES);
    public static final DeferredItem<BlockItem> HAZARD_STRIPES_RED_WHITE = ITEMS.registerSimpleBlockItem(ModBlocks.HAZARD_STRIPES_RED_WHITE);
    public static final DeferredItem<BlockItem> HAZARD_PLATE = ITEMS.registerSimpleBlockItem(ModBlocks.HAZARD_PLATE);
    public static final DeferredItem<BlockItem> HAZARD_PLATE_RED_WHITE = ITEMS.registerSimpleBlockItem(ModBlocks.HAZARD_PLATE_RED_WHITE);
    public static final PaintedItems<BlockItem> CORRUGATED_SHEETS = painted(ModBlocks.CORRUGATED_SHEETS, BlockItem::new);
    public static final DeferredItem<BlockItem> GRATING = ITEMS.registerSimpleBlockItem(ModBlocks.GRATING);
    public static final DeferredItem<BlockItem> GRATING_SLAB = ITEMS.registerSimpleBlockItem(ModBlocks.GRATING_SLAB);

    public static final DeferredItem<TrafficConeItem> TRAFFIC_CONE = ITEMS.register("traffic_cone",
        () -> new TrafficConeItem(ModBlocks.TRAFFIC_CONE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> DELINEATOR = ITEMS.registerSimpleBlockItem(ModBlocks.DELINEATOR);
    public static final DeferredItem<BlockItem> BARRIER_BOARD = ITEMS.registerSimpleBlockItem(ModBlocks.BARRIER_BOARD);
    public static final DeferredItem<BlockItem> SITE_FENCE = ITEMS.registerSimpleBlockItem(ModBlocks.SITE_FENCE);
    /** Warning lamps: placed on any block, or used on a traffic cone to clip onto it. */
    public static final DeferredItem<BlockItem> WARNING_FLASHER = ITEMS.registerSimpleBlockItem(ModBlocks.WARNING_FLASHER);
    public static final DeferredItem<BlockItem> WARNING_BEACON = ITEMS.registerSimpleBlockItem(ModBlocks.WARNING_BEACON);

    // Hydraulic cylinder (prototype): the mount, and the cylinder that links two mounts.
    public static final DeferredItem<BlockItem> CYLINDER_MOUNT = ITEMS.registerSimpleBlockItem(ModBlocks.CYLINDER_MOUNT);
    public static final DeferredItem<BlockItem> OPERATOR_CONSOLE = ITEMS.registerSimpleBlockItem(ModBlocks.OPERATOR_CONSOLE);
    public static final DeferredItem<HydraulicCylinderItem> HYDRAULIC_CYLINDER = ITEMS.register("hydraulic_cylinder",
        () -> new HydraulicCylinderItem(new Item.Properties()));

    /** One item per version of a painted block, under the same id. */
    private static <B extends Block, I extends Item> PaintedItems<I> painted(PaintedBlocks<B> blocks,
                                                                           BiFunction<Block, Item.Properties, I> factory) {
        List<DeferredItem<I>> versions = new ArrayList<>();
        for (DeferredBlock<B> block : blocks.all()) {
            versions.add(ITEMS.register(block.getId().getPath(), () -> factory.apply(block.get(), new Item.Properties())));
        }
        return new PaintedItems<>(versions);
    }

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
