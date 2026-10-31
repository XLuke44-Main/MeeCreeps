package mcjty.meecreeps.items;

import mcjty.lib.network.PacketSendServerCommand;
import mcjty.lib.typed.TypedMap;
import mcjty.meecreeps.CommandHandler;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.actions.PacketShowBalloonToClient;
import mcjty.meecreeps.actions.PacketShowPortalSpawnerConfirmationToClient;
import mcjty.meecreeps.config.DimensionGroups;
import mcjty.meecreeps.blocks.ModBlocks;
import mcjty.meecreeps.blocks.PortalTileEntity;
import mcjty.meecreeps.blocks.PortalSpawnerBlock;
import mcjty.meecreeps.blocks.PortalSpawnerTileEntity;
import mcjty.meecreeps.entities.EntityProjectile;
import mcjty.meecreeps.gui.GuiWheel;
import mcjty.meecreeps.network.MeeCreepsMessages;
import mcjty.meecreeps.teleport.TeleportDestination;
import mcjty.meecreeps.teleport.TeleportationTools;
import mcjty.meecreeps.setup.GuiProxy;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
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
    public static final String BLACKLISTED_DIMENSION_ERROR = "\u00a77 THIS DIMENSION IS BLACKLISTED";
    public static final String INTRADIMENSIONAL_PLAYER_DIMENSION_ERROR = "THE TARGET PLAYER IS IN ANOTHER DIMENSION!";
    public static final String INTRADIMENSIONAL_DIMENSION_LIST_ERROR = "INTRADIMENSIONAL TRANSPORT SOLUTION IS NOT COMPATIBLE WITH INTERDIMENSIONAL TRAVEL!";

    private static final Map<UUID, CustomDestination> CUSTOM_DESTINATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, TeleportDestination> FAST_RETURN_OVERRIDES = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingSpawnerOperation> PENDING_SPAWNER_OPERATIONS = new ConcurrentHashMap<>();
    private static final long SPAWNER_CONFIRMATION_TIMEOUT_MS = 15000L;
    private static final String FAVOURITE_DESTINATION_TAG = "favouriteDestination";
    private static final String DUAL_LINKED_MODE_TAG = "dualLinkedMode";
    private static final String DUAL_PENDING_TAG = "dualPendingEndpoint";
    private static final String DUAL_PENDING_DIMENSION_TAG = "dualPendingDimension";
    private static final String DUAL_PENDING_POS_TAG = "dualPendingPos";
    private static final String DUAL_PENDING_SIDE_TAG = "dualPendingSide";
    private static final String DUAL_PENDING_PLAYER_TAG = "dualPendingPlayer";

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

    public static boolean isDualLinkedMode(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTagCompound()
                && stack.getTagCompound().getBoolean(DUAL_LINKED_MODE_TAG);
    }

    public static boolean toggleDualLinkedMode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        boolean enabled = !stack.getTagCompound().getBoolean(DUAL_LINKED_MODE_TAG);
        stack.getTagCompound().setBoolean(DUAL_LINKED_MODE_TAG, enabled);
        return enabled;
    }

    @Nullable
    private static TeleportDestination getPendingDualEndpoint(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.getBoolean(DUAL_PENDING_TAG) || !tag.hasKey(DUAL_PENDING_DIMENSION_TAG)
                || !tag.hasKey(DUAL_PENDING_POS_TAG) || !tag.hasKey(DUAL_PENDING_SIDE_TAG)) {
            return null;
        }
        int side = tag.getInteger(DUAL_PENDING_SIDE_TAG);
        if (side < 0 || side >= EnumFacing.VALUES.length) return null;
        return new TeleportDestination("", tag.getInteger(DUAL_PENDING_DIMENSION_TAG),
                BlockPos.fromLong(tag.getLong(DUAL_PENDING_POS_TAG)), EnumFacing.VALUES[side], false);
    }

    private static void setPendingDualEndpoint(ItemStack stack, int dimension, BlockPos pos,
                                               EnumFacing side, UUID playerId) {
        if (stack == null || stack.isEmpty() || pos == null || side == null || playerId == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        tag.setBoolean(DUAL_PENDING_TAG, true);
        tag.setInteger(DUAL_PENDING_DIMENSION_TAG, dimension);
        tag.setLong(DUAL_PENDING_POS_TAG, pos.toLong());
        tag.setInteger(DUAL_PENDING_SIDE_TAG, side.ordinal());
        tag.setUniqueId(DUAL_PENDING_PLAYER_TAG, playerId);
    }

    private static UUID getPendingDualPlayer(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTagCompound()
                && stack.getTagCompound().hasUniqueId(DUAL_PENDING_PLAYER_TAG)
                ? stack.getTagCompound().getUniqueId(DUAL_PENDING_PLAYER_TAG) : null;
    }

    private static void clearPendingDualEndpoint(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();
        tag.removeTag(DUAL_PENDING_TAG);
        tag.removeTag(DUAL_PENDING_DIMENSION_TAG);
        tag.removeTag(DUAL_PENDING_POS_TAG);
        tag.removeTag(DUAL_PENDING_SIDE_TAG);
        tag.removeTag(DUAL_PENDING_PLAYER_TAG);
    }

    @Nullable
    public static ItemStack findGunById(EntityPlayer player, UUID gunId) {
        if (player == null || gunId == null) return ItemStack.EMPTY;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.portalGunItem
                    && stack.hasTagCompound() && stack.getTagCompound().hasUniqueId("gunId")
                    && gunId.equals(stack.getTagCompound().getUniqueId("gunId"))) return stack;
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.portalGunItem
                    && stack.hasTagCompound() && stack.getTagCompound().hasUniqueId("gunId")
                    && gunId.equals(stack.getTagCompound().getUniqueId("gunId"))) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static boolean handleDualLinkedEndpointShot(EntityPlayer player, World shotWorld, UUID gunId,
                                                        BlockPos target, EnumFacing side,
                                                        boolean airPlacement, boolean autoClose,
                                                        TransportSolutionType transportSolutionType) {
        if (player == null || shotWorld == null || shotWorld.isRemote || shotWorld.provider == null
                || !(player instanceof EntityPlayerMP) || gunId == null || target == null || side == null) {
            return false;
        }

        ItemStack gun = findGunById(player, gunId);
        if (gun.isEmpty()) {
            return false;
        }

        UUID playerId = player.getUniqueID();
        TeleportDestination pending = getPendingDualEndpoint(gun);
        if (pending != null) {
            UUID pendingPlayer = getPendingDualPlayer(gun);
            if (pendingPlayer == null) {
                clearPendingDualEndpoint(gun);
                player.inventory.markDirty();
                pending = null;
            } else if (!pendingPlayer.equals(playerId)) {
                return false;
            }

            if (pending != null && !mcjty.meecreeps.teleport.TeleportationTools.isPendingDualLinkedEndpointValid(
                    pending, gunId, playerId)) {
                clearPendingDualEndpoint(gun);
                player.inventory.markDirty();
                pending = null;
            }
        }

        if (pending != null) {
            if (mcjty.meecreeps.teleport.TeleportationTools.linkDualLinkedEndpoints(
                    player, shotWorld, pending, target, side, airPlacement, gunId, playerId,
                    autoClose, transportSolutionType)) {
                clearPendingDualEndpoint(gun);
                player.inventory.markDirty();
                return true;
            }

            return false;
        }

        BlockPos portalPos = mcjty.meecreeps.teleport.TeleportationTools.placeDualLinkedEndpoint(
                shotWorld, target, side, airPlacement, gunId, playerId, transportSolutionType);
        if (portalPos == null) {
            return false;
        }

        setPendingDualEndpoint(gun, shotWorld.provider.getDimension(), portalPos, side, playerId);
        player.inventory.markDirty();
        return true;
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

        for (TransportSolutionType type : TransportSolutionType.values()) {
            ItemStack filledGun = new ItemStack(this);
            setTransportSolutionType(filledGun, type);
            setCharge(filledGun, CartridgeItem.getMaxCharge(type));
            items.add(filledGun);
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        PortalSpawnerTileEntity spawner = findPortalSpawner(world, pos);
        if (spawner != null) {
            if (!world.isRemote) {
                if (player.isSneaking()) {
                    requestSpawnerDownload(player, spawner, pos);
                } else {
                    requestSpawnerUpload(player, spawner, pos);
                }
            }
            return EnumActionResult.SUCCESS;
        }

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

    @Nullable
    private static PortalSpawnerTileEntity findPortalSpawner(World world, BlockPos clickedPos) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(clickedPos);
        if (te instanceof PortalSpawnerTileEntity) return (PortalSpawnerTileEntity) te;
        if (te instanceof PortalTileEntity) {
            BlockPos spawnerPos = ((PortalTileEntity) te).getSpawnerPos();
            if (spawnerPos != null) {
                net.minecraft.tileentity.TileEntity spawnerTe = world.getTileEntity(spawnerPos);
                if (spawnerTe instanceof PortalSpawnerTileEntity) return (PortalSpawnerTileEntity) spawnerTe;
            }
        }

        IBlockState clickedState = world.getBlockState(clickedPos);
        if (clickedState.getBlock() instanceof PortalSpawnerBlock
                && clickedState.getValue(PortalSpawnerBlock.HALF) == PortalSpawnerBlock.Half.LOWER) {
            te = world.getTileEntity(clickedPos.up());
            return te instanceof PortalSpawnerTileEntity ? (PortalSpawnerTileEntity) te : null;
        }
        return null;
    }

    private static void requestSpawnerUpload(EntityPlayer player, PortalSpawnerTileEntity spawner, BlockPos clickedPos) {
        ItemStack gun = getGun(player);
        if (gun.isEmpty()) return;
        if (!isValidSpawnerInteraction(player, spawner, clickedPos)) return;
        if (spawner.isLinked()) {
            sendTranslationError(player, "message.meecreeps.portal_spawner_linked_no_rebind");
            return;
        }
        if (spawner.hasActivePortalConnection()) {
            sendTranslationError(player, "message.meecreeps.portal_spawner_active_no_reconfigure");
            return;
        }

        int selectedIndex = getCurrentDestination(gun);
        TeleportDestination selected = getSelectedSavedDestination(gun);
        if (selectedIndex < 0 || selectedIndex >= 24 || selected == null) {
            sendTranslationError(player, "message.meecreeps.portal_spawner_no_destination");
            return;
        }
        if (!validateDestinationForPlayer(gun, player.getEntityWorld(), selected, player)) return;
        if (!DimensionGroups.isDestinationAllowed(spawner.getTransportSolutionType(),
                player.getEntityWorld().provider.getDimension(), selected.getDimension())) {
            sendTranslationError(player, "message.meecreeps.portal_spawner_incompatible");
            return;
        }

        PendingSpawnerOperation pending = createPendingOperation(player, gun, spawner,
                SpawnerOperation.UPLOAD_CONFIGURED_DESTINATION, selectedIndex, selected,
                spawner.getDestination(), null, clickedPos);
        String selectedText = describeDestination(selected);
        String targetText = describeSpawner(spawner);
        showSpawnerConfirmation(player, pending, "message.meecreeps.portal_spawner_upload_confirmation",
                selectedText, targetText);
    }

    private static void requestSpawnerDownload(EntityPlayer player, PortalSpawnerTileEntity spawner, BlockPos clickedPos) {
        ItemStack gun = getGun(player);
        if (gun.isEmpty()) return;
        if (!isValidSpawnerInteraction(player, spawner, clickedPos)) return;

        int selectedIndex = getCurrentDestination(gun);
        if (selectedIndex < 0 || selectedIndex >= 24) {
            sendTranslationError(player, "message.meecreeps.portal_spawner_no_selected_slot");
            return;
        }

        TeleportDestination physicalLocation = getSpawnerPhysicalLocation(spawner);
        if (physicalLocation == null || !validateDestinationForPlayer(gun, player.getEntityWorld(), physicalLocation, player)) {
            return;
        }
        TeleportDestination previousSlotValue = getSavedDestinationEncoded(gun, selectedIndex);

        PendingSpawnerOperation pending = createPendingOperation(player, gun, spawner,
                SpawnerOperation.DOWNLOAD_PHYSICAL_LOCATION, selectedIndex, physicalLocation,
                null, previousSlotValue, clickedPos);
        showSpawnerConfirmation(player, pending, "message.meecreeps.portal_spawner_download_confirmation",
                describeSpawner(spawner), describeSlot(selectedIndex));
    }

    private static boolean isValidSpawnerInteraction(EntityPlayer player, PortalSpawnerTileEntity spawner, BlockPos clickedPos) {
        if (player == null || spawner == null || clickedPos == null || player.world == null || spawner.getWorld() != player.world
                || spawner.isInvalid() || spawner.getWorld().isRemote
                || (!clickedPos.equals(spawner.getPos()) && !clickedPos.equals(spawner.getPos().down()))
                || !TeleportationTools.isWithinInteractionRange(player, clickedPos)) {
            if (player != null) sendTranslationError(player, "message.meecreeps.portal_spawner_invalid_target");
            return false;
        }
        IBlockState state = spawner.getWorld().getBlockState(spawner.getPos());
        if (!(state.getBlock() instanceof PortalSpawnerBlock)
                || state.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.UPPER) {
            sendTranslationError(player, "message.meecreeps.portal_spawner_invalid_target");
            return false;
        }
        return true;
    }

    private static PendingSpawnerOperation createPendingOperation(EntityPlayer player, ItemStack gun,
                                                                  PortalSpawnerTileEntity spawner,
                                                                  SpawnerOperation operation, int selectedIndex,
                                                                  TeleportDestination operationDestination,
                                                                  TeleportDestination previousSpawnerDestination,
                                                                  TeleportDestination previousGunSlotDestination,
                                                                  BlockPos clickedPos) {
        IBlockState state = spawner.getWorld().getBlockState(spawner.getPos());
        PendingSpawnerOperation pending = new PendingSpawnerOperation(
                UUID.randomUUID(), player.getUniqueID(), getGunId(gun), operation,
                spawner.getWorld().provider.getDimension(), spawner.getPos(),
                (PortalSpawnerBlock) state.getBlock(), state.getValue(PortalSpawnerBlock.FACING),
                spawner.isLinked(), selectedIndex, getDestinationSelectionRevision(gun),
                operationDestination, previousSpawnerDestination, previousGunSlotDestination,
                spawner, spawner.getDestinationRevision(), getDestinationSlotRevision(gun, selectedIndex),
                clickedPos, System.currentTimeMillis());
        PENDING_SPAWNER_OPERATIONS.put(player.getUniqueID(), pending);
        return pending;
    }

    private static void showSpawnerConfirmation(EntityPlayer player, PendingSpawnerOperation pending,
                                                 String message, String... parameters) {
        if (player instanceof EntityPlayerMP) {
            MeeCreepsMessages.INSTANCE.sendTo(
                    new PacketShowPortalSpawnerConfirmationToClient(message, pending.token, parameters),
                    (EntityPlayerMP) player);
        }
    }
    public static void tickPendingSpawnerOperation(EntityPlayerMP player) {
        if (player == null) return;
        PendingSpawnerOperation pending = PENDING_SPAWNER_OPERATIONS.get(player.getUniqueID());
        if (pending == null) return;

        boolean expired = System.currentTimeMillis() - pending.createdAt >= SPAWNER_CONFIRMATION_TIMEOUT_MS;
        boolean changedDimension = player.world == null
                || player.world.provider.getDimension() != pending.targetDimension;
        boolean movedAway = !changedDimension
                && !isWithinSpawnerConfirmationRange(player, pending.targetUpperPos.down());

        if ((expired || changedDimension || movedAway)
                && PENDING_SPAWNER_OPERATIONS.remove(player.getUniqueID(), pending)) {
            showBalloon(player, "message.meecreeps.portal_spawner_confirmation_stale");
        }
    }
    public static void clearPendingSpawnerOperation(EntityPlayer player) {
        if (player != null) PENDING_SPAWNER_OPERATIONS.remove(player.getUniqueID());
    }

    private static boolean isWithinSpawnerConfirmationRange(EntityPlayer player, BlockPos spawnerPos) {
        if (player == null || spawnerPos == null) return false;
        double dx = player.posX - (spawnerPos.getX() + 0.5D);
        double dy = player.posY - (spawnerPos.getY() + 0.5D);
        double dz = player.posZ - (spawnerPos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= 64.0D;
    }

    public static void handleSpawnerOperationConfirmation(EntityPlayerMP player, UUID token, boolean confirmed) {
        if (player == null || token == null) return;
        PendingSpawnerOperation pending = PENDING_SPAWNER_OPERATIONS.get(player.getUniqueID());
        if (pending == null || !pending.token.equals(token)) return;
        PENDING_SPAWNER_OPERATIONS.remove(player.getUniqueID(), pending);

        if (!confirmed) {
            showBalloon(player, "message.meecreeps.portal_spawner_operation_cancelled");
            return;
        }
        if (System.currentTimeMillis() - pending.createdAt >= SPAWNER_CONFIRMATION_TIMEOUT_MS
                || !pending.playerId.equals(player.getUniqueID())) {
            showBalloon(player, "message.meecreeps.portal_spawner_confirmation_stale");
            return;
        }

        if (player.world == null || player.world.provider.getDimension() != pending.targetDimension
                || !isWithinSpawnerConfirmationRange(player, pending.targetUpperPos.down())) {
            showBalloon(player, "message.meecreeps.portal_spawner_confirmation_stale");
            return;
        }
        player.world.getChunkFromBlockCoords(pending.targetUpperPos);
        IBlockState targetState = player.world.getBlockState(pending.targetUpperPos);
        net.minecraft.tileentity.TileEntity targetTe = player.world.getTileEntity(pending.targetUpperPos);
        BlockPos targetLowerPos = pending.targetUpperPos.down();
        IBlockState targetLowerState = player.world.getBlockState(targetLowerPos);
        boolean lowerStillBelongsToSpawner = targetLowerState.getBlock() == pending.targetBlock
                && targetLowerState.getValue(PortalSpawnerBlock.HALF) == PortalSpawnerBlock.Half.LOWER
                && targetLowerState.getValue(PortalSpawnerBlock.FACING) == pending.targetFacing;
        if (!lowerStillBelongsToSpawner && targetLowerState.getBlock() == ModBlocks.portalBlock) {
            net.minecraft.tileentity.TileEntity lowerTe = player.world.getTileEntity(targetLowerPos);
            lowerStillBelongsToSpawner = lowerTe instanceof PortalTileEntity
                    && pending.targetUpperPos.equals(((PortalTileEntity) lowerTe).getSpawnerPos());
        }
        if (targetState.getBlock() != pending.targetBlock
                || targetState.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.UPPER
                || targetState.getValue(PortalSpawnerBlock.FACING) != pending.targetFacing
                || !lowerStillBelongsToSpawner
                || !(targetTe instanceof PortalSpawnerTileEntity)) {
            showBalloon(player, "message.meecreeps.portal_spawner_confirmation_stale");
            return;
        }
        PortalSpawnerTileEntity spawner = (PortalSpawnerTileEntity) targetTe;
        if (spawner != pending.targetSpawner || spawner.isLinked() != pending.targetLinked) {
            showBalloon(player, "message.meecreeps.portal_spawner_confirmation_stale");
            return;
        }

        ItemStack gun = getGun(player);
        if (gun.isEmpty() || !getGunId(gun).equals(pending.gunId)
                || getCurrentDestination(gun) != pending.selectedIndex
                || getDestinationSelectionRevision(gun) != pending.selectionRevision
                || getDestinationSlotRevision(gun, pending.selectedIndex) != pending.slotRevision) {
            showBalloon(player, "message.meecreeps.portal_spawner_confirmation_stale");
            return;
        }

        if (pending.operation == SpawnerOperation.UPLOAD_CONFIGURED_DESTINATION) {
            if (spawner.hasActivePortalConnection()) {
                showBalloon(player, "message.meecreeps.portal_spawner_active_no_reconfigure");
                return;
            }
            TeleportDestination currentSelection = getSavedDestinationEncoded(gun, pending.selectedIndex);
            if (pending.targetLinked || spawner.hasActivePortalConnection()
                    || !sameDestination(currentSelection, pending.operationDestination)
                    || !sameDestination(spawner.getDestination(), pending.previousSpawnerDestination)
                    || spawner.getDestinationRevision() != pending.spawnerDestinationRevision
                    || pending.operationDestination == null
                    || !validateDestinationForPlayer(gun, player.world, pending.operationDestination, player)
                    || !DimensionGroups.isDestinationAllowed(spawner.getTransportSolutionType(),
                    pending.targetDimension, pending.operationDestination.getDimension())
                    || !spawner.setDestination(pending.operationDestination)) {
                showBalloon(player, "message.meecreeps.portal_spawner_confirmation_stale");
                return;
            }
            showBalloon(player, "message.meecreeps.portal_spawner_upload_success",
                    describeDestination(pending.operationDestination));
        } else {
            TeleportDestination currentSlot = getSavedDestinationEncoded(gun, pending.selectedIndex);
            TeleportDestination currentPhysicalLocation = getSpawnerPhysicalLocation(spawner);
            if (!sameDestination(currentSlot, pending.previousGunSlotDestination)
                    || !sameDestination(currentPhysicalLocation, pending.operationDestination)
                    || pending.selectedIndex < 0 || pending.selectedIndex >= 24
                    || pending.operationDestination == null
                    || !validateDestinationForPlayer(gun, player.world, pending.operationDestination, player)) {
                showBalloon(player, "message.meecreeps.portal_spawner_confirmation_stale");
                return;
            }
            addDestinationEncoded(gun, pending.operationDestination, pending.selectedIndex);
            showBalloon(player, "message.meecreeps.portal_spawner_download_success",
                    describeSlot(pending.selectedIndex), describeSpawner(spawner));
        }
    }

    @Nullable
    private static TeleportDestination getSpawnerPhysicalLocation(PortalSpawnerTileEntity spawner) {
        if (spawner == null || spawner.getWorld() == null || spawner.getWorld().provider == null
                || spawner.isInvalid()) return null;
        return new TeleportDestination(spawner.getSpawnerDestinationName(),
                spawner.getWorld().provider.getDimension(), spawner.getPos().down(),
                spawner.getFacing(), false);
    }

    private static TeleportDestination getSavedDestinationEncoded(ItemStack gun, int encodedIndex) {
        if (gun == null || gun.isEmpty() || encodedIndex < 0 || encodedIndex >= 24) return null;
        return getDestinations(gun, encodedIndex / SLOTS_PER_RING).get(encodedIndex % SLOTS_PER_RING);
    }

    private static int getDestinationSlotRevision(ItemStack stack, int encodedIndex) {
        if (stack == null || stack.isEmpty() || encodedIndex < 0 || encodedIndex >= 24 || !stack.hasTagCompound()) return 0;
        return stack.getTagCompound().getInteger("destinationSlotRevision_" + encodedIndex);
    }

    public static int getDestinationSelectionRevision(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) return 0;
        return stack.getTagCompound().getInteger("destinationSelectionRevision");
    }

    private static void incrementDestinationSelectionRevision(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        tag.setInteger("destinationSelectionRevision", tag.getInteger("destinationSelectionRevision") + 1);
    }

    private static boolean sameDestination(@Nullable TeleportDestination first, @Nullable TeleportDestination second) {
        if (first == null || second == null) return first == second;
        return first.getName().equals(second.getName())
                && java.util.Objects.equals(first.getDimension(), second.getDimension())
                && java.util.Objects.equals(first.getPos(), second.getPos())
                && first.getSide() == second.getSide()
                && first.shouldSearchNearby() == second.shouldSearchNearby();
    }

    private static String describeDestination(TeleportDestination destination) {
        if (destination == null) return "<none>";
        if (destination.isBlenderTrap()) return "Destination";
        String label = destination.getName().isEmpty() ? "Destination" : destination.getName();
        return label + " (DIM " + destination.getDimension() + ", "
                + destination.getPos().getX() + ", " + destination.getPos().getY() + ", "
                + destination.getPos().getZ() + ")";
    }

    private static String describeSpawner(PortalSpawnerTileEntity spawner) {
        BlockPos physicalPos = spawner.getPos().down();
        return spawner.getSpawnerDestinationName() + " (DIM "
                + spawner.getWorld().provider.getDimension() + ", "
                + physicalPos.getX() + ", " + physicalPos.getY() + ", "
                + physicalPos.getZ() + ")";
    }

    private static String describeSlot(int encodedIndex) {
        return "Ring " + (encodedIndex / SLOTS_PER_RING + 1) + ", slot "
                + (encodedIndex % SLOTS_PER_RING + 1) + " (#" + (encodedIndex + 1) + ")";
    }

    private static void sendTranslationError(EntityPlayer player, String translationKey) {
        sendPortalGunTranslation(player, getGun(player), translationKey);
    }

    private static void showBalloon(EntityPlayer player, String message, String... parameters) {
        if (player instanceof EntityPlayerMP) {
            MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient(message, parameters), (EntityPlayerMP) player);
        }
    }

    private enum SpawnerOperation {
        UPLOAD_CONFIGURED_DESTINATION,
        DOWNLOAD_PHYSICAL_LOCATION
    }

    private static class PendingSpawnerOperation {
        private final UUID token;
        private final UUID playerId;
        private final UUID gunId;
        private final SpawnerOperation operation;
        private final int targetDimension;
        private final BlockPos targetUpperPos;
        private final PortalSpawnerBlock targetBlock;
        private final EnumFacing targetFacing;
        private final boolean targetLinked;
        private final int selectedIndex;
        private final int selectionRevision;
        private final TeleportDestination operationDestination;
        private final TeleportDestination previousSpawnerDestination;
        private final TeleportDestination previousGunSlotDestination;
        private final PortalSpawnerTileEntity targetSpawner;
        private final int spawnerDestinationRevision;
        private final int slotRevision;
        private final BlockPos interactionBlockPos;
        private final long createdAt;

        private PendingSpawnerOperation(UUID token, UUID playerId, UUID gunId, SpawnerOperation operation,
                                        int targetDimension, BlockPos targetUpperPos, PortalSpawnerBlock targetBlock,
                                        EnumFacing targetFacing, boolean targetLinked, int selectedIndex,
                                        int selectionRevision, TeleportDestination operationDestination,
                                        TeleportDestination previousSpawnerDestination,
                                        TeleportDestination previousGunSlotDestination,
                                        PortalSpawnerTileEntity targetSpawner, int spawnerDestinationRevision,
                                        int slotRevision, BlockPos interactionBlockPos, long createdAt) {
            this.token = token;
            this.playerId = playerId;
            this.gunId = gunId;
            this.operation = operation;
            this.targetDimension = targetDimension;
            this.targetUpperPos = targetUpperPos;
            this.targetBlock = targetBlock;
            this.targetFacing = targetFacing;
            this.targetLinked = targetLinked;
            this.selectedIndex = selectedIndex;
            this.selectionRevision = selectionRevision;
            this.operationDestination = operationDestination;
            this.previousSpawnerDestination = previousSpawnerDestination;
            this.previousGunSlotDestination = previousGunSlotDestination;
            this.targetSpawner = targetSpawner;
            this.spawnerDestinationRevision = spawnerDestinationRevision;
            this.slotRevision = slotRevision;
            this.interactionBlockPos = interactionBlockPos;
            this.createdAt = createdAt;
        }
    }

    @Nullable
    public static TeleportDestination getSelectedSavedDestination(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        int current = getCurrentDestination(stack);
        if (current < 0 || current >= 24) return null;
        return getDestinations(stack, current / SLOTS_PER_RING).get(current % SLOTS_PER_RING);
    }

    private static void throwProjectile(EntityPlayer player, EnumHand hand, World world) {
        ItemStack heldItem = player.getHeldItem(hand);
        int charge = getChargeForProjectile(player, heldItem);
        if (charge < 0) return;

        TeleportDestination destination = isDualLinkedMode(heldItem) ? null : getDestinationForUse(heldItem);
        throwProjectile(player, world, heldItem, destination, true, charge);
    }

    private static void throwProjectile(EntityPlayer player, World world, ItemStack heldItem,
                                        @Nullable TeleportDestination destination,
                                        boolean clearFastReturnOnInvalidDestination, int charge) {
        boolean dualLinked = isDualLinkedMode(heldItem);
        if (!dualLinked && destination == null) {
            MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.gun_no_destination"), (EntityPlayerMP) player);
            return;
        }
        if (!dualLinked && !validateDestinationForPortalGun(heldItem, player.getEntityWorld(), destination, player)) {
            if (clearFastReturnOnInvalidDestination) {
                clearFastReturnOverride(heldItem);
            }
            return;
        }

        setCharge(heldItem, charge - 1);
        EntityProjectile projectile = new EntityProjectile(world, player);
        projectile.setDestination(destination);
        projectile.setDualLinked(dualLinked);
        projectile.setPlayerId(player.getUniqueID());
        projectile.setGunId(getGunId(heldItem));
        projectile.setAutoClose(isAutoCloseEnabled(heldItem));
        projectile.setTransportSolutionType(getTransportSolutionType(heldItem));
        projectile.setAirPlacementEnabled(isAirPlacementEnabled(heldItem));
        projectile.setOrigin(player.posX, player.posZ);
        projectile.shoot(player, player.rotationPitch, player.rotationYaw, 0.0F, 1.5F, 1.0F);
        world.spawnEntity(projectile);
    }

    private static int getChargeForProjectile(EntityPlayer player, ItemStack heldItem) {
        int charge = getCharge(heldItem);
        if (charge <= 0) {
            MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient("message.meecreeps.gun_no_charge"), (EntityPlayerMP) player);
            return -1;
        }
        return charge;
    }
    public static void fireFavourite(EntityPlayer player) {
        if (player == null || player.world == null || player.world.isRemote
                || !(player instanceof EntityPlayerMP)) {
            return;
        }

        ItemStack gun = getGun(player);
        if (gun.isEmpty()) {
            return;
        }

        int charge = getChargeForProjectile(player, gun);
        if (charge < 0) return;

        int favouriteIndex = getFavouriteDestination(gun);
        TeleportDestination destination = getSavedDestinationEncoded(gun, favouriteIndex);
        throwProjectile(player, player.world, gun, destination, false, charge);
    }

    private static void sendError(EntityPlayer player, String text) {
        sendPortalGunMessage(player, text);
    }

    private static void sendError(EntityPlayer player, String text, net.minecraft.util.text.TextFormatting color) {
        sendPortalGunMessage(player, text);
    }

    public static void sendPortalGunMessage(EntityPlayer player, String message) {
        if (player == null) return;
        ItemStack gun = getGun(player);
        TransportSolutionType type = gun.isEmpty()
                ? TransportSolutionType.INTERDIMENSIONAL : getTransportSolutionType(gun);
        sendPortalGunMessage(player, type, message);
    }

    public static void sendPortalGunMessage(EntityPlayer player, TransportSolutionType type, String message) {
        if (player == null || message == null) return;
        TransportSolutionType effectiveType = type == null ? TransportSolutionType.INTERDIMENSIONAL : type;
        player.sendMessage(new net.minecraft.util.text.TextComponentString(message)
                .setStyle(new net.minecraft.util.text.Style().setColor(effectiveType.getFormatting())));
    }

    public static void sendPortalGunTranslation(EntityPlayer player, ItemStack stack, String translationKey) {
        if (player == null || translationKey == null) return;
        TransportSolutionType type = stack == null || stack.isEmpty()
                ? TransportSolutionType.INTERDIMENSIONAL : getTransportSolutionType(stack);
        player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(translationKey)
                .setStyle(new net.minecraft.util.text.Style().setColor(type.getFormatting())));
    }

    public static void sendPortalGunTranslation(EntityPlayer player, TransportSolutionType type, String translationKey) {
        if (player == null || translationKey == null) return;
        TransportSolutionType effectiveType = type == null ? TransportSolutionType.INTERDIMENSIONAL : type;
        player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(translationKey)
                .setStyle(new net.minecraft.util.text.Style().setColor(effectiveType.getFormatting())));
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
        if (stack == null || stack.isEmpty() || currentWorld == null || destination == null
                || currentWorld.provider == null) {
            return false;
        }
        TransportSolutionType type = getTransportSolutionType(stack);
        if (destination.isBlenderTrap()) return type != TransportSolutionType.INTRADIMENSIONAL;
        return DimensionGroups.isDestinationAllowed(type,
                currentWorld.provider.getDimension(), destination.getDimension());
    }

    public static boolean validateDestinationForPlayer(ItemStack stack, World currentWorld,
                                                       TeleportDestination destination, EntityPlayer player) {
        return validateDestination(stack, currentWorld, destination, player, false);
    }
    public static boolean validateDestinationForPortalGun(ItemStack stack, World currentWorld,
                                                           TeleportDestination destination, EntityPlayer player) {
        return validateDestination(stack, currentWorld, destination, player, true);
    }

    private static boolean validateDestination(ItemStack stack, World currentWorld,
                                               TeleportDestination destination, EntityPlayer player,
                                               boolean allowNonIntradimensionalSpawnerReceiver) {
        if (stack == null || stack.isEmpty() || currentWorld == null || destination == null
                || currentWorld.provider == null) {
            if (player != null) {
                sendError(player, "INVALID DESTINATION DIMENSION!");
            }
            return false;
        }

        TransportSolutionType type = getTransportSolutionType(stack);
        if (destination.isBlenderTrap()) {
            if (type == TransportSolutionType.INTRADIMENSIONAL) {
                if (player != null) sendIntradimensionalDimensionError(player);
                return false;
            }
            return true;
        }
        DimensionGroups.DestinationStatus status = DimensionGroups.checkDestination(
                type, currentWorld.provider.getDimension(), destination.getDimension());
        if (status == DimensionGroups.DestinationStatus.BLACKLISTED) {
            sendBlacklistedDimensionError(player);
            return false;
        }
        if (status == DimensionGroups.DestinationStatus.GROUP_RESTRICTED
                && (!allowNonIntradimensionalSpawnerReceiver || type != TransportSolutionType.INTRADIMENSIONAL
                || !TeleportationTools.isIntradimensionalSpawnerReceiverAllowed(currentWorld, destination))) {
            sendIntradimensionalDimensionError(player);
            return false;
        }
        if (status == DimensionGroups.DestinationStatus.UNAVAILABLE) {
            if (player != null) {
                sendError(player, "INVALID DESTINATION DIMENSION!");
            }
            return false;
        }

        if (TeleportationTools.resolveDestinationWorld(destination.getDimension()) == null) {
            if (player != null) {
                sendError(player, "INVALID DESTINATION DIMENSION!");
            }
            return false;
        }
        return true;
    }

    public static boolean sendDimensionErrorIfNeeded(EntityPlayer player, ItemStack stack, TeleportDestination destination) {
        return player != null && !validateDestinationForPlayer(stack, player.getEntityWorld(), destination, player);
    }

    public static void sendIntradimensionalDimensionError(EntityPlayer player) {
        sendError(player, INTRADIMENSIONAL_DIMENSION_ERROR);
    }

    public static void sendBlacklistedDimensionError(@Nullable EntityPlayer player) {
        sendPortalGunMessage(player, BLACKLISTED_DIMENSION_ERROR);
    }

    public static void sendBlacklistedDimensionError(@Nullable EntityPlayer player, TransportSolutionType type) {
        sendPortalGunMessage(player, type, BLACKLISTED_DIMENSION_ERROR);
    }

    public static void sendInformationalMessage(EntityPlayer player, ItemStack stack, String message) {
        TransportSolutionType type = stack == null || stack.isEmpty()
                ? TransportSolutionType.INTERDIMENSIONAL : getTransportSolutionType(stack);
        sendInformationalMessage(player, type, message);
    }

    public static void sendInformationalMessage(EntityPlayer player, TransportSolutionType type, String message) {
        if (player == null || message == null) return;
        TransportSolutionType effectiveType = type == null ? TransportSolutionType.INTERDIMENSIONAL : type;
        player.sendMessage(new net.minecraft.util.text.TextComponentString(message)
                .setStyle(new net.minecraft.util.text.Style().setColor(effectiveType.getFormatting())));
    }

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
        int encoded = ring * SLOTS_PER_RING + slot;
        TeleportDestination previousDestination = getSavedDestinationEncoded(stack, encoded);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey(FAVOURITE_DESTINATION_TAG)
                && tag.getInteger(FAVOURITE_DESTINATION_TAG) == encoded
                && !sameDestination(previousDestination, destination)) {
            tag.removeTag(FAVOURITE_DESTINATION_TAG);
        }
        List<TeleportDestination> destinations = getDestinations(stack, ring);
        destinations.set(slot, destination);
        setDestinations(stack, destinations, ring);
        String slotRevisionKey = "destinationSlotRevision_" + encoded;
        stack.getTagCompound().setInteger(slotRevisionKey, stack.getTagCompound().getInteger(slotRevisionKey) + 1);
        UUID id = getGunId(stack);
        if (destination != null) {

            CUSTOM_DESTINATIONS.remove(id);
            setCurrentDestination(stack, encoded);
        } else {
            CustomDestination custom = CUSTOM_DESTINATIONS.get(id);
            if (custom != null && custom.index == encoded) CUSTOM_DESTINATIONS.remove(id);
        }
    }

    private static boolean isValidSavedDestination(TeleportDestination destination) {
        return destination != null && !destination.isBlenderTrap()
                && destination.getPos() != null && destination.getSide() != null
                && destination.getDimension() != null
                && DimensionGroups.isDimensionAvailable(destination.getDimension());
    }

    private static boolean isValidOneShotDestination(TeleportDestination destination) {
        return destination != null && (destination.isBlenderTrap() || isValidSavedDestination(destination));
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

    public static int getFavouriteDestination(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) return -1;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(FAVOURITE_DESTINATION_TAG)) return -1;
        int encoded = tag.getInteger(FAVOURITE_DESTINATION_TAG);
        if (encoded < 0 || encoded >= 24 || getSavedDestinationEncoded(stack, encoded) == null) return -1;
        return encoded;
    }

    public static boolean isFavouriteDestination(ItemStack stack, int encodedIndex) {
        return encodedIndex >= 0 && getFavouriteDestination(stack) == encodedIndex;
    }

    public static boolean toggleFavouriteDestination(ItemStack stack, int encodedIndex) {
        if (stack == null || stack.isEmpty() || encodedIndex < 0 || encodedIndex >= 24
                || getSavedDestinationEncoded(stack, encodedIndex) == null) {
            return false;
        }
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        if (getFavouriteDestination(stack) == encodedIndex) {
            tag.removeTag(FAVOURITE_DESTINATION_TAG);
        } else {
            tag.setInteger(FAVOURITE_DESTINATION_TAG, encodedIndex);
        }
        return true;
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

        int slot = tag.getInteger("destination");
        return slot >= 0 && slot < SLOTS_PER_RING ? RING_CENTER * SLOTS_PER_RING + slot : -1;
    }

    public static void setCurrentDestination(ItemStack stack, int dest) {
        if (dest < 0 || dest >= 24) return;
        int ring = dest / SLOTS_PER_RING;
        int slot = dest % SLOTS_PER_RING;
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey("currentRing")) {

            if (dest < SLOTS_PER_RING && getDestinations(stack, RING_LEFT).get(dest) == null && getDestinations(stack, RING_CENTER).get(dest) != null) {
                ring = RING_CENTER;
                slot = dest;
                dest = RING_CENTER * SLOTS_PER_RING + slot;
            }
        }
        TeleportDestination saved = getDestinations(stack, ring).get(slot);
        int previousSelection = getCurrentDestination(stack);
        UUID id = getGunId(stack);
        CustomDestination custom = CUSTOM_DESTINATIONS.get(id);
        if (saved == null && (custom == null || custom.index != dest)) return;

        FAST_RETURN_OVERRIDES.remove(id);
        CUSTOM_DESTINATIONS.remove(id);
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger("destination", dest);
        stack.getTagCompound().setInteger("currentRing", ring);
        stack.getTagCompound().setInteger("currentSlot", slot);
        if (previousSelection != dest) incrementDestinationSelectionRevision(stack);
    }

    public static List<TeleportDestination> getDestinations(ItemStack stack) {
        return getDestinations(stack, RING_CENTER);
    }

    public static List<TeleportDestination> getSelectableDestinations(ItemStack stack, int ring, World currentWorld) {
        return getDestinations(stack, ring);
    }

    public static List<TeleportDestination> getDestinations(ItemStack stack, int ring) {
        List<TeleportDestination> destinations = new ArrayList<>();
        for (int i = 0; i < SLOTS_PER_RING; i++) destinations.add(null);
        if (!stack.hasTagCompound()) return destinations;
        NBTTagList dests = stack.getTagCompound().getTagList(getRingKey(ring), Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < SLOTS_PER_RING && i < dests.tagCount(); i++) {
            NBTTagCompound tc = dests.getCompoundTagAt(i);
            if (tc.hasKey("dim") && !TeleportDestination.BLENDER_DIMENSION.equals(tc.getString("name"))) {
                destinations.set(i, new TeleportDestination(tc));
            }
        }
        return destinations;
    }

    public static void setCustomDestination(ItemStack stack, int index, TeleportDestination destination) {
        if (index < 0 || index >= 24) return;
        if (!isValidOneShotDestination(destination)) return;
        int previousSelection = getCurrentDestination(stack);
        UUID id = getGunId(stack);
        CUSTOM_DESTINATIONS.put(id, new CustomDestination(index, destination));
        FAST_RETURN_OVERRIDES.remove(id);
        if (previousSelection != index) incrementDestinationSelectionRevision(stack);
    }


    public static void setFastReturnOverride(ItemStack stack, TeleportDestination destination) {
        if (!isValidOneShotDestination(destination)) return;
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
        int previousSelection = getCurrentDestination(stack);
        CustomDestination custom = CUSTOM_DESTINATIONS.get(id);
        if (custom != null && custom.index == previousSelection) {
            CUSTOM_DESTINATIONS.remove(id);
            if (previousSelection != getCurrentDestination(stack)) incrementDestinationSelectionRevision(stack);
        }
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
    public boolean showDurabilityBar(ItemStack stack) {
        return !stack.hasTagCompound() || !stack.getTagCompound().getBoolean("meecreepsCreativeTabIcon");
    }

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
        } else {
            BlockPos portalPos = findPortalInLook(world, player);
            if (portalPos != null) {
                if (world.isRemote) {
                    MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(
                            MeeCreeps.MODID, CommandHandler.CMD_CANCEL_PORTAL,
                            TypedMap.builder().put(CommandHandler.PARAM_POS, portalPos).build()));
                } else {
                    mcjty.meecreeps.teleport.TeleportationTools.cancelPortalPair(player, portalPos);
                }
            } else if (!world.isRemote) {
                throwProjectile(player, hand, world);
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Nullable
    private static BlockPos findPortalInLook(World world, EntityPlayer player) {
        net.minecraft.util.math.Vec3d start = player.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d look = player.getLook(1.0F);
        double reach = player instanceof EntityPlayerMP
                ? ((EntityPlayerMP) player).interactionManager.getBlockReachDistance()
                : 5.0D;
        net.minecraft.util.math.Vec3d end = start.add(look.scale(reach));

        double minX = Math.min(start.x, end.x) - 1.0D;
        double minY = Math.min(start.y, end.y) - 1.0D;
        double minZ = Math.min(start.z, end.z) - 1.0D;
        double maxX = Math.max(start.x, end.x) + 1.0D;
        double maxY = Math.max(start.y, end.y) + 1.0D;
        double maxZ = Math.max(start.z, end.z) + 1.0D;

        double closestDistanceSq = Double.MAX_VALUE;
        BlockPos closestPortal = null;
        for (BlockPos pos : BlockPos.getAllInBox(
                new BlockPos(MathHelper.floor(minX), MathHelper.floor(minY), MathHelper.floor(minZ)),
                new BlockPos(MathHelper.floor(maxX), MathHelper.floor(maxY), MathHelper.floor(maxZ)))) {
            if (world.getBlockState(pos).getBlock() != ModBlocks.portalBlock) continue;
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof PortalTileEntity)) continue;

            PortalTileEntity portal = (PortalTileEntity) te;
            if (portal.isSpawnerControlled() || portal.getPortalSide() == null) continue;

            Double rayDistance = rayDistanceToPortal(start, look, pos, portal.getPortalSide(), reach);
            if (rayDistance == null) continue;
            net.minecraft.util.math.Vec3d hit = start.add(look.scale(rayDistance));
            double distanceSq = hit.squareDistanceTo(start);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closestPortal = pos;
            }
        }

        if (closestPortal == null) return null;

        net.minecraft.util.math.RayTraceResult obstruction = world.rayTraceBlocks(start, end, false, true, false);
        if (obstruction != null && obstruction.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            double obstructionDistanceSq = obstruction.hitVec.squareDistanceTo(start);
            if (obstructionDistanceSq + 1.0E-6D < closestDistanceSq) return null;
        }
        return closestPortal;
    }

    @Nullable
    private static Double rayDistanceToPortal(net.minecraft.util.math.Vec3d start, net.minecraft.util.math.Vec3d look,
                                               BlockPos pos, EnumFacing side, double reach) {
        final double plane;
        final double minU;
        final double maxU;
        final double minV;
        final double maxV;
        final double denominator;
        final double origin;

        switch (side) {
            case DOWN:
                plane = pos.getY() + 0.90D;
                denominator = look.y;
                origin = start.y;
                minU = pos.getX() - 0.50D;
                maxU = pos.getX() + 1.50D;
                minV = pos.getZ() - 0.50D;
                maxV = pos.getZ() + 1.50D;
                break;
            case UP:
                plane = pos.getY() + 0.10D;
                denominator = look.y;
                origin = start.y;
                minU = pos.getX() - 0.50D;
                maxU = pos.getX() + 1.50D;
                minV = pos.getZ() - 0.50D;
                maxV = pos.getZ() + 1.50D;
                break;
            case SOUTH:
                plane = pos.getZ();
                denominator = look.z;
                origin = start.z;
                minU = pos.getX() - 0.50D;
                maxU = pos.getX() + 1.50D;
                minV = pos.getY();
                maxV = pos.getY() + 2.00D;
                break;
            case NORTH:
                plane = pos.getZ() + 1.00D;
                denominator = look.z;
                origin = start.z;
                minU = pos.getX() - 0.50D;
                maxU = pos.getX() + 1.50D;
                minV = pos.getY();
                maxV = pos.getY() + 2.00D;
                break;
            case EAST:
                plane = pos.getX();
                denominator = look.x;
                origin = start.x;
                minU = pos.getZ() - 0.50D;
                maxU = pos.getZ() + 1.50D;
                minV = pos.getY();
                maxV = pos.getY() + 2.00D;
                break;
            case WEST:
                plane = pos.getX() + 1.00D;
                denominator = look.x;
                origin = start.x;
                minU = pos.getZ() - 0.50D;
                maxU = pos.getZ() + 1.50D;
                minV = pos.getY();
                maxV = pos.getY() + 2.00D;
                break;
            default:
                return null;
        }

        if (Math.abs(denominator) < 1.0E-8D) return null;
        double distance = (plane - origin) / denominator;
        if (distance < 0.0D || distance > reach) return null;

        net.minecraft.util.math.Vec3d hit = start.add(look.scale(distance));
        double u;
        double v;
        switch (side.getAxis()) {
            case X:
                u = hit.z;
                v = hit.y;
                break;
            case Y:
                u = hit.x;
                v = hit.z;
                break;
            case Z:
            default:
                u = hit.x;
                v = hit.y;
                break;
        }

        return u >= minU - 1.0E-6D && u <= maxU + 1.0E-6D
                && v >= minV - 1.0E-6D && v <= maxV + 1.0E-6D
                ? distance : null;
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
