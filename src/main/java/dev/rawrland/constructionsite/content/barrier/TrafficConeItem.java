package dev.rawrland.constructionsite.content.barrier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;

/**
 * The traffic cone's item. Sneaking with it on a cone stack still reaches the cone, so the cone can
 * take its top one off (TrafficConeBlock); on anything else sneaking places a cone as usual.
 */
public class TrafficConeItem extends BlockItem {

    public TrafficConeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockState(pos).is(getBlock());
    }
}
