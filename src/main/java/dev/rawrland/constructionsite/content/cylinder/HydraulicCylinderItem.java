package dev.rawrland.constructionsite.content.cylinder;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Hydraulic Cylinder (prototype). Right-click a Cylinder Mount, then a second one:
 * the cylinder is shown between them, its barrel at the first.
 * Sneak and right-click into the air forgets the first mount.
 *
 * Works like Create Aeronautics' Rope Coupling: the first end is kept on the item.
 */
public class HydraulicCylinderItem extends Item {

    private static final String FIRST_END = "FirstCylinderEnd";

    public HydraulicCylinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof CylinderMountBlockEntity clicked)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (clicked.isLinked()) {
            // Settings are in the window now (empty hand on a linked mount).
            CylinderMountBlock.message(player, "message.create_construction_site.hydraulic_cylinder.taken");
            return InteractionResult.SUCCESS;
        }
        BlockPos first = firstEnd(stack);
        if (first == null) {
            setFirstEnd(stack, pos);
            CylinderMountBlock.message(player, "message.create_construction_site.hydraulic_cylinder.first");
            return InteractionResult.SUCCESS;
        }
        if (first.equals(pos)) {
            setFirstEnd(stack, null);
            CylinderMountBlock.message(player, "message.create_construction_site.hydraulic_cylinder.cleared");
            return InteractionResult.SUCCESS;
        }
        // The first mount may have been broken, or moved by a contraption assembly since.
        if (!(level.getBlockEntity(first) instanceof CylinderMountBlockEntity barrel) || barrel.isLinked()) {
            setFirstEnd(stack, null);
            CylinderMountBlock.message(player, "message.create_construction_site.hydraulic_cylinder.first_gone");
            return InteractionResult.SUCCESS;
        }
        double length = CylinderMountBlockEntity.distance(level, first, pos);
        if (length < CylinderMountBlockEntity.MIN_LENGTH) {
            CylinderMountBlock.message(player, "message.create_construction_site.hydraulic_cylinder.too_short");
            return InteractionResult.SUCCESS;
        }
        if (length > CylinderMountBlockEntity.MAX_LENGTH) {
            CylinderMountBlock.message(player, "message.create_construction_site.hydraulic_cylinder.too_long");
            return InteractionResult.SUCCESS;
        }

        UUID link = UUID.randomUUID();
        float tube = CylinderMountBlockEntity.defaultTube(length);
        barrel.link(link, true, tube);
        clicked.link(link, false, tube);
        setFirstEnd(stack, null);
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                "message.create_construction_site.hydraulic_cylinder.linked", String.format("%.1f", length)), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && firstEnd(stack) != null) {
            if (!level.isClientSide) {
                setFirstEnd(stack, null);
                CylinderMountBlock.message(player, "message.create_construction_site.hydraulic_cylinder.cleared");
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    /** Glints while it remembers a first mount. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return firstEnd(stack) != null || super.isFoil(stack);
    }

    @Nullable
    private static BlockPos firstEnd(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(FIRST_END) ? BlockPos.of(tag.getLong(FIRST_END)) : null;
    }

    private static void setFirstEnd(ItemStack stack, @Nullable BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (pos == null) {
                tag.remove(FIRST_END);
            } else {
                tag.putLong(FIRST_END, pos.asLong());
            }
        });
    }
}
