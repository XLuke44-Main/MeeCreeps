package mcjty.meecreeps.teleport;

import mcjty.meecreeps.actions.PacketShowBalloonToClient;
import mcjty.meecreeps.blocks.ModBlocks;
import mcjty.meecreeps.blocks.PortalTileEntity;
import mcjty.meecreeps.config.ConfigSetup;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.network.MeeCreepsMessages;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
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

    // Runtime-only arrival suppression. Never stored in NBT, so it cannot leak between
    // save files or survive a world unload. The WeakReference also prevents retaining an
    // unloaded World instance indefinitely.
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

    public static void cancelPortalPair(EntityPlayer player, BlockPos selectedBlock) {
        try {
            World sourceWorld = player.getEntityWorld();
            TileEntity te = sourceWorld.getTileEntity(selectedBlock);
            if (te instanceof PortalTileEntity) ((PortalTileEntity) te).setTimeout(10);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Returns true when the supplied single token names a player who is currently online.
     */
    public static boolean isOnlinePlayerLocator(EntityPlayerMP requester, String input) {
        return findOnlinePlayer(requester, input) != null;
    }

    /**
     * Resolve a single-token custom destination as an online-player locator. The location is
     * captured when the player submits the custom destination; it is never persisted as a saved
     * destination and is resolved only against players currently online.
     */
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
        makePortalPair(player, selectedBlock, selectedSide, dest, holder.gunId, holder.autoClose);
    }

    public static void makePortalPair(EntityPlayer player, BlockPos selectedBlock, EnumFacing selectedSide, TeleportDestination dest, java.util.UUID gunId, boolean autoClose) {
        try {
            if (player == null || player.getEntityWorld() == null || selectedBlock == null || selectedSide == null || dest == null) return;

            Validation validation = validateDestination(player.getEntityWorld(), dest);
            if (!validation.valid) {
                sendDestinationError(player, validation.message);
                return;
            }

            World sourceWorld = player.getEntityWorld();
            World destWorld = validation.world;

            // The source/origin portal always uses the original placement logic.
            // Custom destination Y is only used for the exit portal.
            BlockPos sourcePortalPos = findBestPosition(sourceWorld, selectedBlock, selectedSide);
            if (sourcePortalPos == null) {
                showBalloon(player, "message.meecreeps.cant_find_portal_spot");
                return;
            }

            // Preserve the original, more useful error for an already occupied saved/custom target.
            if (destWorld.getBlockState(dest.getPos()).getBlock() == ModBlocks.portalBlock) {
                showBalloon(player, "message.meecreeps.portal_already_there");
                return;
            }

            // The dimension and the destination chunk are guaranteed to be loaded BEFORE any
            // destination safety test is attempted.
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
                return;
            }

            if (sourceWorld.provider.getDimension() == destWorld.provider.getDimension() && sourcePortalPos.equals(safeDestination.pos)) {
                sendDestinationError(player, "NO SAFE DESTINATION LOCATION AVAILABLE!");
                return;
            }

            TeleportDestination finalDestination = new TeleportDestination(dest.getName(), dest.getDimension(), safeDestination.pos, safeDestination.side, dest.shouldSearchNearby());
            if (createPair(sourceWorld, sourcePortalPos, selectedSide, destWorld, finalDestination, gunId, autoClose)) {
                if (dest.shouldSearchNearby() && isHazardousCustomDestination(destWorld, finalDestination.getPos(), finalDestination.getSide())) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "WARNING: CUSTOM DESTINATION IS DANGEROUS!"
                    ).setStyle(new net.minecraft.util.text.Style().setColor(net.minecraft.util.text.TextFormatting.YELLOW)));
                }
                PortalGunItem.clearOneShotDestination(PortalGunItem.getGun(player));
            }
        } catch (Throwable t) {
            sendDestinationError(player, "PORTAL CREATION FAILED SAFELY.");
        }
    }

    private static ItemStackHolder getPlayerGunState(EntityPlayer player) {
        if (player == null) return new ItemStackHolder(null, true);
        net.minecraft.item.ItemStack gun = PortalGunItem.getGun(player);
        if (gun.isEmpty()) return new ItemStackHolder(null, true);
        return new ItemStackHolder(PortalGunItem.getGunId(gun), PortalGunItem.isAutoCloseEnabled(gun));
    }

    private static class ItemStackHolder {
        private final java.util.UUID gunId;
        private final boolean autoClose;
        private ItemStackHolder(java.util.UUID gunId, boolean autoClose) { this.gunId = gunId; this.autoClose = autoClose; }
    }

    public static void makePortalPair(World sourceWorld, BlockPos selectedBlock, EnumFacing selectedSide, TeleportDestination dest) {
        try {
            if (sourceWorld == null || selectedBlock == null || selectedSide == null || dest == null) return;
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
            createPair(sourceWorld, sourcePortalPos, selectedSide, destWorld, finalDestination, null, true);
        } catch (Throwable ignored) {
        }
    }

    private static boolean createPair(World sourceWorld, BlockPos sourcePortalPos, EnumFacing sourceSide, World destWorld, TeleportDestination destination, java.util.UUID gunId, boolean autoClose) {
        if (!isValidSourcePlacement(sourceWorld, sourcePortalPos)) return false;
        if (destination.shouldSearchNearby()) {
            if (!isValidCustomDestinationPlacement(destWorld, destination.getPos(), destination.getSide())) return false;
        } else if (!isValidSavedDestinationPlacement(destWorld, destination.getPos(), destination.getSide())) {
            return false;
        }

        sourceWorld.setBlockState(sourcePortalPos, ModBlocks.portalBlock.getDefaultState(), 3);
        destWorld.setBlockState(destination.getPos(), ModBlocks.portalBlock.getDefaultState(), 3);

        TileEntity sourceTe = sourceWorld.getTileEntity(sourcePortalPos);
        TileEntity destinationTe = destWorld.getTileEntity(destination.getPos());
        if (!(sourceTe instanceof PortalTileEntity) || !(destinationTe instanceof PortalTileEntity)) {
            if (sourceWorld.getBlockState(sourcePortalPos).getBlock() == ModBlocks.portalBlock) sourceWorld.setBlockToAir(sourcePortalPos);
            if (destWorld.getBlockState(destination.getPos()).getBlock() == ModBlocks.portalBlock) destWorld.setBlockToAir(destination.getPos());
            return false;
        }

        PortalTileEntity source = (PortalTileEntity) sourceTe;
        PortalTileEntity target = (PortalTileEntity) destinationTe;
        TeleportDestination sourceDestination = new TeleportDestination("", sourceWorld.provider.getDimension(), sourcePortalPos, sourceSide, false);

        source.setTimeout(autoClose ? ConfigSetup.portalTimeout.get() : Integer.MAX_VALUE);
        source.setOther(destination);
        source.setPortalSide(sourceSide);
        source.setGunId(gunId);
        source.setAutoClose(autoClose);
        source.forceResetManualClose();

        target.setTimeout(autoClose ? ConfigSetup.portalTimeout.get() : Integer.MAX_VALUE);
        target.setOther(sourceDestination);
        target.setPortalSide(destination.getSide());
        target.setGunId(gunId);
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

    /** Resolve and, when necessary, initialize a registered dimension. */
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
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ensure chunks around a destination reference are loaded before any safety inspection. */
    private static void loadChunkArea(World world, BlockPos center, int chunkRadius) {
        if (world == null || center == null) return;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = cx + dx;
                int chunkZ = cz + dz;
                // On the server this explicitly asks the chunk provider for the generated
                // chunk rather than relying on an already-loaded chunk. This is important for
                // first-use custom destinations in previously unvisited terrain.
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
            // The most important player-facing hazard is liquid directly underneath the arrival
            // area.  Check the block beneath the portal and the immediately adjacent footprint.
            BlockPos[] checks = new BlockPos[] {
                    pos.down(), pos.down().east(), pos.down().west(), pos.down().north(), pos.down().south()
            };
            for (BlockPos check : checks) {
                IBlockState state = world.getBlockState(check);
                if (state.getMaterial().isLiquid() && (state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.FLOWING_LAVA)) {
                    return true;
                }
            }
            // Wall-mounted portals can also place the arrival volume immediately beside lava.
            if (side.getAxis().isHorizontal()) {
                BlockPos front = pos.offset(side);
                for (int dy = -1; dy <= 1; dy++) {
                    IBlockState state = world.getBlockState(front.up(dy));
                    if (state.getMaterial().isLiquid() && (state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.FLOWING_LAVA)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
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

    /**
     * Validation for an already-saved destination. Keep this deliberately compatible with the
     * original MeeCreeps portal rules so existing saved destinations remain usable.
     */
    /** Preserve the original exact-placement rules for saved destinations. */
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

        // For wall portals, bias the arrival position toward the open side of the wall.
        if (side.getAxis().isHorizontal()) {
            cx += side.getFrontOffsetX() * 0.45;
            cz += side.getFrontOffsetZ() * 0.45;
        }
        return new AxisAlignedBB(cx - 0.30, minY, cz - 0.30, cx + 0.30, maxY, cz + 0.30);
    }

    /**
     * Find a custom portal position no more than five blocks from a player's captured location.
     * Keep a minimum horizontal separation so the locator portal cannot immediately catch the
     * player who was located.
     */
    @Nullable
    private static SafeDestination findSafeDestinationNearPlayer(World world, BlockPos reference, EnumFacing preferredSide) {
        if (world == null || reference == null || preferredSide == null) return null;

        final int radius = 5;
        final double minDistanceSq = 4.0D; // at least 2 blocks away horizontally
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

                // Also inspect around the player's requested Y, but never allow the selected
                // portal to drift more than five blocks vertically from the captured position.
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

        // Custom destinations are proximity searches around the requested coordinates. Y is a
        // reference height, not an exact placement instruction. Evaluate every nearby candidate
        // against the full 3D distance so a safe underground room wins over a much farther surface
        // or Nether roof location.
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
                    // At one position every orientation has the same destination distance, so
                    // honor the orientation priority and stop at the first safe side.
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

    /** Compatibility helper: returns the selected safe position, preserving the preferred side
     * whenever possible. */
    @Nullable
    public static BlockPos findSafeDestinationPosition(World world, BlockPos reference, EnumFacing preferredSide) {
        SafeDestination result = findSafeDestination(world, reference, preferredSide);
        return result == null ? null : result.pos;
    }

    /** The original source/origin placement rules. Custom destinations must never modify this. */
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

    /** Compatibility alias used by older GUI code. */
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
