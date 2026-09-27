package mcjty.meecreeps.items;

import mcjty.meecreeps.blocks.PortalSpawnerBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.block.SoundType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PortalSpawnerItemBlock extends ItemBlock {

    public PortalSpawnerItemBlock(PortalSpawnerBlock block) {
        super(block);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos clickedWallPos,
                                      EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {

        if (facing == null || !facing.getAxis().isHorizontal()) {
            return EnumActionResult.FAIL;
        }

        PortalSpawnerBlock spawnerBlock = (PortalSpawnerBlock) this.block;
        BlockPos supportLower = findPlacementSupport(world, clickedWallPos, facing);
        if (supportLower == null) {
            return EnumActionResult.FAIL;
        }

        BlockPos lowerPos = supportLower.offset(facing);
        BlockPos upperPos = lowerPos.up();
        ItemStack stack = player.getHeldItem(hand);

        if (stack.isEmpty()
                || !player.canPlayerEdit(lowerPos, facing, stack)
                || !player.canPlayerEdit(upperPos, facing, stack)) {
            return EnumActionResult.FAIL;
        }

        if (!spawnerBlock.canPlaceSpawnerPairAt(world, lowerPos, facing)) {
            return EnumActionResult.FAIL;
        }

        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        IBlockState newState = spawnerBlock.getDefaultState()
                .withProperty(PortalSpawnerBlock.FACING, facing)
                .withProperty(PortalSpawnerBlock.HALF, PortalSpawnerBlock.Half.LOWER)
                .withProperty(PortalSpawnerBlock.ACTIVE, false);

        if (!world.setBlockState(lowerPos, newState, 11)) {
            return EnumActionResult.FAIL;
        }

        spawnerBlock.onBlockPlacedBy(world, lowerPos, newState, player, stack);
        if (world.getBlockState(lowerPos).getBlock() != spawnerBlock
                || world.getBlockState(upperPos).getBlock() != spawnerBlock) {
            return EnumActionResult.FAIL;
        }

        SoundType soundType = newState.getBlock().getSoundType(newState, world, lowerPos, player);
        world.playSound(player, lowerPos, soundType.getPlaceSound(), SoundCategory.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }

    @javax.annotation.Nullable
    private static BlockPos findPlacementSupport(World world, BlockPos clickedWallPos, EnumFacing facing) {

        BlockPos[] candidates = {
                clickedWallPos.down(),
                clickedWallPos,
                clickedWallPos.up()
        };

        for (BlockPos lowerSupport : candidates) {
            if (isValidPlacementPair(world, lowerSupport, facing)) {
                return lowerSupport;
            }
        }
        return null;
    }

    private static boolean isValidPlacementPair(World world, BlockPos lowerSupport, EnumFacing facing) {
        if (world == null || lowerSupport == null || facing == null
                || !facing.getAxis().isHorizontal()) {
            return false;
        }

        BlockPos upperSupport = lowerSupport.up();

        IBlockState lowerWallState = world.getBlockState(lowerSupport);
        IBlockState upperWallState = world.getBlockState(upperSupport);
        if (!isValidWallSupport(world, lowerSupport, lowerWallState, facing)
                || !isValidWallSupport(world, upperSupport, upperWallState, facing)) {
            return false;
        }

        BlockPos lowerPos = lowerSupport.offset(facing);
        BlockPos upperPos = lowerPos.up();

        return isReplaceablePlacementSpace(world, lowerPos)
                && isReplaceablePlacementSpace(world, upperPos);
    }

    private static boolean isReplaceablePlacementSpace(World world, BlockPos pos) {
        return world != null && pos != null
                && (world.isAirBlock(pos)
                    || world.getBlockState(pos).getBlock().isReplaceable(world, pos));
    }

    private static boolean isValidWallSupport(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        if (world == null || state == null || side == null || !side.getAxis().isHorizontal()) {
            return false;
        }

        return state.isSideSolid(world, pos, side)
                || state.getMaterial().blocksMovement()
                || state.isFullBlock();
    }
}
