package mcjty.meecreeps.api;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public interface IMeeCreepsApi {

    void registerActionFactory(String id, String message, IActionFactory factory);

    boolean spawnMeeCreep(String id, @Nullable String furtherQuestionId, World world, BlockPos targetPos, EnumFacing targetSide,
                          @Nullable EntityPlayerMP player, boolean doSound);
}
