package mcjty.meecreeps.blocks;

import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.items.TransportSolutionType;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.UUID;

public class PortalSpawnerBlock extends Block {

    public enum Half implements IStringSerializable {
        LOWER("lower"),
        UPPER("upper");

        private final String name;

        Half(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public static final net.minecraft.block.properties.PropertyDirection FACING =
            net.minecraft.block.properties.PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final net.minecraft.block.properties.PropertyEnum<Half> HALF =
            net.minecraft.block.properties.PropertyEnum.create("half", Half.class);
    public static final net.minecraft.block.properties.PropertyBool ACTIVE =
            net.minecraft.block.properties.PropertyBool.create("active");

    public static final String LINKED_PAIR_ID = "linkedPairId";
    public static final String LINKED_TWIN_DIM = "linkedTwinDim";
    public static final String LINKED_TWIN_POS = "linkedTwinPos";
    public static final String LINKED_FIRST_DIM = "linkedFirstDim";
    public static final String LINKED_FIRST_POS = "linkedFirstPos";

    private static final ThreadLocal<Boolean> PORTAL_REPLACEMENT = new ThreadLocal<>();

    private final TransportSolutionType transportSolutionType;
    private final boolean linked;

    public PortalSpawnerBlock(TransportSolutionType transportSolutionType) {
        this(transportSolutionType, false);
    }

    public PortalSpawnerBlock(TransportSolutionType transportSolutionType, boolean linked) {
        super(Material.IRON);

        setLightLevel(1.0F);
        this.transportSolutionType = transportSolutionType;
        this.linked = linked;
        String suffix = (linked ? "portal_spawner_linked_" : "portal_spawner_") + transportSolutionType.getName();
        setUnlocalizedName(MeeCreeps.MODID + "." + suffix);
        setRegistryName(suffix);
        setHardness(2.0F);
        setResistance(6.0F);
        setCreativeTab(MeeCreeps.setup.getTab());
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(HALF, Half.LOWER)
                .withProperty(ACTIVE, false));
    }

    public TransportSolutionType getTransportSolutionType() {
        return transportSolutionType;
    }

    public boolean isLinked() {
        return linked;
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {

        ModelLoader.setCustomStateMapper(this, new StateMap.Builder().ignore(ACTIVE).build());

}

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, HALF, ACTIVE);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        Half half = (meta & 4) == 0 ? Half.LOWER : Half.UPPER;
        boolean active = (meta & 8) != 0;
        int facingId = meta & 3;
        EnumFacing facing;
        switch (facingId) {
            case 1: facing = EnumFacing.SOUTH; break;
            case 2: facing = EnumFacing.WEST; break;
            case 3: facing = EnumFacing.EAST; break;
            case 0:
            default: facing = EnumFacing.NORTH; break;
        }
        return getDefaultState()
                .withProperty(FACING, facing)
                .withProperty(HALF, half)
                .withProperty(ACTIVE, active);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta;
        switch (state.getValue(FACING)) {
            case SOUTH: meta = 1; break;
            case WEST:  meta = 2; break;
            case EAST:  meta = 3; break;
            case NORTH:
            default:    meta = 0; break;
        }
        if (state.getValue(HALF) == Half.UPPER) meta |= 4;
        if (state.getValue(ACTIVE)) meta |= 8;
        return meta;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {

        return state.getValue(HALF) == Half.UPPER && (state.getValue(ACTIVE) || world.getBlockState(pos.down()).getBlock() == ModBlocks.portalBlock) ? 15 : 0;
    }

    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean getUseNeighborBrightness(IBlockState state) {

        return false;
    }

    @Override
    public int getPackedLightmapCoords(IBlockState state, IBlockAccess world, BlockPos pos) {

        if (state.getValue(HALF) == Half.UPPER && (state.getValue(ACTIVE) || world.getBlockState(pos.down()).getBlock() == ModBlocks.portalBlock)) {
            return 15728880;
        }
        return super.getPackedLightmapCoords(state, world, pos);
    }

