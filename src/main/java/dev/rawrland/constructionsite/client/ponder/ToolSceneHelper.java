package dev.rawrland.constructionsite.client.ponder;

import dev.rawrland.constructionsite.client.BulldozerBladeRenderer;
import dev.rawrland.constructionsite.content.blade.BulldozerBladeBlockEntity;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlockEntity;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Changes the tools' data inside a Ponder scene.
 *
 * A scene is scripted: the tools' own logic does not run in it (no contraption,
 * no server, and a Ponder world does not call a block's placement code). So the
 * scenes set the result directly, in the same data a saved world keeps. The
 * tools' renderers then draw it exactly as in the world.
 *
 * The field names are the ones the block entities write (see their write methods).
 */
final class ToolSceneHelper {

    private ToolSceneHelper() {
    }

    /** Joins the bucket blocks in the selection into one bucket with the given anchor and size. */
    static void joinBuckets(SceneBuilder scene, Selection blocks, BlockPos anchor, int height, int width) {
        scene.world().modifyBlockEntityNBT(blocks, ExcavatorBucketBlockEntity.class,
            tag -> setGroup(tag, anchor, height, width));
    }

    /** Makes each bucket block in the selection a single bucket again. */
    static void separateBuckets(SceneBuilder scene, Selection blocks) {
        scene.world().modifyBlockEntityNBT(blocks, ExcavatorBucketBlockEntity.class, ToolSceneHelper::setSingle);
    }

    /** Sets the bucket mount of every block in the selection: 0 top, 1 back, 2 none. */
    static void setBucketMount(SceneBuilder scene, Selection blocks, int mount) {
        scene.world().modifyBlockEntityNBT(blocks, ExcavatorBucketBlockEntity.class, tag -> tag.putInt("Mount", mount));
    }

    /**
     * Fills the bucket blocks in the selection with dirt, one block each. pickedUpAt orders them:
     * a bucket empties the most recently dug ones first.
     */
    static void fillBuckets(SceneBuilder scene, Selection blocks, long pickedUpAt) {
        scene.world().modifyBlockEntityNBT(blocks, ExcavatorBucketBlockEntity.class, tag -> {
            tag.putBoolean("HasContent", true);
            tag.put("StoredBlock", NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()));
            tag.putLong("PickedUpAt", pickedUpAt);
        });
    }

    /** Empties the bucket blocks in the selection. */
    static void emptyBuckets(SceneBuilder scene, Selection blocks) {
        scene.world().modifyBlockEntityNBT(blocks, ExcavatorBucketBlockEntity.class, tag -> {
            tag.putBoolean("HasContent", false);
            tag.remove("StoredBlock");
            tag.remove("StoredBlockEntity");
            tag.remove("PickedUpAt");
        });
    }

    /** Joins the blade blocks in the selection into one blade with the given anchor and size. */
    static void joinBlades(SceneBuilder scene, Selection blocks, BlockPos anchor, int height, int width) {
        scene.world().modifyBlockEntityNBT(blocks, BulldozerBladeBlockEntity.class,
            tag -> setGroup(tag, anchor, height, width));
    }

    /** Sets the blade mount of every block in the selection: 0 middle, 1 ends, 2 none. */
    static void setBladeMount(SceneBuilder scene, Selection blocks, int mount) {
        scene.world().modifyBlockEntityNBT(blocks, BulldozerBladeBlockEntity.class, tag -> tag.putInt("Mount", mount));
    }

    /**
     * Adds a dirt block to the pile stored in one blade block. It is drawn sliding in
     * from where it came from: "fromUp" and "fromForward" are that place measured from
     * its place in the pile, in blocks, along the blade's up and forward directions.
     */
    static void addDirtToPile(SceneBuilder scene, BlockPos bladeBlock, float fromUp, float fromForward) {
        scene.world().modifyBlockEntity(bladeBlock, BulldozerBladeBlockEntity.class, blade -> {
            Level level = blade.getLevel();
            if (level == null) {
                return;
            }
            CompoundTag tag = blade.saveWithFullMetadata(level.registryAccess());
            ListTag stored = tag.getList("Stored", Tag.TAG_COMPOUND);
            CompoundTag entry = new CompoundTag();
            entry.put("Block", NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()));
            entry.putFloat("FromRight", 0.0F);
            entry.putFloat("FromUp", fromUp);
            entry.putFloat("FromForward", fromForward);
            // Now, in Ponder's own ticks (the game is paused while a scene plays), so the renderer plays the slide-in.
            entry.putLong("TakenAt", BulldozerBladeRenderer.clock(level));
            stored.add(entry);
            tag.put("Stored", stored);
            blade.loadWithComponents(tag, level.registryAccess());
        });
    }

    /** Removes the newest block of the pile stored in one blade block. */
    static void removeNewestFromPile(SceneBuilder scene, BlockPos bladeBlock) {
        scene.world().modifyBlockEntity(bladeBlock, BulldozerBladeBlockEntity.class, blade -> {
            Level level = blade.getLevel();
            if (level == null) {
                return;
            }
            CompoundTag tag = blade.saveWithFullMetadata(level.registryAccess());
            ListTag stored = tag.getList("Stored", Tag.TAG_COMPOUND);
            if (!stored.isEmpty()) {
                stored.remove(stored.size() - 1);
            }
            tag.put("Stored", stored);
            blade.loadWithComponents(tag, level.registryAccess());
        });
    }

    /** Empties the piles stored in the blade blocks of the selection. */
    static void clearPile(SceneBuilder scene, Selection bladeBlocks) {
        scene.world().modifyBlockEntityNBT(bladeBlocks, BulldozerBladeBlockEntity.class,
            tag -> tag.put("Stored", new ListTag()));
    }

    private static void setGroup(CompoundTag tag, BlockPos anchor, int height, int width) {
        // A block entity's saved data holds its own position as x, y and z.
        tag.putInt("AnchorX", anchor.getX() - tag.getInt("x"));
        tag.putInt("AnchorY", anchor.getY() - tag.getInt("y"));
        tag.putInt("AnchorZ", anchor.getZ() - tag.getInt("z"));
        tag.putInt("GroupHeight", height);
        tag.putInt("GroupWidth", width);
    }

    private static void setSingle(CompoundTag tag) {
        tag.putInt("AnchorX", 0);
        tag.putInt("AnchorY", 0);
        tag.putInt("AnchorZ", 0);
        tag.putInt("GroupHeight", 1);
        tag.putInt("GroupWidth", 1);
    }
}
