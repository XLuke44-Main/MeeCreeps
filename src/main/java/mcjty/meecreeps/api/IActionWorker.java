package mcjty.meecreeps.api;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface IActionWorker {

    void tick(boolean timeToWrapUp);

    default void init(IMeeCreep meeCreep) { }

    @Nullable
    AxisAlignedBB getActionBox();

    @Nonnull
    AxisAlignedBB getSearchBox();

    default boolean onlyStopWhenDone() { return false; }

    default boolean needsToFollowPlayer() { return false; }

    PreferedChest[] getPreferedChests();

    default void readFromNBT(NBTTagCompound tag) {}

    default void writeToNBT(NBTTagCompound tag) {}
}
