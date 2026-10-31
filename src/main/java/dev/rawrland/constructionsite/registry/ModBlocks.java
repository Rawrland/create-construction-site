package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.compat.Mods;
import dev.rawrland.constructionsite.compat.aeronautics.AeroBucketBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import dev.rawrland.constructionsite.content.barrier.BarrierBoardBlock;
import dev.rawrland.constructionsite.content.barrier.ConeLamp;
import dev.rawrland.constructionsite.content.barrier.DelineatorBlock;
import dev.rawrland.constructionsite.content.barrier.SiteFenceBlock;
import dev.rawrland.constructionsite.content.barrier.TrafficConeBlock;
import dev.rawrland.constructionsite.content.barrier.WarningLampBlock;
import dev.rawrland.constructionsite.content.decoration.GratingBlock;
import dev.rawrland.constructionsite.content.cylinder.CylinderMountBlock;
import dev.rawrland.constructionsite.content.decoration.HazardPlateBlock;
import dev.rawrland.constructionsite.content.decoration.HazardStripesBlock;
import dev.rawrland.constructionsite.content.tool.ToolPaint;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.blade.BulldozerBladeBlock;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlock;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateConstructionSite.MODID);

    /**
     * With Create Aeronautics installed the bucket is its subclass with ropes
     * (see compat.aeronautics). Chosen once here; the id is the same either way,
     * so worlds open with or without Create Aeronautics.
     */
    private static final Function<BlockBehaviour.Properties, ExcavatorBucketBlock> BUCKET_FACTORY =
        Mods.AERONAUTICS ? AeroBucketBlock::new : ExcavatorBucketBlock::new;

    /** Every tool comes bare and in the 16 dye colours, each its own block (see ToolPaint). */
    public static final PaintedBlocks<ExcavatorBucketBlock> EXCAVATOR_BUCKETS = painted(
        "excavator_bucket",
        BUCKET_FACTORY,
        // The model is hollow, so neighbouring block faces must stay visible.
        properties -> properties.strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
    );

    public static final PaintedBlocks<BulldozerBladeBlock> BULLDOZER_BLADES = painted(
        "bulldozer_blade",
        BulldozerBladeBlock::new,
        // The blade is drawn thinner than the block, so neighbouring block faces must stay visible.
        properties -> properties.strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
    );

    public static final PaintedBlocks<DumpBodyBlock> DUMP_BODIES = painted(
        "dump_body",
        DumpBodyBlock::new,
        // The body is drawn open at the top with thin walls, so neighbouring block faces must stay visible.
        properties -> properties.strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
    );

    /** The bare versions, under the tools' original ids. */
    public static final DeferredBlock<ExcavatorBucketBlock> EXCAVATOR_BUCKET = EXCAVATOR_BUCKETS.bare();
    public static final DeferredBlock<BulldozerBladeBlock> BULLDOZER_BLADE = BULLDOZER_BLADES.bare();
    public static final DeferredBlock<DumpBodyBlock> DUMP_BODY = DUMP_BODIES.bare();

    // --- metal blocks ------------------------------------------------------------------

    /**
     * Brass Plating: a smooth brass block, bare ("brass_plating") and in the 16 colours
     * ("red_brass_plating" and so on). The same textures as the tools, so a bare tool looks like
     * Brass Plating and a red tool like Red Brass Plating.
     */
    public static final PaintedBlocks<Block> BRASS_PLATING = painted(
        "brass_plating", Block::new, properties -> metal(properties)
    );

    /**
     * Dark Steel Plating and Polished Iron Plating: the two other metals of the tools, as blocks.
     * Dark steel is the tools' frames, pins and hinges; polished iron the bare tools' teeth and
     * cutting edges. The same textures as on the tools, with connected textures like Brass Plating.
     */
    public static final DeferredBlock<Block> DARK_STEEL_PLATING = BLOCKS.registerBlock(
        "dark_steel_plating", Block::new, metal(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)));
    public static final DeferredBlock<Block> POLISHED_IRON_PLATING = BLOCKS.registerBlock(
        "polished_iron_plating", Block::new, metal(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));

    /**
     * Slabs and stairs of the three platings (add-metal-slabs-and-stairs), each in the colour of
     * its block, with the same connected textures: a slab or stair joins its block and the other
     * slabs and stairs of the same colour into one surface (client.MetalCTModel).
     */
    public static final PaintedBlocks<SlabBlock> BRASS_PLATING_SLABS = painted(
        "brass_plating_slab", SlabBlock::new, properties -> metal(properties));
    public static final PaintedBlocks<StairBlock> BRASS_PLATING_STAIRS = paintedStairs(
        "brass_plating_stairs", BRASS_PLATING);
    public static final DeferredBlock<SlabBlock> DARK_STEEL_PLATING_SLAB = BLOCKS.registerBlock(
        "dark_steel_plating_slab", SlabBlock::new, metal(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)));
    public static final DeferredBlock<StairBlock> DARK_STEEL_PLATING_STAIRS = BLOCKS.registerBlock(
        "dark_steel_plating_stairs", properties -> new StairBlock(DARK_STEEL_PLATING.get().defaultBlockState(), properties),
        metal(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)));
    public static final DeferredBlock<SlabBlock> POLISHED_IRON_PLATING_SLAB = BLOCKS.registerBlock(
        "polished_iron_plating_slab", SlabBlock::new, metal(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));
    public static final DeferredBlock<StairBlock> POLISHED_IRON_PLATING_STAIRS = BLOCKS.registerBlock(
        "polished_iron_plating_stairs", properties -> new StairBlock(POLISHED_IRON_PLATING.get().defaultBlockState(), properties),
        metal(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));

    public static final DeferredBlock<Block> TREAD_PLATE = BLOCKS.registerBlock(
        "tread_plate", Block::new, metal(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));
    public static final DeferredBlock<SlabBlock> TREAD_PLATE_SLAB = BLOCKS.registerBlock(
        "tread_plate_slab", SlabBlock::new, metal(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));
    public static final DeferredBlock<StairBlock> TREAD_PLATE_STAIRS = BLOCKS.registerBlock(
        "tread_plate_stairs", properties -> new StairBlock(TREAD_PLATE.get().defaultBlockState(), properties),
        metal(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));

    public static final DeferredBlock<HazardStripesBlock> HAZARD_STRIPES = BLOCKS.registerBlock(
        "hazard_stripes", HazardStripesBlock::new, metal(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW)));
    public static final DeferredBlock<HazardStripesBlock> HAZARD_STRIPES_RED_WHITE = BLOCKS.registerBlock(
        "hazard_stripes_red_white", HazardStripesBlock::new, metal(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED)));
    public static final DeferredBlock<HazardPlateBlock> HAZARD_PLATE = BLOCKS.registerBlock(
        "hazard_plate", HazardPlateBlock::new, plate(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<HazardPlateBlock> HAZARD_PLATE_RED_WHITE = BLOCKS.registerBlock(
        "hazard_plate_red_white", HazardPlateBlock::new, plate(MapColor.COLOR_RED));

    /** Corrugated Sheet, bare and in the 16 colours. Its ribs follow the axis it was placed along. */
    public static final PaintedBlocks<RotatedPillarBlock> CORRUGATED_SHEETS = painted(
        "corrugated_sheet", RotatedPillarBlock::new, properties -> metal(properties)
    );

    // See-through: the faces behind it must stay visible.
    public static final DeferredBlock<GratingBlock> GRATING = BLOCKS.registerBlock(
        "grating", GratingBlock::new, metal(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)).noOcclusion());
    public static final DeferredBlock<SlabBlock> GRATING_SLAB = BLOCKS.registerBlock(
        "grating_slab", SlabBlock::new, metal(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)).noOcclusion());

    // --- site barriers (add-site-barriers) ---------------------------------------------
    // Plastic parts: quick to break by hand, and see-through around their small shapes.
    public static final DeferredBlock<TrafficConeBlock> TRAFFIC_CONE = BLOCKS.registerBlock(
        "traffic_cone", TrafficConeBlock::new, plastic(MapColor.COLOR_ORANGE).lightLevel(
            state -> state.getValue(TrafficConeBlock.LAMP) == ConeLamp.NONE
                ? 0 : TrafficConeBlock.LAMP_LIGHT));
    public static final DeferredBlock<DelineatorBlock> DELINEATOR = BLOCKS.registerBlock(
        "delineator", DelineatorBlock::new, plastic(MapColor.COLOR_RED));
    public static final DeferredBlock<BarrierBoardBlock> BARRIER_BOARD = BLOCKS.registerBlock(
        "barrier_board", BarrierBoardBlock::new, plastic(MapColor.COLOR_RED));
    // Warning lamps on their own: on vehicles, walls and ceilings (on a cone they clip onto it).
    public static final DeferredBlock<WarningLampBlock> WARNING_FLASHER = BLOCKS.registerBlock(
        "warning_flasher", p -> new WarningLampBlock(ConeLamp.FLASHER, p), lamp());
    public static final DeferredBlock<WarningLampBlock> WARNING_BEACON = BLOCKS.registerBlock(
        "warning_beacon", p -> new WarningLampBlock(ConeLamp.BEACON, p), lamp());
    public static final DeferredBlock<SiteFenceBlock> SITE_FENCE = BLOCKS.registerBlock(
        "site_fence", SiteFenceBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.CHAIN).noOcclusion());

    // --- hydraulic cylinder (add-hydraulic-cylinder, prototype) ---------------------------
    // Quick to break by hand for now; mining tool and recipe come with the real feature.
    public static final DeferredBlock<CylinderMountBlock> CYLINDER_MOUNT = BLOCKS.registerBlock(
        "cylinder_mount", CylinderMountBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion());

    private static BlockBehaviour.Properties lamp() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.3F).sound(SoundType.LANTERN)
            .noOcclusion().lightLevel(state -> WarningLampBlock.LIGHT);
    }

    private static BlockBehaviour.Properties plastic(MapColor map) {
        // Stone sounds: wool sounded too strange for plastic (owner, 29 October 2026).
        return BlockBehaviour.Properties.of().mapColor(map).strength(0.5F).sound(SoundType.STONE).noOcclusion();
    }

    /** Registers one block in every version: bare (map colour of metal) and each dye colour. */
    private static <T extends Block> PaintedBlocks<T> painted(String name, Function<BlockBehaviour.Properties, T> factory,
                                                              UnaryOperator<BlockBehaviour.Properties> properties) {
        List<DeferredBlock<T>> versions = new ArrayList<>();
        for (DyeColor colour : ToolPaint.VERSIONS) {
            MapColor map = colour == null ? MapColor.METAL : colour.getMapColor();
            versions.add(BLOCKS.registerBlock(ToolPaint.id(colour, name), factory,
                properties.apply(BlockBehaviour.Properties.of().mapColor(map))));
        }
        return new PaintedBlocks<>(versions);
    }

    /** Stairs in every version, each standing on the block of its own colour. */
    private static PaintedBlocks<StairBlock> paintedStairs(String name, PaintedBlocks<? extends Block> blocks) {
        List<DeferredBlock<StairBlock>> versions = new ArrayList<>();
        for (DyeColor colour : ToolPaint.VERSIONS) {
            MapColor map = colour == null ? MapColor.METAL : colour.getMapColor();
            DeferredBlock<? extends Block> base = blocks.get(colour);
            versions.add(BLOCKS.registerBlock(ToolPaint.id(colour, name),
                properties -> new StairBlock(base.get().defaultBlockState(), properties),
                metal(BlockBehaviour.Properties.of().mapColor(map))));
        }
        return new PaintedBlocks<>(versions);
    }

    /** Like Create's metal blocks: mined with a pickaxe, as hard as iron blocks are to break by hand. */
    private static BlockBehaviour.Properties metal(BlockBehaviour.Properties properties) {
        return properties.strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    /** A thin plate: quick to break, and it lets light through. */
    private static BlockBehaviour.Properties plate(MapColor map) {
        return BlockBehaviour.Properties.of().mapColor(map).strength(1.0F, 3.0F).sound(SoundType.METAL)
            .noOcclusion().requiresCorrectToolForDrops().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
    }

    private ModBlocks() {
    }

    /** Operator Console: five throttle levers on Redstone Link frequencies (add-operator-console). */
    public static final DeferredBlock<dev.rawrland.constructionsite.content.console.OperatorConsoleBlock> OPERATOR_CONSOLE =
        BLOCKS.registerBlock("operator_console", dev.rawrland.constructionsite.content.console.OperatorConsoleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F, 6.0F).sound(SoundType.METAL)
                .noOcclusion());

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