    @Override
    @Nullable
    public net.minecraft.tileentity.TileEntity createTileEntity(World world, IBlockState state) {
        return state.getValue(HALF) == Half.UPPER ? new PortalSpawnerTileEntity() : null;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return state.getValue(HALF) == Half.UPPER;
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {

        return canOccupy(world, pos) && canOccupy(world, pos.up());
    }

    public boolean canPlaceSpawnerPairAt(World world, BlockPos lowerPos, EnumFacing facing) {
        if (world == null || lowerPos == null || facing == null || !facing.getAxis().isHorizontal()) {
            return false;
        }

        BlockPos upperPos = lowerPos.up();
        if (!canOccupy(world, lowerPos) || !canOccupy(world, upperPos)) {
            return false;
        }

        return hasSpawnerWallSupport(world, lowerPos, facing);
    }

    private boolean canCompletePlacedSpawnerPairAt(World world, BlockPos lowerPos, EnumFacing facing) {
        if (world == null || lowerPos == null || facing == null || !facing.getAxis().isHorizontal()) {
            return false;
        }
        BlockPos upperPos = lowerPos.up();
        if (!canOccupy(world, upperPos)) {
            return false;
        }
        return hasSpawnerWallSupport(world, lowerPos, facing);
    }

    private boolean hasSpawnerWallSupport(World world, BlockPos lowerPos, EnumFacing facing) {
        if (world == null || lowerPos == null || facing == null || !facing.getAxis().isHorizontal()) {
            return false;
        }

        BlockPos upperPos = lowerPos.up();

        BlockPos lowerWall = lowerPos.offset(facing.getOpposite());
        BlockPos upperWall = upperPos.offset(facing.getOpposite());

        return isValidWallSupport(world, lowerWall, facing)
                && isValidWallSupport(world, upperWall, facing);
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        if (side == null || !side.getAxis().isHorizontal()) return false;
        return canPlaceSpawnerPairAt(world, pos, side);
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {

        EnumFacing horizontal = facing != null && facing.getAxis().isHorizontal()
                ? facing
                : (placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite());
        return getDefaultState()
                .withProperty(FACING, horizontal)
                .withProperty(HALF, Half.LOWER)
                .withProperty(ACTIVE, false);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote || state.getValue(HALF) != Half.LOWER) return;
        BlockPos upperPos = pos.up();
        EnumFacing facing = state.getValue(FACING);
        if (!canCompletePlacedSpawnerPairAt(world, pos, facing)) {
            world.setBlockToAir(pos);
            return;
        }

        world.setBlockState(upperPos,
                getDefaultState()
                        .withProperty(FACING, state.getValue(FACING))
                        .withProperty(HALF, Half.UPPER)
                        .withProperty(ACTIVE, false), 3);

        PortalSpawnerTileEntity upperTe = getSpawnerTile(world, upperPos);
        if (upperTe == null) return;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag = tag.copy();
            tag.setInteger("x", upperPos.getX());
            tag.setInteger("y", upperPos.getY());
            tag.setInteger("z", upperPos.getZ());
            upperTe.readFromNBT(tag);
        }

        if (linked) {
            linkPlacedSpawner(world, upperPos, upperTe, stack);

            upperTe.ensureChunkLoadedForSpawner();
        }
    }

