package mcjty.meecreeps.actions.workers;

import mcjty.meecreeps.api.IMeeCreep;
import mcjty.meecreeps.api.IWorkerHelper;
import mcjty.meecreeps.varia.GeneralTools;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlattenAreaActionWorker extends AbstractActionWorker {

    private int size = 0;

    private Set<BlockPos> positionsToSkip = new HashSet<>();
    private int workingLevel = Integer.MIN_VALUE;
    private int overheadY = Integer.MIN_VALUE;

    public FlattenAreaActionWorker(IWorkerHelper helper) {
        super(helper);
    }

    @Override
    public boolean onlyStopWhenDone() {
        return true;
    }

    @Override
    public void init(IMeeCreep meeCreep) {
        helper.setSpeed(3);
        if (workingLevel == Integer.MIN_VALUE) {
            workingLevel = meeCreep.getEntity().getPosition().getY();
        }
    }

    @Nullable
    @Override
    public AxisAlignedBB getActionBox() {
        return null;
    }

    private int getSize() {
        if (size == 0) {
            String id = options.getFurtherQuestionId();
            if ("9x9".equals(id)) {
                size = 9;
            } else if ("11x11".equals(id)) {
                size = 11;
            } else if ("13x13".equals(id)) {
                size = 13;
            } else if ("15x15".equals(id)) {
                size = 15;
            } else if ("17x17".equals(id)) {
                size = 17;
            } else if ("19x19".equals(id)) {
                size = 19;
            } else if ("21x21".equals(id)) {
                size = 21;
            } else {
                size = 13;
            }
        }
        return size;
    }

    private BlockPos findOverheadSpot() {
        IMeeCreep creep = helper.getMeeCreep();
        World world = creep.getWorld();
        BlockPos center = options.getTargetPos();
        int halfSize = (getSize() - 1) / 2;
        if (workingLevel == Integer.MIN_VALUE) {
            workingLevel = creep.getEntity().getPosition().getY();
        }

        if (overheadY == Integer.MIN_VALUE) {
            int highest = workingLevel;
            for (int x = -halfSize; x <= halfSize; x++) {
                for (int z = -halfSize; z <= halfSize; z++) {
                    BlockPos column = center.add(x, 0, z);
                    int y = world.getChunkFromBlockCoords(column).getTopFilledSegment() + 15;
                    while (y > workingLevel) {
                        BlockPos p = new BlockPos(column.getX(), y, column.getZ());
                        IBlockState state = world.getBlockState(p);
                        if (state.getMaterial() != Material.AIR) {
                            highest = Math.max(highest, y);
                            break;
                        }
                        y--;
                    }
                }
            }
            overheadY = highest;
        }

        while (overheadY > workingLevel) {
            List<BlockPos> layer = new ArrayList<>();
            for (int x = -halfSize; x <= halfSize; x++) {
                for (int z = -halfSize; z <= halfSize; z++) {
                    BlockPos p = center.add(x, overheadY, z);
                    if (positionsToSkip.contains(p)) {
                        continue;
                    }
                    IBlockState state = world.getBlockState(p);
                    if (state.getMaterial() == Material.AIR) {
                        continue;
                    }
                    if (!helper.allowedToHarvest(state, world, p, GeneralTools.getHarvester(world))) {
                        positionsToSkip.add(p);
                        continue;
                    }
                    layer.add(p);
                }
            }
            if (!layer.isEmpty()) {
                BlockPos current = creep.getEntity().getPosition();
                layer.sort((a, b) -> Double.compare(current.distanceSq(a), current.distanceSq(b)));
                return layer.get(0);
            }
            overheadY--;
        }
        return null;
    }

    private BlockPos findSpotToFlatten() {
        BlockPos overhead = findOverheadSpot();
        if (overhead != null) {
            return overhead;
        }
        IMeeCreep entity = helper.getMeeCreep();
        BlockPos tpos = options.getTargetPos();
        int hs = (getSize() - 1) / 2;

        List<BlockPos> todo = new ArrayList<>();
        int baseY = workingLevel == Integer.MIN_VALUE
                ? entity.getEntity().getPosition().getY() : workingLevel;
        World world = entity.getWorld();
        for (int y = 4; y >= 0; y--) {
            for (int x = -hs; x <= hs; x++) {
                for (int z = -hs; z <= hs; z++) {
                    BlockPos p = new BlockPos(tpos.getX() + x, baseY + y, tpos.getZ() + z);
                    if (positionsToSkip.contains(p)) {
                        continue;
                    }
                    IBlockState state = world.getBlockState(p);
                    if (state.getMaterial() == Material.AIR) {
                        continue;
                    }
                    if (!helper.allowedToHarvest(state, world, p, GeneralTools.getHarvester(world))) {
                        positionsToSkip.add(p);
                        continue;
                    }
                    todo.add(p);
                }
            }
            if (!todo.isEmpty()) {
                break;
            }
        }
        if (todo.isEmpty()) {
            return null;
        }

        BlockPos position = entity.getEntity().getPosition();
        todo.sort((o1, o2) -> {
            double d1 = position.distanceSq(o1);
            double d2 = position.distanceSq(o2);
            return Double.compare(d1, d2);
        });
        return todo.get(0);
    }

    @Override
    public void tick(boolean timeToWrapUp) {
        if (timeToWrapUp) {
            helper.done();
            return;
        }
        handleFlatten();
    }

    private void handleFlatten() {
        BlockPos flatSpot = findSpotToFlatten();
        if (flatSpot == null) {
            helper.taskIsDone();
        } else {
            BlockPos navigate = helper.findBestNavigationSpot(flatSpot);
            if (navigate != null) {
                helper.navigateTo(navigate, p -> {
                    helper.delayForHardBlocks(flatSpot, pp -> {
                        if (!helper.harvestAndDrop(flatSpot)) {
                            positionsToSkip.add(flatSpot);
                        }
                    });
                });
            } else {

                helper.delayForHardBlocks(flatSpot, pp -> {
                    if (!helper.harvestAndDrop(flatSpot)) {
                        positionsToSkip.add(flatSpot);
                    }
                });
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("flattenWorkingLevel")) {
            workingLevel = tag.getInteger("flattenWorkingLevel");
        }
        if (tag.hasKey("flattenOverheadY")) {
            overheadY = tag.getInteger("flattenOverheadY");
        }
        positionsToSkip.clear();
        NBTTagList skipped = tag.getTagList("flattenSkipped", 4);
        for (int i = 0; i < skipped.tagCount(); i++) {
            positionsToSkip.add(BlockPos.fromLong(((NBTTagLong) skipped.get(i)).getLong()));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        if (workingLevel != Integer.MIN_VALUE) {
            tag.setInteger("flattenWorkingLevel", workingLevel);
        }
        if (overheadY != Integer.MIN_VALUE) {
            tag.setInteger("flattenOverheadY", overheadY);
        }
        NBTTagList skipped = new NBTTagList();
        for (BlockPos pos : positionsToSkip) {
            skipped.appendTag(new NBTTagLong(pos.toLong()));
        }
        tag.setTag("flattenSkipped", skipped);
    }

}
