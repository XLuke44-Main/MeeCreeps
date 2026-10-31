package mcjty.meecreeps.blocks;

import mcjty.meecreeps.items.TransportSolutionType;
import mcjty.meecreeps.teleport.TeleportDestination;
import mcjty.meecreeps.config.DimensionGroups;
import mcjty.meecreeps.teleport.TeleportationTools;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraft.util.math.ChunkPos;
import mcjty.meecreeps.MeeCreeps;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class PortalSpawnerTileEntity extends TileEntity implements ITickable {

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }

    private TeleportDestination destination;
    @Nullable
    private String customSpawnerName;
    private boolean redstoneStateKnown;
    private boolean lastRedstonePowered;
    private boolean lastClientActive;
    private boolean forceCloseLockout;
    private int destinationRevision;

    private UUID linkedPairId;
    private Integer linkedTwinDimension;
    private BlockPos linkedTwinPos;

    @Nullable
    private ForgeChunkManager.Ticket chunkTicket;

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {

        return oldState == null || newState == null || oldState.getBlock() != newState.getBlock();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof PortalSpawnerBlock
                    && state.getValue(PortalSpawnerBlock.HALF) == PortalSpawnerBlock.Half.UPPER) {
                PortalSpawnerBlock block = (PortalSpawnerBlock) state.getBlock();
                if (block.isLinked()) {
                    PortalSpawnerRegistry.register(this);
                }
                if (block.isLinked() || isPortalPresent()) {
                    ensureChunkLoaded();
                }
            }
        }
    }

    @Override
    public void onChunkUnload() {
        PortalSpawnerRegistry.unregister(this);
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        PortalSpawnerRegistry.unregister(this);
        super.invalidate();
    }

    @Override
    public void update() {
        if (world == null) return;

        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof PortalSpawnerBlock)
                || state.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.UPPER) {
            return;
        }

        if (world.isRemote) {
            boolean active = state.getValue(PortalSpawnerBlock.ACTIVE);
            if (active != lastClientActive) {
                lastClientActive = active;
                world.notifyLightSet(pos.down());
                world.notifyLightSet(pos);
                world.markBlockRangeForRenderUpdate(pos.down().add(-1, -1, -1), pos.up().add(1, 1, 1));
                world.checkLight(pos.down());
                world.checkLight(pos);
                world.checkLight(pos.up());
            }
            return;
        }

        PortalSpawnerBlock.setPortalActive(world, pos, isPortalPresent());

        PortalSpawnerBlock block = (PortalSpawnerBlock) state.getBlock();

        if (block.isLinked() || isPortalPresent()) {
            ensureChunkLoaded();
        } else {
            releaseChunkTicket();
        }

        boolean powered = isLocallyPowered();
        boolean wasPowered = redstoneStateKnown && lastRedstonePowered;

        if (block.isLinked() && !isIncomingPortal()) {
            updateLinkedSpawner(powered, wasPowered);
            return;
        }

        updateSpawnerPower(powered, wasPowered, !block.isLinked());
    }

    private void updateSpawnerPower(boolean powered, boolean wasPowered, boolean mayOpenConfiguredDestination) {
        redstoneStateKnown = true;
        lastRedstonePowered = powered;

        if (!powered && forceCloseLockout && !isPortalPresent()) {
            forceCloseLockout = false;
            markDirty();
        }

        if (powered) {
            if (forceCloseLockout) return;
            if (isPortalPresent()) {
                holdPortalOpen();
            } else if (mayOpenConfiguredDestination) {
                openPortal();
            }
        } else if (wasPowered && isPortalPresent()) {

            closePortal();
        }
    }

    private void updateLinkedSpawner(boolean powered, boolean wasPowered) {
        if (!isLinkedPairReady()) {
            if (isPortalPresent() && !isIncomingPortal()) closePortal();
            redstoneStateKnown = true;
            lastRedstonePowered = powered;
            return;
        }

        PortalSpawnerTileEntity twin = getLinkedTwin();
        if (twin == null) return;

        boolean twinPowered = twin != null && twin.isLocallyPowered();
        boolean pairWasPowered = wasPowered || (twin.redstoneStateKnown && twin.lastRedstonePowered);
        boolean pairPowered = powered || twinPowered;

        setRedstoneState(powered);
        twin.setRedstoneState(twinPowered);

        if (!pairPowered && forceCloseLockout && !isPortalPresent()) {
            forceCloseLockout = false;
            markDirty();
        }
        if (!pairPowered && twin.forceCloseLockout && !twin.isPortalPresent()) {
            twin.forceCloseLockout = false;
            twin.markDirty();
        }

        if (pairPowered) {
            if (forceCloseLockout || twin.forceCloseLockout) return;
            if (isLinkedPortalPairPresent()) {
                holdPortalOpen();
            } else if (!isPortalPresent() && !twin.isPortalPresent()) {
                if (openLinkedPortal()) {
                    setRedstoneState(powered);
                    twin.setRedstoneState(twinPowered);
                }
            }
        } else if (pairWasPowered && isLinkedPortalPairPresent()) {
            closePortal();
        }
    }

    private void setRedstoneState(boolean powered) {
        redstoneStateKnown = true;
        lastRedstonePowered = powered;
        markDirty();
    }

    @Nullable
    private PortalSpawnerTileEntity getLinkedTwin() {
        if (linkedTwinDimension == null || linkedTwinPos == null || world == null) return null;
        World twinWorld = TeleportationTools.resolveDestinationWorld(linkedTwinDimension);
        if (twinWorld == null) return null;
        twinWorld.getChunkFromBlockCoords(linkedTwinPos);
        TileEntity te = twinWorld.getTileEntity(linkedTwinPos);
        return te instanceof PortalSpawnerTileEntity ? (PortalSpawnerTileEntity) te : null;
    }

    private boolean isIncomingPortal() {
        PortalTileEntity portal = getPortalTile();
        if (portal == null || !pos.equals(portal.getSpawnerPos())) return false;
        Optional<PortalTileEntity> other = portal.getOtherPortal();
        if (!other.isPresent()) return false;

        return other.get().getSpawnerPos() == null || portal.getGunId() != null;
    }

    private void openPortal() {
        if (destination == null || isPortalPresent()) return;
        if (!isDestinationAllowed()) return;
        if (TeleportationTools.makePortalPairFromSpawner(this)) {
            PortalSpawnerBlock.setPortalActive(world, pos, true);
            ensureChunkLoaded();
        }
    }

    private boolean openLinkedPortal() {
        if (isLinkedPortalPairPresent()) return false;
        if (!isLinkedPairReady()) return false;
        if (TeleportationTools.makeLinkedPortalPair(this)) {
            ensureChunkLoaded();
            return true;
        }
        return false;
    }

    private void closePortal() {
        PortalTileEntity portal = getPortalTile();
        if (portal != null && !portal.isManualCloseRequested()) {
            portal.beginSpawnerShutdown();
        }
    }

    private void holdPortalOpen() {
        PortalTileEntity portal = getPortalTile();
        if (portal != null && !portal.holdOpenForSpawner()) {
            forceCloseLockout = true;
            markDirty();
        }
    }
    public boolean canCaptureProjectile() {
        if (world == null || world.isRemote || isInvalid()) return false;
        IBlockState upperState = world.getBlockState(pos);
        IBlockState lowerState = world.getBlockState(pos.down());
        return upperState.getBlock() instanceof PortalSpawnerBlock
                && upperState.getValue(PortalSpawnerBlock.HALF) == PortalSpawnerBlock.Half.UPPER
                && lowerState.getBlock() == upperState.getBlock()
                && lowerState.getValue(PortalSpawnerBlock.HALF) == PortalSpawnerBlock.Half.LOWER
                && world.getTileEntity(pos) == this
                && world.getTileEntity(pos.down()) == null
                && !isPortalPresent();
    }
    public boolean captureProjectileDestination(@Nullable TeleportDestination projectileDestination,
                                                @Nullable UUID projectileGunId,
                                                @Nullable TransportSolutionType projectileTransportType) {
        if (!canCaptureProjectile() || projectileDestination == null) return false;
        if (!TeleportationTools.makeCapturedPortalPair(this, projectileDestination, projectileGunId,
                projectileTransportType)) {
            return false;
        }
        onIncomingPortalCreated();
        return true;
    }
    public void onIncomingPortalCreated() {
        if (world == null || world.isRemote) return;
        forceCloseLockout = false;
        boolean powered = isLocallyPowered();
        redstoneStateKnown = true;
        lastRedstonePowered = powered;
        PortalSpawnerBlock.setPortalActive(world, pos, isPortalPresent());
        ensureChunkLoaded();
        if (powered) holdPortalOpen();
        markDirty();
    }

    public void onPortalForceClosed() {
        if (world == null || world.isRemote) return;
        forceCloseLockout = true;
        markDirty();
    }

    private boolean isLocallyPowered() {
        return world != null && !world.isRemote
                && (world.isBlockPowered(pos) || world.isBlockPowered(pos.down()));
    }

    public boolean hasRedstonePower() {
        if (isLocallyPowered()) return true;

        if (isLinked() && !isIncomingPortal()) {
            PortalSpawnerTileEntity twin = getLinkedTwin();
            return twin != null && twin.isLocallyPowered();
        }
        return false;
    }

    private boolean isDestinationAllowed() {
        if (destination == null || destination.getDimension() == null || world == null) return false;
        TransportSolutionType type = getTransportSolutionType();
        return DimensionGroups.isDestinationAllowed(type, world.provider.getDimension(), destination.getDimension());
    }

    private boolean isPortalPresent() {
        return world != null && world.getBlockState(pos.down()).getBlock() == ModBlocks.portalBlock
                && world.getTileEntity(pos.down()) instanceof PortalTileEntity;
    }

    private boolean isLinkedPortalPairPresent() {
        if (!isPortalPresent() || linkedTwinDimension == null || linkedTwinPos == null) return false;
        World twinWorld = TeleportationTools.resolveDestinationWorld(linkedTwinDimension);
        if (twinWorld == null) return false;
        TileEntity twinPortalTe = twinWorld.getTileEntity(linkedTwinPos.down());
        return twinPortalTe instanceof PortalTileEntity;
    }

    public boolean isLinkedPairReady() {
        if (!isLinked()) return false;
        if (linkedPairId == null || linkedTwinDimension == null || linkedTwinPos == null) return false;
        if (world == null) return false;

        TransportSolutionType type = getTransportSolutionType();
        if (!DimensionGroups.isDestinationAllowed(type, world.provider.getDimension(), linkedTwinDimension)
                || !DimensionGroups.isDestinationAllowed(type, linkedTwinDimension, world.provider.getDimension())) {
            return false;
        }
        World twinWorld = TeleportationTools.resolveDestinationWorld(linkedTwinDimension);
        if (twinWorld == null) return false;
        twinWorld.getChunkFromBlockCoords(linkedTwinPos);

        IBlockState twinState = twinWorld.getBlockState(linkedTwinPos);
        if (!(twinState.getBlock() instanceof PortalSpawnerBlock)
                || twinState.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.UPPER
                || twinState.getBlock() != world.getBlockState(pos).getBlock()) {
            return false;
        }
        TileEntity te = twinWorld.getTileEntity(linkedTwinPos);
        if (!(te instanceof PortalSpawnerTileEntity)) return false;
        PortalSpawnerTileEntity twin = (PortalSpawnerTileEntity) te;
        return linkedPairId.equals(twin.linkedPairId)
                && twin.linkedTwinDimension != null
                && twin.linkedTwinPos != null
                && world.provider.getDimension() == twin.linkedTwinDimension
                && pos.equals(twin.linkedTwinPos);
    }

    public boolean isLinked() {
        if (world == null) return false;
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof PortalSpawnerBlock
                && ((PortalSpawnerBlock) state.getBlock()).isLinked();
    }

    @Nullable
    private PortalTileEntity getPortalTile() {
        if (!isPortalPresent()) return null;
        TileEntity te = world.getTileEntity(pos.down());
        return te instanceof PortalTileEntity ? (PortalTileEntity) te : null;
    }

    public EnumFacing getFacing() {
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof PortalSpawnerBlock) {
                return state.getValue(PortalSpawnerBlock.FACING);
            }
        }
        return EnumFacing.NORTH;
    }

    public TransportSolutionType getTransportSolutionType() {
        IBlockState state = world == null ? null : world.getBlockState(pos);
        if (state != null && state.getBlock() instanceof PortalSpawnerBlock) {
            return ((PortalSpawnerBlock) state.getBlock()).getTransportSolutionType();
        }
        return TransportSolutionType.INTERDIMENSIONAL;
    }

    @Nullable
    public TeleportDestination getDestination() {
        return destination;
    }

    public boolean hasActivePortalConnection() {
        return isPortalPresent();
    }

    @Nullable
    public String getCustomSpawnerName() {
        return customSpawnerName;
    }

    public String getSpawnerDestinationName() {
        return customSpawnerName == null || customSpawnerName.trim().isEmpty()
                ? "Portal Spawner" : customSpawnerName;
    }

    public int getDestinationRevision() {
        return destinationRevision;
    }

    public boolean setDestination(@Nullable TeleportDestination destination) {

        if (isLinked() || hasActivePortalConnection()) return false;
        if (destination == null || destination.getDimension() == null
                || destination.getPos() == null || destination.getSide() == null) return false;
        if (!DimensionGroups.isDimensionAvailable(destination.getDimension()) || world == null
                || !DimensionGroups.isDestinationAllowed(getTransportSolutionType(),
                world.provider.getDimension(), destination.getDimension())) return false;
        this.destination = destination;
        destinationRevision++;
        markDirtyAndSync();
        return true;
    }

    public void onSpawnerBroken() {
        PortalSpawnerRegistry.unregister(this);
        releaseChunkTicket();
        PortalTileEntity portal = getPortalTile();
        if (portal == null) return;
        Optional<PortalTileEntity> other = portal.getOtherPortal();
        portal.killPortal();
        other.ifPresent(PortalTileEntity::killPortal);
    }

    public void setLinkedPairId(@Nullable UUID pairId) {
        PortalSpawnerRegistry.unregister(this);
        linkedPairId = pairId;
        PortalSpawnerRegistry.register(this);
        markDirtyAndSync();
    }

    @Nullable
    public UUID getLinkedPairId() {
        return linkedPairId;
    }

    public void setTwin(int dimension, BlockPos twinPos) {
        linkedTwinDimension = dimension;
        linkedTwinPos = twinPos;
        markDirtyAndSync();
    }

    @Nullable
    public Integer getTwinDimension() {
        return linkedTwinDimension;
    }

    @Nullable
    public BlockPos getTwinPos() {
        return linkedTwinPos;
    }

    public void ensureChunkLoadedForSpawner() {
        ensureChunkLoaded();
    }

    private void ensureChunkLoaded() {
        if (world == null || world.isRemote) return;

        try {
            if (chunkTicket == null) {
                chunkTicket = ForgeChunkManager.requestTicket(MeeCreeps.instance, world, ForgeChunkManager.Type.NORMAL);
                if (chunkTicket != null) {
                    PortalChunkLoadingCallback.prepareSpawnerTicket(chunkTicket, pos);
                }
            }
            if (chunkTicket != null) {
                ForgeChunkManager.forceChunk(chunkTicket, new ChunkPos(pos));
            }
        } catch (Exception e) {
            chunkTicket = null;
            MeeCreeps.setup.getLogger().warn("Failed to force the Portal Spawner chunk at " + pos, e);
        }

        if (chunkTicket == null) {
            try {
                world.getChunkFromBlockCoords(pos);
            } catch (Exception e) {
                MeeCreeps.setup.getLogger().warn("Failed to load the Portal Spawner chunk at " + pos, e);
            }
        }
    }

    private void releaseChunkTicket() {
        if (chunkTicket == null) return;
        try {
            ForgeChunkManager.releaseTicket(chunkTicket);
        } catch (Exception e) {
            MeeCreeps.setup.getLogger().warn("Failed to release the Portal Spawner chunk ticket at " + pos, e);
        }
        chunkTicket = null;
    }

    public void restoreChunkTicket(ForgeChunkManager.Ticket ticket) {
        if (world == null || world.isRemote || ticket == null) return;
        chunkTicket = ticket;
        PortalChunkLoadingCallback.prepareSpawnerTicket(ticket, pos);
        ForgeChunkManager.forceChunk(ticket, new ChunkPos(pos));
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    @Nullable
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, writeToNBT(new NBTTagCompound()));
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        destination = compound.hasKey("destination", 10)
                ? new TeleportDestination(compound.getCompoundTag("destination"))
                : null;

        if (compound.hasKey("display", 10)
                && compound.getCompoundTag("display").hasKey("Name", 8)) {
            customSpawnerName = compound.getCompoundTag("display").getString("Name");
        } else if (compound.hasKey("spawnerCustomName", 8)) {
            customSpawnerName = compound.getString("spawnerCustomName");
        } else {
            customSpawnerName = null;
        }
        redstoneStateKnown = compound.getBoolean("redstoneStateKnown");
        lastRedstonePowered = compound.getBoolean("lastRedstonePowered");
        forceCloseLockout = compound.getBoolean("forceCloseLockout");
        destinationRevision = compound.getInteger("destinationRevision");
        linkedPairId = compound.hasUniqueId(PortalSpawnerBlock.LINKED_PAIR_ID)
                ? compound.getUniqueId(PortalSpawnerBlock.LINKED_PAIR_ID) : null;
        linkedTwinDimension = compound.hasKey(PortalSpawnerBlock.LINKED_TWIN_DIM)
                ? compound.getInteger(PortalSpawnerBlock.LINKED_TWIN_DIM) : null;
        linkedTwinPos = compound.hasKey(PortalSpawnerBlock.LINKED_TWIN_POS)
                ? BlockPos.fromLong(compound.getLong(PortalSpawnerBlock.LINKED_TWIN_POS)) : null;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        if (destination != null) compound.setTag("destination", destination.getCompound());
        if (customSpawnerName != null && !customSpawnerName.trim().isEmpty()) {
            compound.setString("spawnerCustomName", customSpawnerName);
        } else {
            compound.removeTag("spawnerCustomName");
        }
        compound.setBoolean("redstoneStateKnown", redstoneStateKnown);
        compound.setBoolean("lastRedstonePowered", lastRedstonePowered);
        compound.setBoolean("forceCloseLockout", forceCloseLockout);
        compound.setInteger("destinationRevision", destinationRevision);
        if (linkedPairId != null) compound.setUniqueId(PortalSpawnerBlock.LINKED_PAIR_ID, linkedPairId);
        if (linkedTwinDimension != null && linkedTwinPos != null) {
            compound.setInteger(PortalSpawnerBlock.LINKED_TWIN_DIM, linkedTwinDimension);
            compound.setLong(PortalSpawnerBlock.LINKED_TWIN_POS, linkedTwinPos.toLong());
        }
        return super.writeToNBT(compound);
    }

}