    private void linkPlacedSpawner(World world, BlockPos upperPos, PortalSpawnerTileEntity upperTe, ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        if (!tag.hasUniqueId(LINKED_PAIR_ID)) {
            tag.setUniqueId(LINKED_PAIR_ID, UUID.randomUUID());
        }
        UUID pairId = tag.getUniqueId(LINKED_PAIR_ID);
        upperTe.setLinkedPairId(pairId);

        if (tag.hasKey(LINKED_TWIN_DIM) && tag.hasKey(LINKED_TWIN_POS)) {
            int twinDim = tag.getInteger(LINKED_TWIN_DIM);
            BlockPos twinPos = BlockPos.fromLong(tag.getLong(LINKED_TWIN_POS));
            if (tryLinkToExistingTwin(world, upperPos, upperTe, pairId, twinDim, twinPos)) {
                tag.removeTag(LINKED_FIRST_DIM);
                tag.removeTag(LINKED_FIRST_POS);
                return;
            }
        }

        if (tag.hasKey(LINKED_FIRST_DIM) && tag.hasKey(LINKED_FIRST_POS)) {
            int firstDim = tag.getInteger(LINKED_FIRST_DIM);
            BlockPos firstPos = BlockPos.fromLong(tag.getLong(LINKED_FIRST_POS));
            if (!upperPos.equals(firstPos) && tryLinkToExistingTwin(world, upperPos, upperTe, pairId, firstDim, firstPos)) {
                tag.removeTag(LINKED_FIRST_DIM);
                tag.removeTag(LINKED_FIRST_POS);
                return;
            }
        }

        if (tryFindAndLinkExistingTwin(world, upperPos, upperTe, pairId)) {
            tag.removeTag(LINKED_FIRST_DIM);
            tag.removeTag(LINKED_FIRST_POS);
            return;
        }

        tag.setInteger(LINKED_FIRST_DIM, world.provider.getDimension());
        tag.setLong(LINKED_FIRST_POS, upperPos.toLong());
        tag.removeTag(LINKED_TWIN_DIM);
        tag.removeTag(LINKED_TWIN_POS);
    }

    private boolean tryFindAndLinkExistingTwin(World currentWorld, BlockPos currentUpperPos,
                                               PortalSpawnerTileEntity currentTe, UUID pairId) {
        Integer[] dimensions = net.minecraftforge.common.DimensionManager.getStaticDimensionIDs();
        for (int dimension : dimensions) {
            World candidateWorld = net.minecraftforge.common.DimensionManager.getWorld(dimension);
            if (candidateWorld == null) continue;

            for (net.minecraft.tileentity.TileEntity tileEntity : candidateWorld.loadedTileEntityList) {
                if (!(tileEntity instanceof PortalSpawnerTileEntity)) continue;
                PortalSpawnerTileEntity candidate = (PortalSpawnerTileEntity) tileEntity;
                if (candidate == currentTe || candidate.getPos().equals(currentUpperPos)) continue;
                if (!pairId.equals(candidate.getLinkedPairId())) continue;

                IBlockState candidateState = candidateWorld.getBlockState(candidate.getPos());
                if (!(candidateState.getBlock() instanceof PortalSpawnerBlock)
                        || candidateState.getValue(HALF) != Half.UPPER
                        || !((PortalSpawnerBlock) candidateState.getBlock()).isLinked()
                        || candidateState.getBlock() != this) continue;

                if (candidate.getTwinDimension() != null && candidate.getTwinPos() != null) {
                    if (candidateWorld.provider.getDimension() == currentWorld.provider.getDimension()
                            && candidate.getTwinPos().equals(currentUpperPos)) {
                        return false;
                    }
                    continue;
                }

                currentTe.setTwin(dimension, candidate.getPos());
                candidate.setTwin(currentWorld.provider.getDimension(), currentUpperPos);
                return true;
            }
        }
        return false;
    }

    private boolean tryLinkToExistingTwin(World currentWorld, BlockPos currentUpperPos,
                                          PortalSpawnerTileEntity currentTe, UUID pairId,
                                          int twinDimension, BlockPos twinUpperPos) {
        World twinWorld = mcjty.meecreeps.teleport.TeleportationTools.resolveDestinationWorld(twinDimension);
        if (twinWorld == null) return false;
        twinWorld.getChunkFromBlockCoords(twinUpperPos);

        IBlockState twinState = twinWorld.getBlockState(twinUpperPos);
        if (twinState.getBlock() != this
                || twinState.getValue(HALF) != Half.UPPER
                || !((PortalSpawnerBlock) twinState.getBlock()).isLinked()) {
            return false;
        }
        PortalSpawnerTileEntity twinTe = getSpawnerTile(twinWorld, twinUpperPos);
        if (twinTe == null) return false;
        UUID twinPairId = twinTe.getLinkedPairId();
        if (twinPairId == null || !twinPairId.equals(pairId)) return false;

        currentTe.setTwin(twinDimension, twinUpperPos);
        twinTe.setTwin(currentWorld.provider.getDimension(), currentUpperPos);
        return true;
    }

