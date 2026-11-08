package mcjty.meecreeps.blocks;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class CubeTileEntity extends TileEntity {

    private ItemStack cube = ItemStack.EMPTY;

    public void setCube(ItemStack cube) {
        this.cube = cube == null ? ItemStack.EMPTY : cube.copy();
        markDirty();
    }

    public ItemStack getCube() {
        return cube;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (!cube.isEmpty()) {
            compound.setTag("CubeStack", cube.writeToNBT(new NBTTagCompound()));
        } else {
            compound.removeTag("CubeStack");
        }
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        cube = compound.hasKey("CubeStack", 10) ? new ItemStack(compound.getCompoundTag("CubeStack")) : ItemStack.EMPTY;
    }
}
