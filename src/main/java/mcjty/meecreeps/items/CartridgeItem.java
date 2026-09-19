package mcjty.meecreeps.items;

import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.actions.PacketShowBalloonToClient;
import mcjty.meecreeps.config.ConfigSetup;
import mcjty.meecreeps.network.MeeCreepsMessages;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.block.material.Material;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class CartridgeItem extends Item {

    public CartridgeItem() {
        setRegistryName("cartridge");
        setUnlocalizedName(MeeCreeps.MODID + ".cartridge");
        setMaxStackSize(1);
        setCreativeTab(MeeCreeps.setup.getTab());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        Collections.addAll(tooltip, StringUtils.split(I18n.translateToLocalFormatted("message.meecreeps.tooltip." + getTransportSolutionType(stack).getName() + "_cartridge_item", Integer.toString(getCharge(stack))), "\n"));
    }


    @SideOnly(Side.CLIENT)
    public void initModel() {
        ModelLoader.setCustomModelResourceLocation(this, 0,
                new net.minecraft.client.renderer.block.model.ModelResourceLocation(getRegistryName(), "inventory"));
        addPropertyOverride(new net.minecraft.util.ResourceLocation(MeeCreeps.MODID, "solution_state"),
                new IItemPropertyGetter() {
                    @Override
                    public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
                        return getSolutionState(stack);
                    }
                });
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(net.minecraft.creativetab.CreativeTabs tab, net.minecraft.util.NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;

        for (TransportSolutionType type : TransportSolutionType.values()) {
            ItemStack emptyCartridge = new ItemStack(this);
            setTransportSolutionType(emptyCartridge, type);
            setCharge(emptyCartridge, 0);
            items.add(emptyCartridge);

            ItemStack filledCartridge = new ItemStack(this);
            setTransportSolutionType(filledCartridge, type);
            setCharge(filledCartridge, getMaxCharge(type));
            items.add(filledCartridge);
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        TransportSolutionType type = getTransportSolutionType(stack);
        String prefix = getCharge(stack) > 0 ? "" : "empty_";
        // getItemStackDisplayName() is also called on a dedicated server while Forge
        // rebuilds item/stat data. Do not use client-only net.minecraft.client.resources.I18n here.
        return I18n.translateToLocal("item.meecreeps." + prefix + type.getName() + "_cartridge.name").trim();
    }

    public static void setTransportSolutionType(ItemStack stack, TransportSolutionType type) {
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        TransportSolutionType.writeToNBT(stack.getTagCompound(), type);
    }

    public static TransportSolutionType getTransportSolutionType(ItemStack stack) {
        return TransportSolutionType.fromNBT(stack == null || !stack.hasTagCompound() ? null : stack.getTagCompound());
    }

    public static int getMaxCharge(TransportSolutionType type) {
        switch (type) {
            case INTRADIMENSIONAL: return ConfigSetup.intradimensionalMaxCharge.get();
            case EXTRADIMENSIONAL: return ConfigSetup.extradimensionalMaxCharge.get();
            case HYPERDIMENSIONAL: return ConfigSetup.hyperdimensionalMaxCharge.get();
            case INTERDIMENSIONAL:
            default: return ConfigSetup.interdimensionalMaxCharge.get();
        }
    }

    public static int getRefillAmount(TransportSolutionType type) {
        switch (type) {
            case INTRADIMENSIONAL: return ConfigSetup.intradimensionalRefillAmount.get();
            case EXTRADIMENSIONAL: return ConfigSetup.extradimensionalRefillAmount.get();
            case HYPERDIMENSIONAL: return ConfigSetup.hyperdimensionalRefillAmount.get();
            case INTERDIMENSIONAL:
            default: return ConfigSetup.interdimensionalRefillAmount.get();
        }
    }

    public static float getSolutionState(ItemStack stack) {
        TransportSolutionType type = getTransportSolutionType(stack);
        return type.getId() * 2.0F + (getCharge(stack) > 0 ? 1.0F : 0.0F);
    }

    public static void setCharge(ItemStack stack, int charge) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setInteger("charge", charge);
    }

    public static int getCharge(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            return 0;
        }
        return stack.getTagCompound().getInteger("charge");
    }


    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        int max = getMaxCharge(getTransportSolutionType(stack));
        int stored = getCharge(stack);
        return (max - stored) / (double) max;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!world.isRemote) {
            chargeCartridge(player, world, pos, side, hand);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            // A liquid block can be missed by vanilla's normal block-use ray trace
            // because it has no collision box. Perform our own ray trace that
            // explicitly stops on liquids so an open water source can be used even
            // when there is no solid block underneath it.
            BlockPos lookedAtWater = findWaterSourceInLook(world, player);
            chargeCartridge(player, world, lookedAtWater != null ? lookedAtWater : player.getPosition(), null, hand);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    private void chargeCartridge(EntityPlayer player, World world, BlockPos pos, @Nullable EnumFacing side, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        TransportSolutionType type = getTransportSolutionType(heldItem);
        int maxCharge = getMaxCharge(type);
        int charge = getCharge(heldItem);
        if (charge >= maxCharge) {
            MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.cartridge_full"), (EntityPlayerMP) player);
            return;
        }

        switch (type) {
            case INTRADIMENSIONAL:
                if (isWaterSource(world, pos) || (side != null && isWaterSource(world, pos.offset(side)))) {
                    setCharge(heldItem, Math.min(maxCharge, charge + getRefillAmount(type)));
                    return;
                }
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack stack = player.inventory.getStackInSlot(i);
                    if (isIntradimensionalRefillContainer(stack)) {
                        ItemStack remainder = getIntradimensionalRefillRemainder(stack);
                        stack.shrink(1);
                        player.inventory.setInventorySlotContents(i, remainder);
                        setCharge(heldItem, Math.min(maxCharge, charge + getRefillAmount(type)));
                        return;
                    }
                }
                MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.missing_intradimensional_refill"), (EntityPlayerMP) player);
                return;

            case EXTRADIMENSIONAL:
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack stack = player.inventory.getStackInSlot(i);
                    if (stack.getItem() == Items.GOLD_INGOT) {
                        stack.splitStack(1);
                        setCharge(heldItem, Math.min(maxCharge, charge + getRefillAmount(type)));
                        return;
                    }
                }
                MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.missing_gold_ingots"), (EntityPlayerMP) player);
                return;

            case HYPERDIMENSIONAL:
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack stack = player.inventory.getStackInSlot(i);
                    if (stack.getItem() == Items.REDSTONE) {
                        stack.splitStack(1);
                        setCharge(heldItem, Math.min(maxCharge, charge + getRefillAmount(type)));
                        return;
                    }
                }
                MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.missing_redstone"), (EntityPlayerMP) player);
                return;

            case INTERDIMENSIONAL:
            default:
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack stack = player.inventory.getStackInSlot(i);
                    if (stack.getItem() == Items.ENDER_PEARL) {
                        stack.splitStack(1);
                        setCharge(heldItem, Math.min(maxCharge, charge + getRefillAmount(type)));
                        return;
                    }
                }
                MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.missing_enderpearls"), (EntityPlayerMP) player);
        }
    }

    /**
     * Returns true for vanilla refill items accepted by the intradimensional solution:
     * a water bucket or any regular, splash, or lingering potion.
     */
    public static boolean isIntradimensionalRefillContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        return item == Items.WATER_BUCKET
                || item == Items.POTIONITEM
                || item == Items.SPLASH_POTION
                || item == Items.LINGERING_POTION;
    }

    /**
     * Returns the empty container left behind after consuming an intradimensional refill item.
     */
    public static ItemStack getIntradimensionalRefillRemainder(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (stack.getItem() == Items.WATER_BUCKET) {
            return new ItemStack(Items.BUCKET);
        }

        if (isIntradimensionalRefillContainer(stack)) {
            return new ItemStack(Items.GLASS_BOTTLE);
        }

        return ItemStack.EMPTY;
    }

    private static boolean isWaterSource(World world, BlockPos pos) {
        return world.getBlockState(pos).getMaterial() == Material.WATER;
    }

    @Nullable
    private static BlockPos findWaterSourceInLook(World world, EntityPlayer player) {
        Vec3d start = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        double reach = player.capabilities.isCreativeMode ? 5.0D : 4.5D;

        Vec3d end = start.addVector(look.x * reach, look.y * reach, look.z * reach);
        RayTraceResult hit = world.rayTraceBlocks(start, end, true, false, false);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && isWaterSource(world, hit.getBlockPos())) {
            return hit.getBlockPos();
        }
        return null;
    }

}
