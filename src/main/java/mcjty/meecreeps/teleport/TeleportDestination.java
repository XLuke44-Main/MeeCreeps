package mcjty.meecreeps.teleport;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

public class TeleportDestination {
    public static final String BLENDER_DIMENSION = "blender_dimension";

    private final String name;
    @Nullable
    private final Integer dimension;
    @Nullable
    private final BlockPos pos;
    @Nullable
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

    private TeleportDestination(String name) {
        this.name = name;
        this.dimension = null;
        this.pos = null;
        this.side = null;
        this.searchNearby = false;
    }

    public static TeleportDestination blenderTrap() {
        return new TeleportDestination(BLENDER_DIMENSION);
    }

    public TeleportDestination(NBTTagCompound tc) {
        String savedName = tc.getString("name");
        boolean blenderTrap = tc.getBoolean("blenderTrap") || BLENDER_DIMENSION.equals(savedName);
        name = blenderTrap ? BLENDER_DIMENSION : savedName;
        if (blenderTrap) {
            dimension = null;
            pos = null;
            side = null;
            searchNearby = false;
        } else {
            dimension = tc.getInteger("dim");
            pos = new BlockPos(tc.getInteger("x"), tc.getInteger("y"), tc.getInteger("z"));
            int sideIndex = tc.getByte("side");
            side = sideIndex >= 0 && sideIndex < EnumFacing.VALUES.length ? EnumFacing.VALUES[sideIndex] : EnumFacing.UP;
            searchNearby = tc.getBoolean("safeSearch");
        }
    }

    public NBTTagCompound getCompound() {
        NBTTagCompound tc = new NBTTagCompound();
        tc.setString("name", getName());
        if (isBlenderTrap()) {
            tc.setBoolean("blenderTrap", true);
            return tc;
        }
        if (dimension != null) tc.setInteger("dim", dimension);
        if (side != null) tc.setByte("side", (byte) side.ordinal());
        if (pos != null) {
            tc.setInteger("x", pos.getX());
            tc.setInteger("y", pos.getY());
            tc.setInteger("z", pos.getZ());
        }
        if (searchNearby) {
            tc.setBoolean("safeSearch", true);
        }
        return tc;
    }

    public String getName() { return name; }
    @Nullable public Integer getDimension() { return dimension; }
    @Nullable public BlockPos getPos() { return pos; }
    @Nullable public EnumFacing getSide() { return side; }
    public boolean shouldSearchNearby() { return searchNearby; }
    public boolean isBlenderTrap() { return BLENDER_DIMENSION.equals(name) && dimension == null && pos == null && side == null; }
}
