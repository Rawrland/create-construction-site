package dev.rawrland.constructionsite.compat.aeronautics;

import dev.rawrland.constructionsite.content.blade.BulldozerBladeBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Bulldozer Blade's block entity when Create Aeronautics is installed.
 *
 * It only adds what needs Create Aeronautics: Sable calls {@link #sable$tick}
 * once per server tick while the blade is part of a simulated contraption
 * ("sub-level"). All the work is the blade's own, in the parent class.
 */
public class AeroBladeBlockEntity extends BulldozerBladeBlockEntity implements BlockEntitySubLevelActor {

    public AeroBladeBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        if (level instanceof ServerLevel serverLevel && Sable.HELPER.getContaining(this) == subLevel) {
            tickOnSimulatedContraption(serverLevel, new SableToolHost(serverLevel));
        }
    }
}
