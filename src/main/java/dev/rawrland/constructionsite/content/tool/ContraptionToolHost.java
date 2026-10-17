package dev.rawrland.constructionsite.content.tool;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.MutablePair;

import javax.annotation.Nullable;

/**
 * A tool on one of Create's own contraptions: a bearing, a piston, a gantry,
 * a minecart or a train.
 *
 * While such a contraption moves, its blocks are not in the world. Each block
 * keeps its position in the contraption's own grid ("local" position), and the
 * contraption entity knows where that grid is and how it is turned.
 *
 * The tool's blocks have no block entities there. Each of them gets a stand-in
 * copy (see {@link ContraptionToolShadow}), which this host hands out.
 *
 * @param contraption the contraption the tool is on
 */
public record ContraptionToolHost(Contraption contraption) implements ToolHost {

    @Override
    public Vec3 toWorld(Vec3 localPoint) {
        // 1 = where the contraption is now, at the end of this tick.
        return contraption.entity.toGlobalVector(localPoint, 1);
    }

    @Nullable
    @Override
    public BlockEntity blockEntityAt(BlockPos localPos) {
        // The blocks of a tool are all actors, because the tools have a movement behaviour.
        MutablePair<StructureBlockInfo, MovementContext> actor = contraption.getActorAt(localPos);
        if (actor == null || actor.right == null) {
            return null;
        }
        ContraptionToolShadow shadow = ContraptionToolShadow.of(actor.right);
        return shadow == null ? null : shadow.blockEntity();
    }
}
