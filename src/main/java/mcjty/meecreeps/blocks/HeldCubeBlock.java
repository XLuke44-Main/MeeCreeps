package mcjty.meecreeps.blocks;

import mcjty.meecreeps.MeeCreeps;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityItem;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

@SuppressWarnings("deprecation")
public class HeldCubeBlock extends BlockContainer {

    private final boolean mini;

    public HeldCubeBlock() {
        this("creepcube");
    }

    public HeldCubeBlock(String registryName) {
        super(Material.IRON);
        mini = "minicube".equals(registryName);
        setUnlocalizedName(MeeCreeps.MODID + ".held_" + registryName);
        setRegistryName(registryName);
        setCreativeTab(MeeCreeps.setup.getTab());
        setHardness(2.0F);
        setResistance(6.0F);
        setHarvestLevel("pickaxe", 2);
        setLightOpacity(0);
    }

    public boolean isMiniCubeBlock() {
        return mini;
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new CubeTileEntity();
    }

    public static boolean reclaim(World world, BlockPos pos, EntityPlayer player) {
        if (player == null || !player.isSneaking()) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof CubeTileEntity)) {
            return false;
        }
        ItemStack stack = ((CubeTileEntity) tile).getCube().copy();
        if (stack.isEmpty()) {
            return false;
        }
        world.setBlockToAir(pos);
        EntityItem dropped = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, stack);
        dropped.setDefaultPickupDelay();
        world.spawnEntity(dropped);
        return true;
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
                                    net.minecraft.util.EnumHand hand, net.minecraft.util.EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        reclaim(worldIn, pos, playerIn);
        return true;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, net.minecraft.util.EnumFacing side) {
        return false;
    }

    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Item.getItemFromBlock(this);
    }

    @Override
    public void harvestBlock(World worldIn, EntityPlayer player, BlockPos pos, IBlockState state,
                             @Nullable TileEntity tileEntity, ItemStack tool) {
        if (tileEntity instanceof CubeTileEntity) {
            ItemStack storedCube = ((CubeTileEntity) tileEntity).getCube().copy();
            if (!storedCube.isEmpty()) {
                StatBase blockStat = StatList.getBlockStats(this);
                if (blockStat != null) {
                    player.addStat(blockStat);
                }
                if (!worldIn.isRemote) {
                    spawnAsEntity(worldIn, pos, storedCube);
                }
                return;
            }
        }
        super.harvestBlock(worldIn, player, pos, state, tileEntity, tool);
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), 0, new ModelResourceLocation(getRegistryName(), "inventory"));
    }
}