    @Nullable
    private static PortalSpawnerTileEntity getSpawnerTile(World world, BlockPos upperPos) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(upperPos);
        return te instanceof PortalSpawnerTileEntity ? (PortalSpawnerTileEntity) te : null;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        if (world.isRemote) return;

        EnumFacing facing = state.getValue(FACING);
        if (state.getValue(HALF) == Half.UPPER) {

            BlockPos upperWall = pos.offset(facing.getOpposite());
            if (!isValidWallSupport(world, upperWall, facing)) {
                world.setBlockToAir(pos);
                return;
            }

            if (isPortalReplacement()) return;

            BlockPos lowerPos = pos.down();
            IBlockState lowerState = world.getBlockState(lowerPos);
            boolean validLower = lowerState.getBlock() == this
                    && lowerState.getValue(HALF) == Half.LOWER
                    && lowerState.getValue(FACING) == facing;

            if (!validLower && lowerState.getBlock() == ModBlocks.portalBlock) {
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(lowerPos);
                if (te instanceof PortalTileEntity) {
                    BlockPos spawnerPos = ((PortalTileEntity) te).getSpawnerPos();
                    validLower = pos.equals(spawnerPos);
                }
            }

            if (!validLower) {
                world.setBlockToAir(pos);
            }
            return;
        }

