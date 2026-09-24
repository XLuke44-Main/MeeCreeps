package mcjty.meecreeps.teleport;

import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.actions.PacketShowBalloonToClient;
import mcjty.meecreeps.blocks.ModBlocks;
import mcjty.meecreeps.blocks.PortalTileEntity;
import mcjty.meecreeps.blocks.PortalSpawnerBlock;
import mcjty.meecreeps.blocks.PortalSpawnerTileEntity;
import mcjty.meecreeps.config.ConfigSetup;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.items.TransportSolutionType;
import mcjty.meecreeps.network.MeeCreepsMessages;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportationTools {

    private static final String FAST_RETURN_TAG = "MeeCreepsFastReturn";

    private static final Map<UUID, TeleportSuppression> TELEPORT_SUPPRESSION = new ConcurrentHashMap<>();
    private static final int TELEPORT_SUPPRESSION_TICKS = 12;

    private static final class TeleportSuppression {
        private final WeakReference<World> world;
        private final BlockPos portalPos;
        private long expiresAt;

        private TeleportSuppression(PortalTileEntity portal) {
            this.world = new WeakReference<>(portal.getWorld());
            this.portalPos = portal.getPos();
            this.expiresAt = portal.getWorld().getTotalWorldTime() + TELEPORT_SUPPRESSION_TICKS;
        }

        private boolean matches(EntityPlayer player, PortalTileEntity portal) {
            World w = world.get();
            return w != null && w == portal.getWorld() && portal.getPos().equals(portalPos)
                    && w.getTotalWorldTime() <= expiresAt;
        }
    }

    private static final class Validation {
        private final boolean valid;
        private final String message;
        private final World world;

        private Validation(boolean valid, String message, World world) {
            this.valid = valid;
            this.message = message;
            this.world = world;
        }

        private static Validation valid(World world) {
            return new Validation(true, null, world);
        }

        private static Validation invalid(String message) {
            return new Validation(false, message, null);
        }
    }

    private static final class SafeDestination {
        private final BlockPos pos;
        private final EnumFacing side;

        private SafeDestination(BlockPos pos, EnumFacing side) {
            this.pos = pos;
            this.side = side;
        }
    }

    static boolean isWithinInteractionRange(EntityPlayer player, BlockPos selectedBlock) {
        if (player == null || selectedBlock == null) return false;
        double reach = player instanceof EntityPlayerMP
                ? ((EntityPlayerMP) player).interactionManager.getBlockReachDistance()
                : 4.5D;
        double dx = player.posX - (selectedBlock.getX() + 0.5D);
        double dy = player.posY - (selectedBlock.getY() + 0.5D);
        double dz = player.posZ - (selectedBlock.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    public static void cancelPortalPair(EntityPlayer player, BlockPos selectedBlock) {
        try {
            if (player == null || selectedBlock == null) return;
            World sourceWorld = player.getEntityWorld();
            if (!isWithinInteractionRange(player, selectedBlock)) return;

            TileEntity te = sourceWorld.getTileEntity(selectedBlock);
            if (te instanceof PortalTileEntity) {
                PortalTileEntity portal = (PortalTileEntity) te;
                ItemStack heldGun = PortalGunItem.getGun(player);
                if (heldGun.isEmpty()) return;

                UUID portalGunId = portal.getGunId();
                if (portalGunId != null && !portalGunId.equals(PortalGunItem.getGunId(heldGun))) return;

                portal.forceManualClose();
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean isOnlinePlayerLocator(EntityPlayerMP requester, String input) {
        return findOnlinePlayer(requester, input) != null;
    }

    @Nullable
    public static TeleportDestination resolvePlayerLocator(EntityPlayerMP requester, String input) {
        EntityPlayerMP target = findOnlinePlayer(requester, input);
        if (target == null || target.getEntityWorld() == null || target.isDead) return null;

        World targetWorld = target.getEntityWorld();
        BlockPos targetPos = new BlockPos(target.posX, target.posY, target.posZ);
        loadChunkArea(targetWorld, targetPos, 1);
        SafeDestination safe = findSafeDestinationNearPlayer(targetWorld, targetPos, EnumFacing.NORTH);
        if (safe == null) return null;

        return new TeleportDestination(target.getName(), targetWorld.provider.getDimension(), safe.pos, safe.side, true);
    }

    @Nullable
    private static EntityPlayerMP findOnlinePlayer(EntityPlayerMP requester, String input) {
        if (requester == null || input == null) return null;
        String username = input.trim();
        if (username.isEmpty() || username.indexOf(';') >= 0 || username.length() > 16) return null;
        if (!username.matches("[A-Za-z0-9_]+")) return null;

        MinecraftServer server = requester.getServer();
        if (server == null) return null;
        for (EntityPlayerMP online : server.getPlayerList().getPlayers()) {
            if (online != null && online.getName().equalsIgnoreCase(username)) return online;
        }
        return null;
    }

    public static void makePortalPair(EntityPlayer player, BlockPos selectedBlock, EnumFacing selectedSide, TeleportDestination dest) {
        ItemStackHolder holder = getPlayerGunState(player);
        makePortalPair(player, selectedBlock, selectedSide, dest, holder.gunId, holder.autoClose,
                holder.transportSolutionType);
    }

    public static void makePortalPair(EntityPlayer player, BlockPos selectedBlock, EnumFacing selectedSide, TeleportDestination dest, java.util.UUID gunId, boolean autoClose) {
        net.minecraft.item.ItemStack heldGun = PortalGunItem.getGun(player);
        TransportSolutionType type = heldGun.isEmpty() ? TransportSolutionType.INTERDIMENSIONAL : PortalGunItem.getTransportSolutionType(heldGun);
        makePortalPair(player, selectedBlock, selectedSide, dest, gunId, autoClose, type);
    }

    public static void makePortalPair(EntityPlayer player, BlockPos selectedBlock, EnumFacing selectedSide, TeleportDestination dest, java.util.UUID gunId, boolean autoClose, TransportSolutionType transportSolutionType) {
        makePortalPair(player, selectedBlock, selectedSide, dest, gunId, autoClose, null, false, transportSolutionType);
    }

    public static boolean makeAirPortalPair(EntityPlayer player, BlockPos sourcePortalPos, EnumFacing sourceSide, TeleportDestination dest, java.util.UUID gunId, boolean autoClose) {
        net.minecraft.item.ItemStack heldGun = PortalGunItem.getGun(player);
        TransportSolutionType type = heldGun.isEmpty() ? TransportSolutionType.INTERDIMENSIONAL : PortalGunItem.getTransportSolutionType(heldGun);
        return makeAirPortalPair(player, sourcePortalPos, sourceSide, dest, gunId, autoClose, type);
    }

    public static boolean makeAirPortalPair(EntityPlayer player, BlockPos sourcePortalPos, EnumFacing sourceSide, TeleportDestination dest, java.util.UUID gunId, boolean autoClose, TransportSolutionType transportSolutionType) {
        return makePortalPair(player, null, sourceSide, dest, gunId, autoClose, sourcePortalPos, true, transportSolutionType);
    }

    private static boolean makePortalPair(EntityPlayer player, BlockPos selectedBlock, EnumFacing selectedSide, TeleportDestination dest, java.util.UUID gunId, boolean autoClose, @Nullable BlockPos sourcePortalPosOverride, boolean allowAirSource, TransportSolutionType transportSolutionType) {
        try {
            if (player == null || player.getEntityWorld() == null || (selectedBlock == null && sourcePortalPosOverride == null) || selectedSide == null || dest == null) return false;

            TransportSolutionType type = transportSolutionType == null ? TransportSolutionType.INTERDIMENSIONAL : transportSolutionType;
            if (type.isIntradimensional() && dest.getDimension() != player.getEntityWorld().provider.getDimension()) {
                PortalGunItem.sendIntradimensionalDimensionError(player);
                return false;
            }

            Validation validation = validateDestination(player.getEntityWorld(), dest);
            if (!validation.valid) {
                sendDestinationError(player, validation.message);
                return false;
            }

            World sourceWorld = player.getEntityWorld();
            World destWorld = validation.world;

            BlockPos sourcePortalPos = sourcePortalPosOverride == null
                    ? findBestPosition(sourceWorld, selectedBlock, selectedSide)
                    : sourcePortalPosOverride;
            if (sourcePortalPos == null) {
                showBalloon(player, "message.meecreeps.cant_find_portal_spot");
                return false;
            }

            if (destWorld.getBlockState(dest.getPos()).getBlock() == ModBlocks.portalBlock) {
                showBalloon(player, "message.meecreeps.portal_already_there");
                return false;
            }

            loadChunkArea(destWorld, dest.getPos(), dest.shouldSearchNearby() ? 1 : 0);

            SafeDestination safeDestination;
            if (dest.shouldSearchNearby()) {
                safeDestination = findSafeDestination(destWorld, dest.getPos(), dest.getSide());
            } else if (isValidSavedDestinationPlacement(destWorld, dest.getPos(), dest.getSide())) {
                safeDestination = new SafeDestination(dest.getPos(), dest.getSide());
            } else {
                safeDestination = null;
            }
            if (safeDestination == null) {
                if (dest.shouldSearchNearby()) {
                    sendDestinationError(player, "NO SAFE DESTINATION LOCATION AVAILABLE!");
                } else {
                    showBalloon(player, "message.meecreeps.destination_obstructed");
                }
                return false;
            }

            if (sourceWorld.provider.getDimension() == destWorld.provider.getDimension() && sourcePortalPos.equals(safeDestination.pos)) {
                sendDestinationError(player, "NO SAFE DESTINATION LOCATION AVAILABLE!");
                return false;
            }

            TeleportDestination finalDestination = new TeleportDestination(dest.getName(), dest.getDimension(), safeDestination.pos, safeDestination.side, dest.shouldSearchNearby());
            boolean created = createPair(sourceWorld, sourcePortalPos, selectedSide, destWorld, finalDestination, gunId, autoClose, allowAirSource, transportSolutionType);
            if (created) {
                if (dest.shouldSearchNearby() && isHazardousCustomDestination(destWorld, finalDestination.getPos(), finalDestination.getSide())) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "WARNING: CUSTOM DESTINATION IS DANGEROUS!"
                    ).setStyle(new net.minecraft.util.text.Style().setColor(net.minecraft.util.text.TextFormatting.YELLOW)));
                }
                PortalGunItem.clearOneShotDestination(PortalGunItem.getGun(player));
            }
            return created;
        } catch (Exception e) {
            MeeCreeps.setup.getLogger().error("Portal creation failed safely", e);
            sendDestinationError(player, "PORTAL CREATION FAILED SAFELY.");
            return false;
        }
    }

    private static ItemStackHolder getPlayerGunState(EntityPlayer player) {
        if (player == null) return new ItemStackHolder(null, true, TransportSolutionType.INTERDIMENSIONAL);
        net.minecraft.item.ItemStack gun = PortalGunItem.getGun(player);
        if (gun.isEmpty()) return new ItemStackHolder(null, true, TransportSolutionType.INTERDIMENSIONAL);
        return new ItemStackHolder(PortalGunItem.getGunId(gun), PortalGunItem.isAutoCloseEnabled(gun), PortalGunItem.getTransportSolutionType(gun));
    }

    private static class ItemStackHolder {
        private final java.util.UUID gunId;
        private final boolean autoClose;
        private final TransportSolutionType transportSolutionType;
        private ItemStackHolder(java.util.UUID gunId, boolean autoClose, TransportSolutionType transportSolutionType) { this.gunId = gunId; this.autoClose = autoClose; this.transportSolutionType = transportSolutionType; }
    }

    public static Entity teleportEntity(Entity entity, World destWorld, double newX, double newY, double newZ, EnumFacing facing) {
        return mcjty.lib.varia.TeleportationTools.teleportEntity(entity, destWorld, newX, newY, newZ, facing);
    }

    public static Entity teleportEntity(Entity entity, World destWorld, double newX, double newY, double newZ,
                                         EnumFacing sourceFacing, EnumFacing destinationFacing) {
        if (entity == null || destWorld == null) return entity;
        if (!(entity instanceof EntityPlayer) || sourceFacing == null || destinationFacing == null) {
            return teleportEntity(entity, destWorld, newX, newY, newZ, destinationFacing);
        }

        float rotationYaw = entity.rotationYaw;
        float rotationPitch = entity.rotationPitch;
        Vec3d look = getLookVector(rotationYaw, rotationPitch);
        Vec3d transformed = transformPortalVector(look, sourceFacing, destinationFacing);
        float yaw = rotationYaw;
        float pitch = rotationPitch;
        if (destinationFacing.getAxis().isHorizontal()) {
            double horizontalLook = Math.hypot(transformed.x, transformed.z);
            if (horizontalLook >= 1.0E-7D) {
                float targetYaw = (float) (Math.atan2(-transformed.x, transformed.z) * 180.0D / Math.PI);
                yaw = rotationYaw + MathHelper.wrapDegrees(targetYaw - rotationYaw);
            }
        }

        setOrientation(entity, yaw, pitch);
        Entity teleported = mcjty.lib.varia.TeleportationTools.teleportEntity(
                entity, destWorld, newX, newY, newZ, null);
        if (teleported == null) return null;

        setOrientation(teleported, yaw, pitch);
        if (teleported instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) teleported;
            if (player.connection != null) {
                player.connection.setPlayerLocation(teleported.posX, teleported.posY, teleported.posZ, yaw, pitch);
            }
        }
        return teleported;
    }

    private static Vec3d getLookVector(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRadians);
        return new Vec3d(
                -Math.sin(yawRadians) * horizontal,
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * horizontal
        );
    }

    private static Vec3d transformPortalVector(Vec3d vector, EnumFacing sourceFacing, EnumFacing destinationFacing) {
        Vec3d sourceRight = getPortalRight(sourceFacing);
        Vec3d sourceUp = getPortalUp(sourceFacing);
        Vec3d sourceNormal = getPortalNormal(sourceFacing);

        double right = vector.dotProduct(sourceRight);
        double up = vector.dotProduct(sourceUp);
        double normal = vector.dotProduct(sourceNormal);

        Vec3d destinationRight = getPortalRight(destinationFacing);
        Vec3d destinationUp = getPortalUp(destinationFacing);
        Vec3d destinationNormal = getPortalNormal(destinationFacing);

        // Portal traversal keeps the two in-plane coordinates continuous and reverses only
        // the component perpendicular to the portal. This transform is used for the
        // player's view direction so horizontal <-> vertical transitions do not
        // introduce a lateral mirror/snap.
        return new Vec3d(
                destinationRight.x * right + destinationUp.x * up - destinationNormal.x * normal,
                destinationRight.y * right + destinationUp.y * up - destinationNormal.y * normal,
                destinationRight.z * right + destinationUp.z * up - destinationNormal.z * normal
        );
    }

    private static Vec3d getPortalNormal(EnumFacing facing) {
        return new Vec3d(facing.getFrontOffsetX(), facing.getFrontOffsetY(), facing.getFrontOffsetZ());
    }

    private static Vec3d getPortalUp(EnumFacing facing) {
        return facing.getAxis().isHorizontal() ? new Vec3d(0.0D, 1.0D, 0.0D) : new Vec3d(0.0D, 0.0D, 1.0D);
    }

    private static Vec3d getPortalRight(EnumFacing facing) {
        Vec3d up = getPortalUp(facing);
        Vec3d normal = getPortalNormal(facing);
        return new Vec3d(
                up.y * normal.z - up.z * normal.y,
                up.z * normal.x - up.x * normal.z,
                up.x * normal.y - up.y * normal.x
        );
    }

    private static void setOrientation(Entity entity, float yaw, float pitch) {
        entity.rotationYaw = yaw;
        entity.prevRotationYaw = yaw;
        entity.rotationPitch = pitch;
        entity.prevRotationPitch = pitch;
        if (entity instanceof net.minecraft.entity.EntityLivingBase) {
            net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) entity;
            living.rotationYawHead = yaw;
            living.prevRotationYawHead = yaw;
            living.renderYawOffset = yaw;
            living.prevRenderYawOffset = yaw;
        }
    }

    public static boolean makePortalPairFromSpawner(PortalSpawnerTileEntity spawner) {
        try {
            if (spawner == null || spawner.getWorld() == null || spawner.getWorld().isRemote) return false;
            World sourceWorld = spawner.getWorld();
            BlockPos upperPos = spawner.getPos();
            BlockPos sourcePortalPos = upperPos.down();
            IBlockState upperState = sourceWorld.getBlockState(upperPos);
            IBlockState lowerState = sourceWorld.getBlockState(sourcePortalPos);
            if (!(upperState.getBlock() instanceof PortalSpawnerBlock)
                    || upperState.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.UPPER
                    || lowerState.getBlock() != upperState.getBlock()
                    || lowerState.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.LOWER) {
                return false;
            }

            TeleportDestination dest = spawner.getDestination();
            if (dest == null || dest.getPos() == null || dest.getSide() == null) return false;
            TransportSolutionType type = spawner.getTransportSolutionType();
            if (type.isIntradimensional() && dest.getDimension() != sourceWorld.provider.getDimension()) return false;

            Validation validation = validateDestination(sourceWorld, dest);
            if (!validation.valid) return false;
            World destWorld = validation.world;
            if (destWorld.getBlockState(dest.getPos()).getBlock() == ModBlocks.portalBlock) return false;

            loadChunkArea(destWorld, dest.getPos(), dest.shouldSearchNearby() ? 1 : 0);
            SafeDestination safeDestination = dest.shouldSearchNearby()
                    ? findSafeDestination(destWorld, dest.getPos(), dest.getSide())
                    : (isValidSavedDestinationPlacement(destWorld, dest.getPos(), dest.getSide())
                        ? new SafeDestination(dest.getPos(), dest.getSide()) : null);
            if (safeDestination == null) return false;
            if (sourceWorld.provider.getDimension() == destWorld.provider.getDimension()
                    && sourcePortalPos.equals(safeDestination.pos)) return false;

            TeleportDestination finalDestination = new TeleportDestination(
                    dest.getName(), dest.getDimension(), safeDestination.pos, safeDestination.side, dest.shouldSearchNearby());
            boolean created = createPair(sourceWorld, sourcePortalPos, spawner.getFacing(), destWorld, finalDestination,
                    null, false, false, type, upperPos, true);
            if (created) {
                PortalSpawnerBlock.setPortalActive(sourceWorld, upperPos, true);
            }
            return created;
        } catch (Exception e) {
            MeeCreeps.setup.getLogger().error("Failed to create a Portal Spawner portal pair", e);
            return false;
        }
    }

    public static boolean makeLinkedPortalPair(PortalSpawnerTileEntity spawner) {
        try {
            if (spawner == null || spawner.getWorld() == null || spawner.getWorld().isRemote) return false;
            if (!spawner.isLinkedPairReady()) return false;

            World sourceWorld = spawner.getWorld();
            BlockPos sourceUpperPos = spawner.getPos();
            BlockPos sourceLowerPos = sourceUpperPos.down();
            PortalSpawnerBlock sourceBlock = getLinkedSpawnerBlock(sourceWorld, sourceUpperPos);
            if (sourceBlock == null) return false;

            Integer twinDimension = spawner.getTwinDimension();
            BlockPos twinUpperPos = spawner.getTwinPos();
            if (twinDimension == null || twinUpperPos == null) return false;

            World twinWorld = resolveDestinationWorld(twinDimension);
            if (twinWorld == null) return false;
            twinWorld.getChunkFromBlockCoords(twinUpperPos);
            BlockPos twinLowerPos = twinUpperPos.down();
            PortalSpawnerBlock twinBlock = getLinkedSpawnerBlock(twinWorld, twinUpperPos);
            if (twinBlock == null || twinBlock != sourceBlock) return false;

            PortalSpawnerTileEntity twinSpawner = getSpawnerTile(twinWorld, twinUpperPos);
            if (twinSpawner == null || !spawner.isLinkedPairReady() || !twinSpawner.isLinkedPairReady()) return false;

            IBlockState sourceLowerState = sourceWorld.getBlockState(sourceLowerPos);
            IBlockState twinLowerState = twinWorld.getBlockState(twinLowerPos);
            if (sourceLowerState.getBlock() != sourceBlock
                    || sourceLowerState.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.LOWER
                    || twinLowerState.getBlock() != twinBlock
                    || twinLowerState.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.LOWER) {
                return false;
            }

            TransportSolutionType type = sourceBlock.getTransportSolutionType();
            if (type.isIntradimensional()
                    && sourceWorld.provider.getDimension() != twinWorld.provider.getDimension()) {
                return false;
            }

            TeleportDestination sourceDestination = new TeleportDestination(
                    "", twinWorld.provider.getDimension(), twinLowerPos, twinSpawner.getFacing(), false);
            TeleportDestination twinDestination = new TeleportDestination(
                    "", sourceWorld.provider.getDimension(), sourceLowerPos, spawner.getFacing(), false);

            PortalSpawnerBlock.beginPortalReplacement();
            try {
                sourceWorld.setBlockState(sourceLowerPos, ModBlocks.portalBlock.getDefaultState(), 3);
                twinWorld.setBlockState(twinLowerPos, ModBlocks.portalBlock.getDefaultState(), 3);
            } finally {
                PortalSpawnerBlock.endPortalReplacement();
            }

            TileEntity sourceTe = sourceWorld.getTileEntity(sourceLowerPos);
            TileEntity twinTe = twinWorld.getTileEntity(twinLowerPos);
            if (!(sourceTe instanceof PortalTileEntity) || !(twinTe instanceof PortalTileEntity)) {
                if (sourceWorld.getBlockState(sourceLowerPos).getBlock() == ModBlocks.portalBlock) {
                    sourceWorld.setBlockToAir(sourceLowerPos);
                    PortalSpawnerBlock.restoreLowerSection(sourceWorld, sourceUpperPos);
                }
                if (twinWorld.getBlockState(twinLowerPos).getBlock() == ModBlocks.portalBlock) {
                    twinWorld.setBlockToAir(twinLowerPos);
                    PortalSpawnerBlock.restoreLowerSection(twinWorld, twinUpperPos);
                }
                return false;
            }

            PortalTileEntity sourcePortal = (PortalTileEntity) sourceTe;
            PortalTileEntity twinPortal = (PortalTileEntity) twinTe;
            sourcePortal.setTimeout(Integer.MAX_VALUE);
            sourcePortal.setOther(sourceDestination);
            sourcePortal.setPortalSide(spawner.getFacing());
            sourcePortal.setGunId(null);
            sourcePortal.setTransportSolutionType(type);
            sourcePortal.setSpawnerPos(sourceUpperPos);
            sourcePortal.setAutoClose(false);
            sourcePortal.forceResetManualClose();

            twinPortal.setTimeout(Integer.MAX_VALUE);
            twinPortal.setOther(twinDestination);
            twinPortal.setPortalSide(twinSpawner.getFacing());
            twinPortal.setGunId(null);
            twinPortal.setTransportSolutionType(type);
            twinPortal.setSpawnerPos(twinUpperPos);
            twinPortal.setAutoClose(false);
            twinPortal.forceResetManualClose();

            PortalSpawnerBlock.setPortalActive(sourceWorld, sourceUpperPos, true);
            PortalSpawnerBlock.setPortalActive(twinWorld, twinUpperPos, true);
            return true;
        } catch (Exception e) {
            MeeCreeps.setup.getLogger().error("Failed to create a linked Portal Spawner portal pair", e);
            return false;
        }
    }

    @Nullable
    private static PortalSpawnerBlock getLinkedSpawnerBlock(World world, BlockPos upperPos) {
        if (world == null || upperPos == null) return null;
        IBlockState state = world.getBlockState(upperPos);
        if (!(state.getBlock() instanceof PortalSpawnerBlock)
                || state.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.UPPER) return null;
        PortalSpawnerBlock block = (PortalSpawnerBlock) state.getBlock();
        return block.isLinked() ? block : null;
    }

    @Nullable
    private static PortalSpawnerTileEntity getSpawnerTile(World world, BlockPos pos) {
        if (world == null || pos == null) return null;
        TileEntity te = world.getTileEntity(pos);
        return te instanceof PortalSpawnerTileEntity ? (PortalSpawnerTileEntity) te : null;
    }

    public static void makePortalPair(World sourceWorld, BlockPos selectedBlock, EnumFacing selectedSide, TeleportDestination dest) {
        makePortalPair(sourceWorld, selectedBlock, selectedSide, dest, TransportSolutionType.INTERDIMENSIONAL);
    }

    public static void makePortalPair(World sourceWorld, BlockPos selectedBlock, EnumFacing selectedSide, TeleportDestination dest, TransportSolutionType transportSolutionType) {
        try {
            if (sourceWorld == null || selectedBlock == null || selectedSide == null || dest == null) return;
            TransportSolutionType type = transportSolutionType == null ? TransportSolutionType.INTERDIMENSIONAL : transportSolutionType;
            if (type.isIntradimensional() && dest.getDimension() != sourceWorld.provider.getDimension()) return;
            Validation validation = validateDestination(sourceWorld, dest);
            if (!validation.valid) return;
            World destWorld = validation.world;
            BlockPos sourcePortalPos = findBestPosition(sourceWorld, selectedBlock, selectedSide);
            if (sourcePortalPos == null) return;
            if (destWorld.getBlockState(dest.getPos()).getBlock() == ModBlocks.portalBlock) return;
            loadChunkArea(destWorld, dest.getPos(), dest.shouldSearchNearby() ? 1 : 0);

            SafeDestination safeDestination = dest.shouldSearchNearby()
                    ? findSafeDestination(destWorld, dest.getPos(), dest.getSide())
                    : (isValidSavedDestinationPlacement(destWorld, dest.getPos(), dest.getSide())
                        ? new SafeDestination(dest.getPos(), dest.getSide()) : null);
            if (safeDestination == null) return;
            if (sourceWorld.provider.getDimension() == destWorld.provider.getDimension() && sourcePortalPos.equals(safeDestination.pos)) return;
            TeleportDestination finalDestination = new TeleportDestination(dest.getName(), dest.getDimension(), safeDestination.pos, safeDestination.side, dest.shouldSearchNearby());
            createPair(sourceWorld, sourcePortalPos, selectedSide, destWorld, finalDestination, null, true, false, type);
        } catch (Exception e) {
            MeeCreeps.setup.getLogger().error("Failed to create a Portal Gun portal pair", e);
        }
    }

    private static boolean createPair(World sourceWorld, BlockPos sourcePortalPos, EnumFacing sourceSide, World destWorld, TeleportDestination destination, java.util.UUID gunId, boolean autoClose) {
        return createPair(sourceWorld, sourcePortalPos, sourceSide, destWorld, destination, gunId, autoClose, false, TransportSolutionType.INTERDIMENSIONAL, null, false);
    }

    private static boolean createPair(World sourceWorld, BlockPos sourcePortalPos, EnumFacing sourceSide, World destWorld, TeleportDestination destination, java.util.UUID gunId, boolean autoClose, boolean allowAirSource, TransportSolutionType transportSolutionType) {
        return createPair(sourceWorld, sourcePortalPos, sourceSide, destWorld, destination, gunId, autoClose, allowAirSource, transportSolutionType, null, false);
    }

    private static boolean createPair(World sourceWorld, BlockPos sourcePortalPos, EnumFacing sourceSide, World destWorld, TeleportDestination destination, java.util.UUID gunId, boolean autoClose, boolean allowAirSource, TransportSolutionType transportSolutionType, @Nullable BlockPos spawnerUpperPos, boolean allowPortalSpawnerSource) {
        boolean validSource;
        if (allowPortalSpawnerSource) {
            validSource = false;
            if (sourceWorld != null && sourcePortalPos != null && spawnerUpperPos != null) {
                IBlockState lowerState = sourceWorld.getBlockState(sourcePortalPos);
                IBlockState upperState = sourceWorld.getBlockState(spawnerUpperPos);
                validSource = lowerState.getBlock() instanceof PortalSpawnerBlock
                        && upperState.getBlock() == lowerState.getBlock()
                        && lowerState.getValue(PortalSpawnerBlock.HALF) == PortalSpawnerBlock.Half.LOWER
                        && upperState.getValue(PortalSpawnerBlock.HALF) == PortalSpawnerBlock.Half.UPPER
                        && sourceWorld.getTileEntity(spawnerUpperPos) != null;
            }
        } else {
            validSource = allowAirSource ? isValidAirSourcePlacement(sourceWorld, sourcePortalPos) : isValidSourcePlacement(sourceWorld, sourcePortalPos);
        }
        if (!validSource) return false;

        if (destination.shouldSearchNearby()) {
            if (!isValidCustomDestinationPlacement(destWorld, destination.getPos(), destination.getSide())) return false;
        } else if (!isValidSavedDestinationPlacement(destWorld, destination.getPos(), destination.getSide())) {
            return false;
        }

        boolean replacingSpawner = allowPortalSpawnerSource;
        if (replacingSpawner) PortalSpawnerBlock.beginPortalReplacement();
        try {
            sourceWorld.setBlockState(sourcePortalPos, ModBlocks.portalBlock.getDefaultState(), 3);
            if (spawnerUpperPos != null) {
                sourceWorld.notifyLightSet(spawnerUpperPos);
            }
        } finally {
            if (replacingSpawner) PortalSpawnerBlock.endPortalReplacement();
        }
        destWorld.setBlockState(destination.getPos(), ModBlocks.portalBlock.getDefaultState(), 3);

        TileEntity sourceTe = sourceWorld.getTileEntity(sourcePortalPos);
        TileEntity destinationTe = destWorld.getTileEntity(destination.getPos());
        if (!(sourceTe instanceof PortalTileEntity) || !(destinationTe instanceof PortalTileEntity)) {
            if (sourceWorld.getBlockState(sourcePortalPos).getBlock() == ModBlocks.portalBlock) sourceWorld.setBlockToAir(sourcePortalPos);
            if (destWorld.getBlockState(destination.getPos()).getBlock() == ModBlocks.portalBlock) destWorld.setBlockToAir(destination.getPos());
            if (spawnerUpperPos != null) {
                if (PortalSpawnerBlock.restoreLowerSection(sourceWorld, spawnerUpperPos)) {
                    sourceWorld.notifyLightSet(spawnerUpperPos);
                }
            }
            return false;
        }

        PortalTileEntity source = (PortalTileEntity) sourceTe;
        PortalTileEntity target = (PortalTileEntity) destinationTe;
        TeleportDestination sourceDestination = new TeleportDestination("", sourceWorld.provider.getDimension(), sourcePortalPos, sourceSide, false);

        source.setTimeout(autoClose ? ConfigSetup.portalTimeout.get() : Integer.MAX_VALUE);
        source.setOther(destination);
        source.setPortalSide(sourceSide);
        source.setGunId(gunId);
        source.setTransportSolutionType(transportSolutionType == null ? TransportSolutionType.INTERDIMENSIONAL : transportSolutionType);
        if (spawnerUpperPos != null) source.setSpawnerPos(spawnerUpperPos);
        source.setAutoClose(autoClose);
        source.forceResetManualClose();

        target.setTimeout(autoClose ? ConfigSetup.portalTimeout.get() : Integer.MAX_VALUE);
        target.setOther(sourceDestination);
        target.setPortalSide(destination.getSide());
        target.setGunId(gunId);
        target.setTransportSolutionType(transportSolutionType == null ? TransportSolutionType.INTERDIMENSIONAL : transportSolutionType);
        target.setAutoClose(autoClose);
        target.forceResetManualClose();
        return true;
    }

    private static Validation validateDestination(World currentWorld, TeleportDestination dest) {
        if (dest == null || dest.getPos() == null || dest.getSide() == null) return Validation.invalid(DestinationParser.INVALID_DESTINATION);
        if (!DimensionManager.isDimensionRegistered(dest.getDimension())) return Validation.invalid(DestinationParser.INVALID_DIMENSION);
        World world = resolveDestinationWorld(dest.getDimension());
        if (world == null) return Validation.invalid(DestinationParser.INVALID_DIMENSION);
        return Validation.valid(world);
    }

    @Nullable
    public static World resolveDestinationWorld(int dimension) {
        if (!DimensionManager.isDimensionRegistered(dimension)) return null;
        try {
            World world = DimensionManager.getWorld(dimension);
            if (world == null) {
                DimensionManager.initDimension(dimension);
                world = DimensionManager.getWorld(dimension);
            }
            return world;
        } catch (Exception e) {
            MeeCreeps.setup.getLogger().debug("Unable to resolve dimension " + dimension, e);
            return null;
        }
    }

    private static void loadChunkArea(World world, BlockPos center, int chunkRadius) {
        if (world == null || center == null) return;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = cx + dx;
                int chunkZ = cz + dz;

                if (world instanceof WorldServer) {
                    ((WorldServer) world).getChunkProvider().provideChunk(chunkX, chunkZ);
                } else {
                    world.getChunkFromChunkCoords(chunkX, chunkZ);
                }
            }
        }
    }

    private static void sendDestinationError(EntityPlayer player, String text) {
        if (player instanceof EntityPlayerMP) {
            player.sendMessage(new net.minecraft.util.text.TextComponentString(text).setStyle(new net.minecraft.util.text.Style().setColor(net.minecraft.util.text.TextFormatting.RED)));
        }
    }

    private static void showBalloon(EntityPlayer player, String key) {
        if (player instanceof EntityPlayerMP) MeeCreepsMessages.INSTANCE.sendTo(new PacketShowBalloonToClient(key), (EntityPlayerMP) player);
    }

    private static boolean isHazardousCustomDestination(World world, BlockPos pos, EnumFacing side) {
        if (world == null || pos == null || side == null) return false;
        try {

            BlockPos[] checks = new BlockPos[] {
                    pos.down(), pos.down().east(), pos.down().west(), pos.down().north(), pos.down().south()
            };
            for (BlockPos check : checks) {
                IBlockState state = world.getBlockState(check);
                if (state.getMaterial().isLiquid() && (state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.FLOWING_LAVA)) {
                    return true;
                }
            }

            if (side.getAxis().isHorizontal()) {
                BlockPos front = pos.offset(side);
                for (int dy = -1; dy <= 1; dy++) {
                    IBlockState state = world.getBlockState(front.up(dy));
                    if (state.getMaterial().isLiquid() && (state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.FLOWING_LAVA)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean canPlacePortal(World world, BlockPos pos) {
        if (world.isAirBlock(pos)) return true;
        return world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }

    private static boolean canCollideWith(World world, BlockPos pos) {
        if (world.isAirBlock(pos)) return false;
        IBlockState state = world.getBlockState(pos);
        return state.getBlock().getCollisionBoundingBox(state, world, pos) != null;
    }

    private static boolean isValidSourcePlacement(World world, BlockPos pos) {
        return world != null && pos != null && world.getBlockState(pos).getBlock() != ModBlocks.portalBlock && canPlacePortal(world, pos);
    }

    private static boolean isValidAirSourcePlacement(World world, BlockPos pos) {
        return world != null && pos != null && pos.getY() >= 1 && pos.getY() <= 254
                && world.getBlockState(pos).getBlock() != ModBlocks.portalBlock && canPlacePortal(world, pos);
    }

    private static boolean isValidSavedDestinationPlacement(World world, BlockPos pos, EnumFacing side) {
        if (world == null || pos == null || side == null) return false;
        if (world.getBlockState(pos).getBlock() == ModBlocks.portalBlock) return false;
        if (side == EnumFacing.DOWN) {
            return canPlacePortal(world, pos) && !canCollideWith(world, pos.down());
        } else {
            return canPlacePortal(world, pos) && !canCollideWith(world, pos.up());
        }
    }

    private static boolean isValidCustomDestinationPlacement(World world, BlockPos pos, EnumFacing side) {
        if (world == null || pos == null || side == null) return false;
        if (world.getBlockState(pos).getBlock() == ModBlocks.portalBlock) return false;
        IBlockState portalState = world.getBlockState(pos);
        if (portalState.getMaterial().isLiquid() || portalState.getBlock() == Blocks.FIRE || portalState.getBlock() == Blocks.FLOWING_LAVA || portalState.getBlock() == Blocks.LAVA) return false;
        if (!canPlacePortal(world, pos)) return false;

        BlockPos support = pos.offset(side.getOpposite());
        if (!canCollideWith(world, support)) return false;

        AxisAlignedBB arrival = getArrivalBox(pos, side);
        return world.getCollisionBoxes(null, arrival).isEmpty();
    }

    private static AxisAlignedBB getArrivalBox(BlockPos pos, EnumFacing side) {
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        double minY;
        double maxY;
        if (side == EnumFacing.DOWN) {
            minY = pos.getY() - 2.0;
            maxY = pos.getY() - 0.2;
        } else {
            minY = pos.getY();
            maxY = pos.getY() + 1.8;
        }

        if (side.getAxis().isHorizontal()) {
            cx += side.getFrontOffsetX() * 0.45;
            cz += side.getFrontOffsetZ() * 0.45;
        }
        return new AxisAlignedBB(cx - 0.30, minY, cz - 0.30, cx + 0.30, maxY, cz + 0.30);
    }

    @Nullable
    private static SafeDestination findSafeDestinationNearPlayer(World world, BlockPos reference, EnumFacing preferredSide) {
        if (world == null || reference == null || preferredSide == null) return null;

        final int radius = 5;
        final double minDistanceSq = 4.0D;
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double horizontalSq = dx * dx + dz * dz;
                if (horizontalSq > radius * radius || horizontalSq < minDistanceSq) continue;
                candidates.add(reference.add(dx, 0, dz));
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> {
            double dx = p.getX() - reference.getX();
            double dz = p.getZ() - reference.getZ();
            return dx * dx + dz * dz;
        }));

        EnumFacing[] order = new EnumFacing[] {
                preferredSide, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST,
                EnumFacing.DOWN
        };

        for (EnumFacing side : order) {
            for (BlockPos column : candidates) {
                BlockPos top = world.getTopSolidOrLiquidBlock(column);
                if (top != null) {
                    for (int dy = -5; dy <= 5; dy++) {
                        int y = top.getY() + dy;
                        if (y < 1 || y > 254) continue;
                        BlockPos candidate = new BlockPos(column.getX(), y, column.getZ());
                        if (dxDistanceSquared(candidate, reference) <= 25.0D
                                && isValidCustomDestinationPlacement(world, candidate, side)) {
                            return new SafeDestination(candidate, side);
                        }
                    }
                }

                for (int dy = -5; dy <= 5; dy++) {
                    int y = reference.getY() + dy;
                    if (y < 1 || y > 254) continue;
                    BlockPos candidate = new BlockPos(column.getX(), y, column.getZ());
                    double distanceSq = dxDistanceSquared(candidate, reference);
                    if (distanceSq <= 25.0D && isValidCustomDestinationPlacement(world, candidate, side)) {
                        return new SafeDestination(candidate, side);
                    }
                }
            }
        }
        return null;
    }

    private static double dxDistanceSquared(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @Nullable
    private static SafeDestination findSafeDestination(World world, BlockPos reference, EnumFacing preferredSide) {
        if (world == null || reference == null || preferredSide == null) return null;

        final int horizontalRadius = 8;
        final int minY = 1;
        final int maxY = 254;
        final EnumFacing[] order = new EnumFacing[] {
                preferredSide,
                EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST,
                EnumFacing.UP, EnumFacing.DOWN
        };

        SafeCandidate best = null;
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                if (dx * dx + dz * dz > horizontalRadius * horizontalRadius) continue;
                int x = reference.getX() + dx;
                int z = reference.getZ() + dz;

                for (int y = minY; y <= maxY; y++) {
                    double dy = y - reference.getY();
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    if (best != null && distanceSq > best.distanceSq) continue;

                    BlockPos candidate = new BlockPos(x, y, z);

                    for (int sideIndex = 0; sideIndex < order.length; sideIndex++) {
                        EnumFacing side = order[sideIndex];
                        if (!isValidCustomDestinationPlacement(world, candidate, side)) continue;
                        SafeCandidate found = new SafeCandidate(candidate, side, distanceSq, sideIndex);
                        if (best == null || found.isBetterThan(best)) best = found;
                        break;
                    }
                }
            }
        }

        return best == null ? null : new SafeDestination(best.pos, best.side);
    }

    private static class SafeCandidate {
        private final BlockPos pos;
        private final EnumFacing side;
        private final double distanceSq;
        private final int sidePriority;

        private SafeCandidate(BlockPos pos, EnumFacing side, double distanceSq, int sidePriority) {
            this.pos = pos;
            this.side = side;
            this.distanceSq = distanceSq;
            this.sidePriority = sidePriority;
        }

        private boolean isBetterThan(SafeCandidate other) {
            if (distanceSq != other.distanceSq) return distanceSq < other.distanceSq;
            return sidePriority < other.sidePriority;
        }
    }

    @Nullable
    public static BlockPos findSafeDestinationPosition(World world, BlockPos reference, EnumFacing preferredSide) {
        SafeDestination result = findSafeDestination(world, reference, preferredSide);
        return result == null ? null : result.pos;
    }

    @Nullable
    public static BlockPos findBestPosition(World world, BlockPos selectedBlock, EnumFacing selectedSide) {
        if (world == null || selectedBlock == null || selectedSide == null) return null;

        if (selectedSide == EnumFacing.UP) {
            if (world.isAirBlock(selectedBlock.up()) && world.isAirBlock(selectedBlock.up(2))) {
                return selectedBlock.up();
            }
            return null;
        }
        if (selectedSide == EnumFacing.DOWN) {
            if (world.isAirBlock(selectedBlock.down()) && world.isAirBlock(selectedBlock.down(2))) {
                return selectedBlock.down();
            }
            return null;
        }

        if (selectedSide == EnumFacing.EAST) {
            if (world.isAirBlock(selectedBlock.east()) && world.isAirBlock(selectedBlock.east(2))) {
                return selectedBlock.east();
            }
        }
        if (selectedSide == EnumFacing.WEST) {
            if (world.isAirBlock(selectedBlock.west()) && world.isAirBlock(selectedBlock.west(2))) {
                return selectedBlock.west();
            }
            return null;
        }
        if (selectedSide == EnumFacing.NORTH) {
            if (world.isAirBlock(selectedBlock.north()) && world.isAirBlock(selectedBlock.north(2))) {
                return selectedBlock.north();
            }
        }
        if (selectedSide == EnumFacing.SOUTH) {
            if (world.isAirBlock(selectedBlock.south()) && world.isAirBlock(selectedBlock.south(2))) {
                return selectedBlock.south();
            }
        }

        selectedBlock = selectedBlock.offset(selectedSide);
        if (world.isAirBlock(selectedBlock.down())) {
            selectedBlock = selectedBlock.down();
        }
        if (!world.isAirBlock(selectedBlock.down())) {
            return findBestPosition(world, selectedBlock.down(), EnumFacing.UP);
        }
        return null;
    }

    @Nullable
    public static BlockPos findSafeOriginPosition(World world, BlockPos selectedBlock, EnumFacing selectedSide) {
        return findBestPosition(world, selectedBlock, selectedSide);
    }

    public static void suppressTeleport(EntityPlayer player, PortalTileEntity destinationPortal) {
        if (player == null || destinationPortal == null || destinationPortal.getWorld() == null) return;
        TELEPORT_SUPPRESSION.put(player.getUniqueID(), new TeleportSuppression(destinationPortal));
    }

    public static boolean isTeleportSuppressed(EntityPlayer player, PortalTileEntity portal) {
        if (player == null || portal == null || portal.getWorld() == null) return false;
        UUID id = player.getUniqueID();
        TeleportSuppression suppression = TELEPORT_SUPPRESSION.get(id);
        if (suppression == null) return false;
        if (suppression.matches(player, portal)) return true;
        TELEPORT_SUPPRESSION.remove(id, suppression);
        return false;
    }

    public static void rememberOrigin(EntityPlayer player, PortalTileEntity source) {
        if (player == null || source == null || source.getPortalSide() == null) return;
        net.minecraft.nbt.NBTTagCompound c = new net.minecraft.nbt.NBTTagCompound();
        c.setInteger("dim", source.getWorld().provider.getDimension());
        c.setLong("pos", source.getPos().toLong());
        c.setByte("side", (byte) source.getPortalSide().ordinal());
        player.getEntityData().setTag(FAST_RETURN_TAG, c);
    }

    @Nullable
    public static TeleportDestination getFastReturnDestination(EntityPlayerMP player) {
        if (player == null) return null;
        if (!player.getEntityData().hasKey(FAST_RETURN_TAG, 10)) return null;
        net.minecraft.nbt.NBTTagCompound c = player.getEntityData().getCompoundTag(FAST_RETURN_TAG);
        if (!c.hasKey("dim") || !c.hasKey("pos") || !c.hasKey("side")) return null;
        int dim = c.getInteger("dim");
        if (!DimensionManager.isDimensionRegistered(dim)) return null;
        int side = c.getByte("side");
        if (side < 0 || side >= EnumFacing.VALUES.length) return null;
        return new TeleportDestination("Fast Return", dim, BlockPos.fromLong(c.getLong("pos")), EnumFacing.VALUES[side], false);
    }
}
