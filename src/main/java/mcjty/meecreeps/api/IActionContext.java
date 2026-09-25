package mcjty.meecreeps.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

public interface IActionContext {

    BlockPos getTargetPos();

    EnumFacing getTargetSide();

    @Nullable
    String getFurtherQuestionId();

    @Nullable
    EntityPlayer getPlayer();
}
