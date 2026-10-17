package dev.rawrland.constructionsite.content.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Sounds the mod's tools make while they work.
 *
 * The metallic sound is an existing Minecraft sound, played quietly and low,
 * on top of the sound of the material itself. To try another sound, change
 * {@link #METAL} (and maybe the volume and pitch); nothing else needs to change.
 */
public final class ToolSounds {

    /** The steel of a bucket or blade scraping through material. */
    private static final SoundEvent METAL = SoundEvents.NETHERITE_BLOCK_HIT;
    private static final float METAL_VOLUME = 0.35F;
    private static final float METAL_PITCH = 0.6F;

    private ToolSounds() {
    }

    /** A short, quiet metallic sound where a tool just took a block. */
    public static void playMetalInMaterial(ServerLevel level, BlockPos pos) {
        // A little variation in pitch, so many blocks in a row do not sound like one machine gun.
        float pitch = METAL_PITCH + (level.random.nextFloat() - 0.5F) * 0.1F;
        level.playSound(null, pos, METAL, SoundSource.BLOCKS, METAL_VOLUME, pitch);
    }
}
