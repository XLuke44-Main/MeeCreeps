package mcjty.meecreeps.items;

import mcjty.lib.network.PacketSendServerCommand;
import mcjty.lib.typed.TypedMap;
import mcjty.meecreeps.CommandHandler;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.actions.PacketShowBalloonToClient;
import mcjty.meecreeps.blocks.ModBlocks;
import mcjty.meecreeps.blocks.PortalTileEntity;
import mcjty.meecreeps.entities.EntityProjectile;
import mcjty.meecreeps.gui.GuiWheel;
import mcjty.meecreeps.network.MeeCreepsMessages;
import mcjty.meecreeps.setup.GuiProxy;
import mcjty.meecreeps.teleport.TeleportDestination;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PortalGunItem extends Item {

    public static final int RING_LEFT = 0;
    public static final int RING_CENTER = 1;
    public static final int RING_RIGHT = 2;
    public static final int SLOTS_PER_RING = 8;
    public static final String INTRADIMENSIONAL_DIMENSION_ERROR = "INTRADIMENSIONAL TRANSPORT SOLUTION CANNOT TRAVEL TO ANOTHER DIMENSION!";
    public static final String INTRADIMENSIONAL_PLAYER_DIMENSION_ERROR = "THE TARGET PLAYER IS IN ANOTHER DIMENSION!";
    public static final String INTRADIMENSIONAL_DIMENSION_LIST_ERROR = "INTRADIMENSIONAL TRANSPORT SOLUTION IS NOT COMPATIBLE WITH INTERDIMENSIONAL TRAVEL!";

    private static final Map<UUID, CustomDestination> CUSTOM_DESTINATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, TeleportDestination> FAST_RETURN_OVERRIDES = new ConcurrentHashMap<>();

    public PortalGunItem() {
        setRegistryName("portalgun");
        setUnlocalizedName(MeeCreeps.MODID + ".portalgun");
        setMaxStackSize(1);
        setCreativeTab(MeeCreeps.setup.getTab());
    }

    public static ItemStack getGun(EntityPlayer player) {
        ItemStack heldItem = player.getHeldItem(EnumHand.MAIN_HAND);
        if (heldItem.getItem() != ModItems.portalGunItem) {
            heldItem = player.getHeldItem(EnumHand.OFF_HAND);
            if (heldItem.getItem() != ModItems.portalGunItem) {
                return ItemStack.EMPTY;
            }
        }
        return heldItem;
    }

    public static UUID getGunId(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasUniqueId("gunId")) tag.setUniqueId("gunId", UUID.randomUUID());
        return tag.getUniqueId("gunId");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return super.getItemStackDisplayName(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        TransportSolutionType type = getTransportSolutionType(stack);
        String solutionColor = type.getFormatting().toString();
        Collections.addAll(tooltip, StringUtils.split(
                I18n.format("message.meecreeps.tooltip.portalgun", solutionColor, Integer.toString(getCharge(stack))),
                "\n"));
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {
        ModelLoader.setCustomModelResourceLocation(this, 0,
                new net.minecraft.client.renderer.block.model.ModelResourceLocation(getRegistryName(), "inventory"));
        addPropertyOverride(new net.minecraft.util.ResourceLocation(MeeCreeps.MODID, "portal_solution"),
                new IItemPropertyGetter() {
                    @Override
                    public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
                        // Model predicates are intentionally 0=interdimensional, 1=intradimensional,
                        // 2=extradimensional, 3=hyperdimensional.
                        // Do not use a filled/empty state here: the gun model depends only on the installed solution.
                        switch (CartridgeItem.getTransportSolutionType(stack)) {
                            case INTRADIMENSIONAL: return 1.0F;
                            case EXTRADIMENSIONAL: return 2.0F;
                            case HYPERDIMENSIONAL: return 3.0F;
                            case INTERDIMENSIONAL:
                            default: return 0.0F;
                        }
                    }
                });
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(net.minecraft.creativetab.CreativeTabs tab, net.minecraft.util.NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;

        ItemStack emptyGun = new ItemStack(this);
        setTransportSolutionType(emptyGun, TransportSolutionType.INTERDIMENSIONAL);
        setCharge(emptyGun, 0);
        items.add(emptyGun);

        for (TransportSolutionType type : TransportSolutionType.values()) {
            ItemStack filledGun = new ItemStack(this);
            setTransportSolutionType(filledGun, type);
            setCharge(filledGun, CartridgeItem.getMaxCharge(type));
            items.add(filledGun);
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) {
            if (world.getBlockState(pos.offset(side)).getBlock() == ModBlocks.portalBlock) {
                MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID, CommandHandler.CMD_CANCEL_PORTAL, TypedMap.builder().put(CommandHandler.PARAM_POS, pos.offset(side)).build()));
                return EnumActionResult.SUCCESS;
            }
            if (side != EnumFacing.UP && side != EnumFacing.DOWN && world.getBlockState(pos.offset(side).down()).getBlock() == ModBlocks.portalBlock) {
                MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID, CommandHandler.CMD_CANCEL_PORTAL, TypedMap.builder().put(CommandHandler.PARAM_POS, pos.offset(side).down()).build()));
                return EnumActionResult.SUCCESS;
            }
            if (player.isSneaking()) {
                GuiWheel.selectedBlock = pos;
                GuiWheel.selectedSide = side;
                player.openGui(MeeCreeps.instance, GuiProxy.GUI_WHEEL, world, pos.getX(), pos.getY(), pos.getZ());
            }
            return EnumActionResult.SUCCESS;
        } else {
            if (world.getBlockState(pos.offset(side)).getBlock() == ModBlocks.portalBlock) return EnumActionResult.SUCCESS;
            if (side != EnumFacing.UP && side != EnumFacing.DOWN && world.getBlockState(pos.offset(side).down()).getBlock() == ModBlocks.portalBlock) return EnumActionResult.SUCCESS;
            if (!player.isSneaking()) throwProjectile(player, hand, world);
        }
        return EnumActionResult.SUCCESS;
    }

    private void throwProjectile(EntityPlayer player, EnumHand hand, World world) {
        ItemStack heldItem = player.getHeldItem(hand);
        int charge = getCharge(heldItem);
        if (charge <= 0) {
            MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.gun_no_charge"), (EntityPlayerMP) player);
            return;
        }

        TeleportDestination destination = getDestinationForUse(heldItem);
        if (destination == null) {
            MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.gun_no_destination"), (EntityPlayerMP) player);
            return;
        }
        if (!DimensionManager.isDimensionRegistered(destination.getDimension()) || mcjty.meecreeps.teleport.TeleportationTools.resolveDestinationWorld(destination.getDimension()) == null) {
            sendError(player, "INVALID DESTINATION DIMENSION!");
            clearFastReturnOverride(heldItem);
            return;
        }
        if (!isDestinationAllowed(heldItem, player.getEntityWorld(), destination)) {
            sendIntradimensionalDimensionError(player);
            clearFastReturnOverride(heldItem);
            return;
        }

        setCharge(heldItem, charge - 1);
        EntityProjectile projectile = new EntityProjectile(world, player);
        projectile.setDestination(destination);
        projectile.setPlayerId(player.getUniqueID());
        projectile.setGunId(getGunId(heldItem));
        projectile.setAutoClose(isAutoCloseEnabled(heldItem));
        projectile.setTransportSolutionType(getTransportSolutionType(heldItem));
        projectile.setAirPlacementEnabled(isAirPlacementEnabled(heldItem));
        projectile.setOrigin(player.posX, player.posZ);
        projectile.shoot(player, player.rotationPitch, player.rotationYaw, 0.0F, 1.5F, 1.0F);
        world.spawnEntity(projectile);
    }

    private static void sendError(EntityPlayer player, String text) {
        sendError(player, text, net.minecraft.util.text.TextFormatting.RED);
    }

    private static void sendError(EntityPlayer player, String text, net.minecraft.util.text.TextFormatting color) {
        player.sendMessage(new net.minecraft.util.text.TextComponentString(text).setStyle(new net.minecraft.util.text.Style().setColor(color)));
    }

    public static void setTransportSolutionType(ItemStack stack, TransportSolutionType type) {
        CartridgeItem.setTransportSolutionType(stack, type);
    }

    public static TransportSolutionType getTransportSolutionType(ItemStack stack) {
        return CartridgeItem.getTransportSolutionType(stack);
    }

    public static boolean isIntradimensionalActive(ItemStack stack) {
        return getTransportSolutionType(stack) == TransportSolutionType.INTRADIMENSIONAL && getCharge(stack) > 0;
    }

    public static boolean isDestinationAllowed(ItemStack stack, World currentWorld, TeleportDestination destination) {
        return !isIntradimensionalActive(stack) || currentWorld == null || destination == null
                || currentWorld.provider.getDimension() == destination.getDimension();
    }

    public static boolean sendDimensionErrorIfNeeded(EntityPlayer player, ItemStack stack, TeleportDestination destination) {
        if (player != null && !isDestinationAllowed(stack, player.getEntityWorld(), destination)) {
            sendIntradimensionalDimensionError(player);
            return true;
        }
        return false;
    }

    public static void sendIntradimensionalDimensionError(EntityPlayer player) {
        sendError(player, INTRADIMENSIONAL_DIMENSION_ERROR, net.minecraft.util.text.TextFormatting.BLUE);
    }

    /** Backwards-compatible API: the original single destination ring is the center ring. */
    public static void addDestination(ItemStack stack, @Nullable TeleportDestination destination, int destinationIndex) {
        addDestination(stack, destination, RING_CENTER, destinationIndex);
    }

    public static void addDestinationEncoded(ItemStack stack, @Nullable TeleportDestination destination, int encodedIndex) {
        int ring = encodedIndex / SLOTS_PER_RING;
        int slot = encodedIndex % SLOTS_PER_RING;
        addDestination(stack, destination, ring, slot);
    }

    public static void addDestination(ItemStack stack, @Nullable TeleportDestination destination, int ring, int slot) {
        if (ring < 0 || ring >= 3 || slot < 0 || slot >= SLOTS_PER_RING) return;
        if (destination != null && !isValidSavedDestination(destination)) return;
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        List<TeleportDestination> destinations = getDestinations(stack, ring);
        destinations.set(slot, destination);
        setDestinations(stack, destinations, ring);
        int encoded = ring * SLOTS_PER_RING + slot;
        UUID id = getGunId(stack);
        if (destination != null) {
            // A saved destination written explicitly replaces any temporary custom override.
            CUSTOM_DESTINATIONS.remove(id);
            setCurrentDestination(stack, encoded);
        } else {
            CustomDestination custom = CUSTOM_DESTINATIONS.get(id);
            if (custom != null && custom.index == encoded) CUSTOM_DESTINATIONS.remove(id);
        }
    }

    private static boolean isValidSavedDestination(TeleportDestination destination) {
        // Client-side destination wheels must still be able to display saved entries for
        // registered dimensions. Server-side selection/use performs the stricter availability
        // check before the destination can actually be used.
        return destination != null && destination.getPos() != null && destination.getSide() != null
                && DimensionManager.isDimensionRegistered(destination.getDimension());
    }

    private static void setDestinations(ItemStack stack, List<TeleportDestination> destinations, int ring) {
        NBTTagList dests = new NBTTagList();
        for (TeleportDestination destination : destinations) dests.appendTag(destination == null ? new NBTTagCompound() : destination.getCompound());
        stack.getTagCompound().setTag(getRingKey(ring), dests);
    }

    private static String getRingKey(int ring) {
        if (ring == RING_LEFT) return "dests_left";
        if (ring == RING_RIGHT) return "dests_right";
        return "dests";
    }

    public static int getCurrentDestination(ItemStack stack) {
        UUID id = getGunId(stack);
        CustomDestination custom = CUSTOM_DESTINATIONS.get(id);
        if (custom != null) return custom.index;
        if (!stack.hasTagCompound()) return -1;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey("currentRing")) {
            int ring = tag.getInteger("currentRing");
            int slot = tag.getInteger("currentSlot");
            if (ring < 0 || ring >= 3 || slot < 0 || slot >= SLOTS_PER_RING) return -1;
            return ring * SLOTS_PER_RING + slot;
        }
        if (!tag.hasKey("destination")) return -1;
        // Legacy forks stored the original center ring as slots 0..7.
        int slot = tag.getInteger("destination");
        return slot >= 0 && slot < SLOTS_PER_RING ? RING_CENTER * SLOTS_PER_RING + slot : -1;
    }

    public static void setCurrentDestination(ItemStack stack, int dest) {
        if (dest < 0 || dest >= 24) return;
        int ring = dest / SLOTS_PER_RING;
        int slot = dest % SLOTS_PER_RING;
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey("currentRing")) {
            // Preserve legacy API behavior for IDs 0..7 when only the original center ring exists.
            if (dest < SLOTS_PER_RING && getDestinations(stack, RING_LEFT).get(dest) == null && getDestinations(stack, RING_CENTER).get(dest) != null) {
                ring = RING_CENTER;
                slot = dest;
                dest = RING_CENTER * SLOTS_PER_RING + slot;
            }
        }
        TeleportDestination saved = getDestinations(stack, ring).get(slot);
        UUID id = getGunId(stack);
        CustomDestination custom = CUSTOM_DESTINATIONS.get(id);
        if (saved == null && (custom == null || custom.index != dest)) return;
        // Choosing a saved destination explicitly cancels one-shot overrides.
        FAST_RETURN_OVERRIDES.remove(id);
        CUSTOM_DESTINATIONS.remove(id);
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger("destination", dest);
        stack.getTagCompound().setInteger("currentRing", ring);
        stack.getTagCompound().setInteger("currentSlot", slot);
    }

    public static List<TeleportDestination> getDestinations(ItemStack stack) {
        return getDestinations(stack, RING_CENTER);
    }

    public static List<TeleportDestination> getDestinations(ItemStack stack, int ring) {
        List<TeleportDestination> destinations = new ArrayList<>();
        for (int i = 0; i < SLOTS_PER_RING; i++) destinations.add(null);
        if (!stack.hasTagCompound()) return destinations;
        NBTTagList dests = stack.getTagCompound().getTagList(getRingKey(ring), Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < SLOTS_PER_RING && i < dests.tagCount(); i++) {
            NBTTagCompound tc = dests.getCompoundTagAt(i);
            if (tc.hasKey("dim")) {
                TeleportDestination destination = new TeleportDestination(tc);
                if (isValidSavedDestination(destination)) destinations.set(i, destination);
            }
        }
        return destinations;
    }

    public static void setCustomDestination(ItemStack stack, int index, TeleportDestination destination) {
        if (index < 0 || index >= 24) return;
        if (destination == null || !isValidSavedDestination(destination)) return;
        UUID id = getGunId(stack);
        CUSTOM_DESTINATIONS.put(id, new CustomDestination(index, destination));
        FAST_RETURN_OVERRIDES.remove(id);
        setCurrentDestinationForCustom(stack, index);
    }

    private static void setCurrentDestinationForCustom(ItemStack stack, int dest) {
        // Custom destinations are intentionally runtime-only. Do not write either the
        // destination or the selected ring/slot into the persistent saved-destination NBT.
    }

    public static void setFastReturnOverride(ItemStack stack, TeleportDestination destination) {
        if (destination == null || !isValidSavedDestination(destination)) return;
        FAST_RETURN_OVERRIDES.put(getGunId(stack), destination);
    }

    public static TeleportDestination getDestinationForUse(ItemStack stack) {
        UUID id = getGunId(stack);
        TeleportDestination override = FAST_RETURN_OVERRIDES.get(id);
        if (override != null) return override;

        int current = getCurrentDestination(stack);
        if (current < 0 || current >= 24) return null;
        CustomDestination custom = CUSTOM_DESTINATIONS.get(id);
        if (custom != null && custom.index == current) return custom.destination;
        return getDestinations(stack, current / SLOTS_PER_RING).get(current % SLOTS_PER_RING);
    }

    public static void clearOneShotDestination(ItemStack stack) {
        UUID id = getGunId(stack);
        FAST_RETURN_OVERRIDES.remove(id);
        CustomDestination custom = CUSTOM_DESTINATIONS.get(id);
        if (custom != null && custom.index == getCurrentDestination(stack)) CUSTOM_DESTINATIONS.remove(id);
    }

    public static void clearFastReturnOverride(ItemStack stack) {
        FAST_RETURN_OVERRIDES.remove(getGunId(stack));
    }

    public static void setAutoCloseEnabled(ItemStack stack, boolean enabled) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean("autoClose", enabled);
    }

    public static boolean isAutoCloseEnabled(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey("autoClose") ? stack.getTagCompound().getBoolean("autoClose") : true;
    }

    public static boolean toggleAutoClose(ItemStack stack) {
        boolean enabled = !isAutoCloseEnabled(stack);
        setAutoCloseEnabled(stack, enabled);
        return enabled;
    }

    public static void setAirPlacementEnabled(ItemStack stack, boolean enabled) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean("airPlacement", enabled);
    }

    public static boolean isAirPlacementEnabled(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean("airPlacement");
    }

    public static boolean toggleAirPlacement(ItemStack stack) {
        boolean enabled = !isAirPlacementEnabled(stack);
        setAirPlacementEnabled(stack, enabled);
        return enabled;
    }

    public static void setCharge(ItemStack stack, int charge) {
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger("charge", charge);
    }

    public static int getCharge(ItemStack stack) {
        return stack.getTagCompound() == null ? 0 : stack.getTagCompound().getInteger("charge");
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) { return true; }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        int max = CartridgeItem.getMaxCharge(getTransportSolutionType(stack));
        int stored = getCharge(stack);
        return (max - stored) / (double) max;
    }

    @Override
    public boolean hasContainerItem(ItemStack stack) { return true; }

    @Override
    public Item getContainerItem() { return ModItems.emptyPortalGunItem; }

    @Override
    public ItemStack getContainerItem(ItemStack itemStack) {
        ItemStack stack = new ItemStack(ModItems.emptyPortalGunItem);
        if (itemStack.hasTagCompound()) {
            stack.setTagCompound(itemStack.getTagCompound().copy());
        }
        return stack;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) { return EnumActionResult.SUCCESS; }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (player.isSneaking()) {
            if (world.isRemote) {
                BlockPos pos = player.getPosition().down();
                GuiWheel.selectedBlock = pos;
                GuiWheel.selectedSide = EnumFacing.UP;
                player.openGui(MeeCreeps.instance, GuiProxy.GUI_WHEEL, world, pos.getX(), pos.getY(), pos.getZ());
            }
        } else if (!world.isRemote) {
            throwProjectile(player, hand, world);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    public static void closeAllPortals(ItemStack stack) {
        UUID gunId = getGunId(stack);
        for (World world : DimensionManager.getWorlds()) {
            if (world == null) continue;
            List<PortalTileEntity> portals = new ArrayList<>();
            for (net.minecraft.tileentity.TileEntity te : world.loadedTileEntityList) {
                if (te instanceof PortalTileEntity && gunId.equals(((PortalTileEntity) te).getGunId())) portals.add((PortalTileEntity) te);
            }
            for (PortalTileEntity portal : portals) portal.forceManualClose();
        }
    }

    private static class CustomDestination {
        private final int index;
        private final TeleportDestination destination;
        private CustomDestination(int index, TeleportDestination destination) { this.index = index; this.destination = destination; }
    }
}
