package dev.rawrland.constructionsite.content.tool;

import dev.rawrland.constructionsite.registry.ModItems;
import dev.rawrland.constructionsite.registry.PaintedItems;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LayeredCauldronBlock;

/**
 * Washing paint off in a water cauldron, like leather armour or shulker boxes: a painted
 * item (the whole stack) becomes its bare version and the cauldron loses one level of water.
 * Create's bulk washing (a fan through water) does the same through splashing recipes.
 */
public final class PaintWashing {

    private PaintWashing() {
    }

    /** Called once at common setup, on the main thread. */
    public static void registerCauldron() {
        addAll(ModItems.EXCAVATOR_BUCKETS);
        addAll(ModItems.BULLDOZER_BLADES);
        addAll(ModItems.DUMP_BODIES);
        addAll(ModItems.CORRUGATED_SHEETS);
        addAll(ModItems.BRASS_PLATING);
    }

    private static void addAll(PaintedItems<?> items) {
        Item bare = items.bare().get();
        for (Item item : items.items()) {
            if (item != bare) {
                add(item, bare);
            }
        }
    }

    private static void add(Item painted, Item bare) {
        CauldronInteraction.WATER.map().put(painted, (state, level, pos, player, hand, stack) -> {
            if (!level.isClientSide) {
                player.setItemInHand(hand, stack.transmuteCopy(bare, stack.getCount()));
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 1.2F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        });
    }
}
