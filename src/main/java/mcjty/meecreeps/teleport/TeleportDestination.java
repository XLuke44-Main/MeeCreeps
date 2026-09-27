package mcjty.meecreeps.teleport;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public class TeleportDestination {
    private final String name;
    private final int dimension;
    private final BlockPos pos;
    private final EnumFacing side;
    private final boolean searchNearby;

    public TeleportDestination(String name, int dimension, BlockPos pos, EnumFacing side) {
        this(name, dimension, pos, side, false);
    }

    public TeleportDestination(String name, int dimension, BlockPos pos, EnumFacing side, boolean searchNearby) {
        this.name = name == null ? "" : name;
        this.dimension = dimension;
        this.pos = pos;
        this.side = side == null ? EnumFacing.UP : side;
        this.searchNearby = searchNearby;
    }

    public TeleportDestination(NBTTagCompound tc) {
        name = tc.getString("name");
        dimension = tc.getInteger("dim");
        pos = new BlockPos(tc.getInteger("x"), tc.getInteger("y"), tc.getInteger("z"));
        int sideIndex = tc.getByte("side");
        side = sideIndex >= 0 && sideIndex < EnumFacing.VALUES.length ? EnumFacing.VALUES[sideIndex] : EnumFacing.UP;
        searchNearby = tc.getBoolean("safeSearch");
    }

    public NBTTagCompound getCompound() {
        NBTTagCompound tc = new NBTTagCompound();
        tc.setString("name", getName());
        tc.setInteger("dim", getDimension());
        tc.setByte("side", (byte) getSide().ordinal());
        tc.setInteger("x", getPos().getX());
        tc.setInteger("y", getPos().getY());
        tc.setInteger("z", getPos().getZ());
        if (searchNearby) {
            tc.setBoolean("safeSearch", true);
        }
        return tc;
    }

    public String getName() { return name; }
    public int getDimension() { return dimension; }
    public BlockPos getPos() { return pos; }
    public EnumFacing getSide() { return side; }
    public boolean shouldSearchNearby() { return searchNearby; }
}