        BlockPos wall = pos.offset(facing.getOpposite());
        if (!isValidWallSupport(world, wall, facing)) {
            world.setBlockToAir(pos);
        }
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        if (willHarvest) return true;
        return super.removedByPlayer(state, world, pos, player, false);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state,
                              net.minecraft.tileentity.TileEntity te, ItemStack stack) {
        super.harvestBlock(world, player, pos, state, te, stack);
        world.setBlockToAir(pos);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && !isPortalReplacement()) {
            BlockPos upperPos = state.getValue(HALF) == Half.UPPER ? pos : pos.up();
            net.minecraft.tileentity.TileEntity upperTe = world.getTileEntity(upperPos);
            if (upperTe instanceof PortalSpawnerTileEntity) {

                ((PortalSpawnerTileEntity) upperTe).onSpawnerBroken();
            }

            BlockPos partner = state.getValue(HALF) == Half.UPPER ? pos.down() : pos.up();
            IBlockState partnerState = world.getBlockState(partner);
            if (partnerState.getBlock() == this && partnerState.getValue(HALF) != state.getValue(HALF)) {
                try {
                    PORTAL_REPLACEMENT.set(Boolean.TRUE);
                    world.setBlockToAir(partner);
                } finally {
                    PORTAL_REPLACEMENT.remove();
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos,
                         IBlockState state, int fortune) {
        Item item = Item.getItemFromBlock(this);
        if (item == null) return;

        ItemStack drop = new ItemStack(item, 1, 0);
        BlockPos upperPos = state.getValue(HALF) == Half.UPPER ? pos : pos.up();
        net.minecraft.tileentity.TileEntity tileEntity = world.getTileEntity(upperPos);
        if (tileEntity instanceof PortalSpawnerTileEntity) {
            NBTTagCompound tag = ((PortalSpawnerTileEntity) tileEntity).writeToNBT(new NBTTagCompound());
            tag.removeTag("id");
            tag.removeTag("x");
            tag.removeTag("y");
            tag.removeTag("z");
            if (!tag.getKeySet().isEmpty()) {
                drop.setTagCompound(tag);
            }
        }
        drops.add(drop);
    }

    private static boolean canOccupy(World world, BlockPos pos) {
        if (world.isAirBlock(pos)) return true;
        return world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }

    private static boolean isValidWallSupport(World world, BlockPos pos, EnumFacing side) {
        if (world == null || pos == null || side == null || !side.getAxis().isHorizontal()) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);

        return state.isSideSolid(world, pos, side)
                || state.getMaterial().blocksMovement()
                || state.isFullBlock();
    }

    public static void beginPortalReplacement() {
        PORTAL_REPLACEMENT.set(Boolean.TRUE);
    }

    public static void endPortalReplacement() {
        PORTAL_REPLACEMENT.remove();
    }

    private static boolean isPortalReplacement() {
        return Boolean.TRUE.equals(PORTAL_REPLACEMENT.get());
    }

    public static boolean restoreLowerSection(World world, BlockPos upperPos) {
        if (world == null || upperPos == null) return false;
        IBlockState upperState = world.getBlockState(upperPos);
        if (!(upperState.getBlock() instanceof PortalSpawnerBlock)
                || upperState.getValue(HALF) != Half.UPPER) {
            return false;
        }
        PortalSpawnerBlock block = (PortalSpawnerBlock) upperState.getBlock();
        BlockPos lowerPos = upperPos.down();
        IBlockState lowerState = world.getBlockState(lowerPos);
        if (!world.isAirBlock(lowerPos) && lowerState.getBlock() != ModBlocks.portalBlock) {
            return false;
        }

        boolean alreadyReplacing = isPortalReplacement();
        if (!alreadyReplacing) beginPortalReplacement();
        try {
            if (upperState.getValue(ACTIVE)) {
                world.setBlockState(upperPos, upperState.withProperty(ACTIVE, false), 3);
            }
            world.setBlockState(lowerPos,
                    block.getDefaultState()
                            .withProperty(FACING, upperState.getValue(FACING))
                            .withProperty(HALF, Half.LOWER)
                            .withProperty(ACTIVE, false), 3);
        } finally {
            if (!alreadyReplacing) endPortalReplacement();
        }
        refreshPortalSpawnerLight(world, upperPos);
        return true;
    }

    public static void setPortalActive(World world, BlockPos upperPos, boolean active) {
        if (world == null || upperPos == null) return;
        IBlockState state = world.getBlockState(upperPos);
        if (!(state.getBlock() instanceof PortalSpawnerBlock)
                || state.getValue(HALF) != Half.UPPER) return;

        if (state.getValue(ACTIVE) != active) {
            world.setBlockState(upperPos, state.withProperty(ACTIVE, active), 3);
        }

        refreshPortalSpawnerLight(world, upperPos);
    }

    private static void refreshPortalSpawnerLight(World world, BlockPos upperPos) {
        BlockPos lowerPos = upperPos.down();
        world.notifyLightSet(lowerPos);
        world.notifyLightSet(upperPos);
        world.markBlockRangeForRenderUpdate(lowerPos.add(-1, -1, -1), upperPos.add(1, 1, 1));
        world.checkLight(lowerPos);
        world.checkLight(upperPos);
        world.checkLight(upperPos.up());
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        switch (state.getValue(FACING)) {

            case NORTH: return new AxisAlignedBB(0, 0, 0.9375, 1, 1, 1);
            case SOUTH: return new AxisAlignedBB(0, 0, 0, 1, 1, 0.0625);
            case EAST:  return new AxisAlignedBB(0, 0, 0, 0.0625, 1, 1);
            case WEST:  return new AxisAlignedBB(0.9375, 0, 0, 1, 1, 1);
            default:    return new AxisAlignedBB(0, 0, 0, 1, 1, 1);
        }
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean isFullBlock(IBlockState state) { return false; }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean isBlockNormalCube(IBlockState state) { return false; }
}
