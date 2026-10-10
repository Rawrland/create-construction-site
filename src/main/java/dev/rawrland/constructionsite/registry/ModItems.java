package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateConstructionSite.MODID);

    public static final DeferredItem<ExcavatorBucketItem> EXCAVATOR_BUCKET = ITEMS.register(
        "excavator_bucket",
        () -> new ExcavatorBucketItem(ModBlocks.EXCAVATOR_BUCKET.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> BULLDOZER_BLADE = ITEMS.register(
        "bulldozer_blade",
        () -> new BlockItem(ModBlocks.BULLDOZER_BLADE.get(), new Item.Properties())
    );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
