package mcjty.meecreeps.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface IWorkerHelper {

    IActionContext getContext();

    IMeeCreep getMeeCreep();

    IDesiredBlock getAirBlock();

    IDesiredBlock getIgnoreBlock();

    void delay(int ticks, Runnable task);

    boolean allowedToHarvest(IBlockState state, World world, BlockPos pos, EntityPlayer entityPlayer);

    boolean placeBuildingBlock(BlockPos pos, IDesiredBlock desiredBlock);

    BlockPos findSpotToFlatten(@Nonnull IBuildSchematic schematic);

    BlockPos findSpotToBuild(@Nonnull IBuildSchematic schematic, @Nonnull BuildProgress progress, @Nonnull Set<BlockPos> toSkip);

    void delayForHardBlocks(BlockPos pos, Consumer<BlockPos> nextJob);

    boolean handleFlatten(@Nonnull IBuildSchematic schematic);

    boolean handleBuilding(@Nonnull IBuildSchematic schematic, @Nonnull BuildProgress progress, @Nonnull Set<BlockPos> toSkip);

    void placeStackAt(ItemStack blockStack, World world, BlockPos pos);

    boolean harvestAndPickup(BlockPos pos);

    boolean harvestAndDrop(BlockPos pos);

    void pickup(EntityItem item);

    void done();

    void taskIsDone();

    void putStuffAway();

    void speedUp(int t);

    void showMessage(String message, String... parameters);

    void giveDropsToMeeCreeps(@Nonnull List<ItemStack> drops);

    void registerHarvestableBlock(BlockPos pos);

    void navigateTo(BlockPos pos, Consumer<BlockPos> job);

    boolean navigateTo(Entity dest, Consumer<BlockPos> job, double maxDist);

    boolean navigateTo(Entity dest, Consumer<BlockPos> job);

    void setSpeed(int speed);

    int getSpeed();

    void dropAndPutAwayLater(ItemStack stack);

    BlockPos findSuitablePositionNearPlayer(double distance);

    void giveToPlayerOrDrop();

    boolean findItemOnGroundOrInChest(Predicate<ItemStack> matcher, int maxAmount, String message, String... parameters);

    boolean findItemOnGroundOrInChest(Predicate<ItemStack> matcher, int maxAmount);

    boolean findItemOnGround(AxisAlignedBB box, Predicate<ItemStack> matcher, Consumer<EntityItem> job);

    void putInventoryInChest(BlockPos pos);

    boolean findSuitableInventory(AxisAlignedBB box, Predicate<ItemStack> matcher, Consumer<BlockPos> job);

    BlockPos findBestNavigationSpot(BlockPos pos);
}
