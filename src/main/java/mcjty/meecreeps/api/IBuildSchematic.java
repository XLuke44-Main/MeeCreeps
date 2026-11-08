package mcjty.meecreeps.api;

import net.minecraft.util.math.BlockPos;

public interface IBuildSchematic {

    BlockPos getMinPos();

    BlockPos getMaxPos();

    IDesiredBlock getDesiredBlock(BlockPos relativePos);
}
