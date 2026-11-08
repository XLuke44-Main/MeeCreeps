package mcjty.meecreeps.items;

import mcjty.lib.network.PacketSendServerCommand;
import mcjty.lib.typed.TypedMap;
import mcjty.meecreeps.CommandHandler;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.MeeCreepsApi;
import mcjty.meecreeps.actions.MeeCreepActionType;
import mcjty.meecreeps.actions.PacketShowBalloonToClient;
import mcjty.meecreeps.actions.ServerActionManager;
import mcjty.meecreeps.blocks.CubeTileEntity;
import mcjty.meecreeps.blocks.HeldCubeBlock;
import mcjty.meecreeps.blocks.ModBlocks;
import mcjty.meecreeps.config.ConfigSetup;
import mcjty.meecreeps.network.MeeCreepsMessages;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CreepCubeItem extends ItemBlock {

    private final boolean miniCube;
    private final String cubeUnlocalizedName;

    public CreepCubeItem() {
        this("creepcube", false, ModBlocks.heldCubeBlock);
    }

    protected CreepCubeItem(String registryName, boolean miniCube, HeldCubeBlock block) {
        super(block);
        this.miniCube = miniCube;
        this.cubeUnlocalizedName = "item." + MeeCreeps.MODID + "." + registryName;
        setRegistryName(registryName);
        setUnlocalizedName(MeeCreeps.MODID + "." + registryName);
        setMaxStackSize(1);
        setCreativeTab(MeeCreeps.setup.getTab());
    }

    @Override
    public String getUnlocalizedName() {
        return cubeUnlocalizedName;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return cubeUnlocalizedName;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        return EnumActionResult.PASS;
    }

    public boolean isMiniCube() {
        return miniCube;
    }

    @Override
    public void onCreated(ItemStack stack, World worldIn, EntityPlayer playerIn) {
        getCubeId(stack);
    }

    @Nullable
    public static UUID getCubeId(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof CreepCubeItem)) {
            return null;
        }
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        if (!stack.getTagCompound().hasUniqueId("meecreepsCubeId")) {
            stack.getTagCompound().setUniqueId("meecreepsCubeId", UUID.randomUUID());
        }
        return stack.getTagCompound().getUniqueId("meecreepsCubeId");
    }

    public static UUID getCubeId(ItemStack stack, EntityPlayer player) {
        UUID id = getCubeId(stack);
        if (id == null || player == null) {
            return id;
        }
        boolean foundStack = false;
        boolean duplicateBefore = false;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack candidate = player.inventory.getStackInSlot(i);
            if (candidate == stack) {
                foundStack = true;
                break;
            }
            if (!candidate.isEmpty() && candidate.getItem() instanceof CreepCubeItem
                    && id.equals(getCubeId(candidate))) {
                duplicateBefore = true;
            }
        }
        if (foundStack && duplicateBefore) {
            id = UUID.randomUUID();
            stack.getTagCompound().setUniqueId("meecreepsCubeId", id);
        }
        return id;
    }

    public static ItemStack getHeldCube(EntityPlayer player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getHeldItem(EnumHand.MAIN_HAND);
        if (!main.isEmpty() && main.getItem() instanceof CreepCubeItem) {
            return main;
        }
        ItemStack off = player.getHeldItem(EnumHand.OFF_HAND);
        return !off.isEmpty() && off.getItem() instanceof CreepCubeItem ? off : ItemStack.EMPTY;
    }

    public static ItemStack getHeldCube(EntityPlayer player, UUID cubeId) {
        if (player == null || cubeId == null) return ItemStack.EMPTY;
        ItemStack main = player.getHeldItem(EnumHand.MAIN_HAND);
        if (cubeId.equals(getCubeId(main, player))) return main;
        ItemStack off = player.getHeldItem(EnumHand.OFF_HAND);
        return cubeId.equals(getCubeId(off, player)) ? off : ItemStack.EMPTY;
    }

    public static ItemStack getCube(EntityPlayer player, UUID cubeId) {
        if (player == null || cubeId == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack candidate = player.inventory.getStackInSlot(i);
            if (!candidate.isEmpty() && candidate.getItem() instanceof CreepCubeItem
                    && cubeId.equals(getCubeId(candidate, player))) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack getCube(EntityPlayer player, boolean miniCube) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getHeldItem(EnumHand.MAIN_HAND);
        if (!main.isEmpty() && main.getItem() instanceof CreepCubeItem
                && ((CreepCubeItem) main.getItem()).isMiniCube() == miniCube) {
            return main;
        }
        ItemStack off = player.getHeldItem(EnumHand.OFF_HAND);
        if (!off.isEmpty() && off.getItem() instanceof CreepCubeItem
                && ((CreepCubeItem) off.getItem()).isMiniCube() == miniCube) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
        if (!worldIn.isRemote && entityIn instanceof EntityPlayer) {
            getCubeId(stack, (EntityPlayer) entityIn);
        }
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {
        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(getRegistryName(), "inventory"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        Collections.addAll(tooltip, StringUtils.split(I18n.format("message.meecreeps.tooltip.cube_intro"), "\n"));
        MeeCreepActionType lastAction = getLastAction(stack);
        if (lastAction != null) {
            MeeCreepsApi.Factory factory = MeeCreeps.api.getFactory(lastAction);
            if (factory != null) {
                tooltip.add(TextFormatting.YELLOW + "    (" + I18n.format(factory.getMessage()) + ")");
            }
        }
        if (isLimited()) {
            int remaining = Math.max(0, ConfigSetup.meeCreepBoxMaxUsage.get() - getUsages(stack));
            Collections.addAll(tooltip, StringUtils.split(I18n.format("message.meecreeps.tooltip.cube_uses", Integer.toString(remaining)), "\n"));
        }
    }

    public static void setLastAction(ItemStack cube, MeeCreepActionType type, @Nullable String furtherQuestionId) {
        if (cube.getTagCompound() == null) {
            cube.setTagCompound(new NBTTagCompound());
        }
        cube.getTagCompound().setString("lastType", type.getId());
        if (furtherQuestionId != null) {
            cube.getTagCompound().setString("lastQuestion", furtherQuestionId);
        } else {
            cube.getTagCompound().removeTag("lastQuestion");
        }
        getCubeId(cube);
    }

    @Nullable
    public static MeeCreepActionType getLastAction(ItemStack cube) {
        if (cube.getTagCompound() == null || !cube.getTagCompound().hasKey("lastType")) {
            return null;
        }
        return new MeeCreepActionType(cube.getTagCompound().getString("lastType"));
    }

    @Nullable
    public static String getLastQuestionId(ItemStack cube) {
        return cube.getTagCompound() == null || !cube.getTagCompound().hasKey("lastQuestion")
                ? null : cube.getTagCompound().getString("lastQuestion");
    }

    public static void setUsages(ItemStack stack, int uses) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setInteger("uses", uses);
    }

    public static int getUsages(ItemStack stack) {
        return stack.getTagCompound() == null ? 0 : stack.getTagCompound().getInteger("uses");
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return isLimited();
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        int max = ConfigSetup.meeCreepBoxMaxUsage.get();
        if (max <= 0) return 0.0D;
        return Math.max(0, Math.min(getUsages(stack), max)) / (double) max;
    }

    private boolean isLimited() {
        return ConfigSetup.meeCreepBoxMaxUsage.get() > 0;
    }

    private boolean canUseCube(ItemStack stack, EntityPlayer player) {
        if (isLimited() && getUsages(stack) >= ConfigSetup.meeCreepBoxMaxUsage.get()) {
            MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.box_unusable"), (EntityPlayerMP) player);
            return false;
        }
        if (ConfigSetup.maxMeecreepsPerPlayer.get() >= 0
                && ServerActionManager.getManager().countMeeCreeps(player) >= ConfigSetup.maxMeecreepsPerPlayer.get()) {
            MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.max_spawn_reached",
                    Integer.toString(ConfigSetup.maxMeecreepsPerPlayer.get())), (EntityPlayerMP) player);
            return false;
        }
        return true;
    }

    public static void handleLeftClick(EntityPlayerMP player, BlockPos pos, EnumFacing side, UUID expectedCubeId) {
        ItemStack cube = getHeldCube(player, expectedCubeId);
        if (cube.isEmpty() || pos == null) return;

        World world = player.getEntityWorld();
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof HeldCubeBlock) {
            if (player.isSneaking()) {
                HeldCubeBlock.reclaim(world, pos, player);
            }
            return;
        }
        if (!state.getMaterial().isSolid()) return;

        CreepCubeItem item = (CreepCubeItem) cube.getItem();
        if (player.isSneaking()) {
            placeCube(player, cube, item, world, pos, state);
            return;
        }
        item.repeatLastAction(cube, player, world, pos, side);
    }

    public static void handleRightClick(EntityPlayerMP player, BlockPos pos, EnumFacing side, UUID expectedCubeId) {
        ItemStack cube = getHeldCube(player, expectedCubeId);
        if (cube.isEmpty() || pos == null) return;

        World world = player.getEntityWorld();
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof HeldCubeBlock) {
            if (player.isSneaking()) {
                HeldCubeBlock.reclaim(world, pos, player);
            }
            return;
        }
        if (!state.getMaterial().isSolid()) return;

        CreepCubeItem item = (CreepCubeItem) cube.getItem();
        if (player.isSneaking()) {
            placeCube(player, cube, item, world, pos, state);
            return;
        }
        if (!item.canUseCube(cube, player)) return;
        ServerActionManager.getManager().createActionOptions(world, pos, side, player, item.isMiniCube(), getCubeId(cube, player));
        if (item.isLimited()) {
            setUsages(cube, getUsages(cube) + 1);
        }
    }

    private static boolean placeCube(EntityPlayerMP player, ItemStack cube, CreepCubeItem item,
                                     World world, BlockPos pos, IBlockState state) {
        if (!player.isSneaking()) return false;
        if (!state.isSideSolid(world, pos, EnumFacing.UP)) return false;
        BlockPos placePos = pos.up();
        if (!world.isAirBlock(placePos)) return false;

        HeldCubeBlock block = item.isMiniCube() ? ModBlocks.miniCubeBlock : ModBlocks.heldCubeBlock;
        if (block == null || !world.setBlockState(placePos, block.getDefaultState(), 3)) return false;

        TileEntity tile = world.getTileEntity(placePos);
        if (!(tile instanceof CubeTileEntity)) {
            tile = new CubeTileEntity();
            world.setTileEntity(placePos, tile);
        }

        ItemStack stored = cube.copy();
        if (player.capabilities.isCreativeMode) {
            if (stored.getTagCompound() == null) stored.setTagCompound(new NBTTagCompound());
            stored.getTagCompound().setUniqueId("meecreepsCubeId", UUID.randomUUID());
        } else {
            cube.shrink(1);
        }
        ((CubeTileEntity) tile).setCube(stored);
        return true;
    }

    private boolean repeatLastAction(ItemStack cube, EntityPlayer player, World world, BlockPos pos, EnumFacing side) {
        if (!canUseCube(cube, player)) return true;
        MeeCreepActionType action = getLastAction(cube);
        if (action == null) {
            MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.no_last_action"), (EntityPlayerMP) player);
            return true;
        }
        MeeCreepsApi.Factory factory = MeeCreeps.api.getFactory(action);
        if (factory == null || (!factory.getFactory().isPossible(world, pos, side)
                && !factory.getFactory().isPossibleSecondary(world, pos, side))) {
            MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.last_action_not_possible"), (EntityPlayerMP) player);
            return true;
        }
        if (MeeCreeps.api.spawnMeeCreep(action.getId(), getLastQuestionId(cube), world, pos, side,
                (EntityPlayerMP) player, false, miniCube, getCubeId(cube, player)) && isLimited()) {
            setUsages(cube, getUsages(cube) + 1);
        }
        return true;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        IBlockState state = world.getBlockState(pos);
        if (!state.getMaterial().isSolid()) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            ItemStack cube = player.getHeldItem(hand);
            UUID cubeId = getCubeId(cube, player);
            if (cubeId != null) {
                MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID,
                        CommandHandler.CMD_CUBE_RIGHT_CLICK, TypedMap.builder()
                        .put(CommandHandler.PARAM_POS, pos).put(CommandHandler.PARAM_SIDE, side.ordinal())
                        .put(CommandHandler.PARAM_CUBE_UUID, cubeId.toString()).build()));
            }
        }
        return EnumActionResult.SUCCESS;
    }


    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        return 0.0F;
    }

    @Override
    public boolean canHarvestBlock(IBlockState state) {
        return false;
    }

    @Override
    public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player) {
        return false;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, EntityPlayer player) {
        return true;
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return true;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand hand) {
        return new ActionResult<>(EnumActionResult.PASS, playerIn.getHeldItem(hand));
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity target) {
        if (!(target instanceof mcjty.meecreeps.entities.EntityMeeCreeps)) return false;
        if (player.getEntityWorld().isRemote) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID,
                    CommandHandler.CMD_TERMINATE_ENTITY, TypedMap.builder()
                    .put(CommandHandler.PARAM_ENTITY_ID, target.getEntityId())
                    .put(CommandHandler.PARAM_CUBE_UUID, getCubeId(stack, player).toString()).build()));
        } else if (player instanceof EntityPlayerMP) {
            ServerActionManager.getManager().terminateMeeCreep((EntityPlayerMP) player,
                    (mcjty.meecreeps.entities.EntityMeeCreeps) target, getCubeId(stack, player));
        }
        return true;
    }
}
