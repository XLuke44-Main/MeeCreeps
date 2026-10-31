package mcjty.meecreeps.api;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public interface IActionFactory {

    boolean isPossible(World world, BlockPos pos, EnumFacing side);

    boolean isPossibleSecondary(World world, BlockPos pos, EnumFacing side);

    @Nullable
    default String getFurtherQuestionHeading(World world, BlockPos pos, EnumFacing side) { return null; }

    @Nonnull
    default List<Pair<String, String>> getFurtherQuestions(World world, BlockPos pos, EnumFacing side) { return Collections.emptyList(); }

    IActionWorker createWorker(@Nonnull IWorkerHelper helper);
}
