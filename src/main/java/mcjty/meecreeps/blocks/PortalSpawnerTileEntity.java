package mcjty.meecreeps.blocks;

import mcjty.meecreeps.items.TransportSolutionType;
import mcjty.meecreeps.teleport.TeleportDestination;
import mcjty.meecreeps.teleport.TeleportationTools;
import net.minecraft.block.BlockLever;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
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
    private boolean redstoneStateKnown;
    private boolean lastRedstonePowered;
    private boolean lastClientActive;

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

        if (block.isLinked()) {
            updateLinkedSpawner(block);
            return;
        }

        EnumFacing facing = state.getValue(PortalSpawnerBlock.FACING);
        LeverControl leverControl = getAttachedLeverControl(facing);
        if (leverControl.present) {
            redstoneStateKnown = true;
            lastRedstonePowered = leverControl.powered;
            if (leverControl.powered) {
                openPortal();
            } else {
                closePortal();
            }
            return;
        }

        boolean powered = world.isBlockIndirectlyGettingPowered(pos) > 0;
        if (!redstoneStateKnown) {
            redstoneStateKnown = true;
            lastRedstonePowered = powered;
            return;
        }

        if (powered && !lastRedstonePowered) {
            if (isPortalPresent()) {
                closePortal();
            } else {
                openPortal();
            }
        }
        lastRedstonePowered = powered;
    }

    private void updateLinkedSpawner(PortalSpawnerBlock block) {
        if (!isLinkedPairReady()) {
            if (isPortalPresent()) closePortal();
            redstoneStateKnown = true;
            lastRedstonePowered = false;
            return;
        }

        EnumFacing facing = world.getBlockState(pos).getValue(PortalSpawnerBlock.FACING);
        LeverControl leverControl = getAttachedLeverControl(facing);
        if (leverControl.present) {
            redstoneStateKnown = true;
            lastRedstonePowered = leverControl.powered;
            if (leverControl.powered) {
                openLinkedPortal();
            } else {
                closePortal();
            }
            return;
        }

        boolean powered = world.isBlockIndirectlyGettingPowered(pos) > 0;
        if (!redstoneStateKnown) {
            redstoneStateKnown = true;
            lastRedstonePowered = powered;
            return;
        }

        if (powered && !lastRedstonePowered) {
            if (isLinkedPortalPairPresent()) {
                closePortal();
            } else {
                if (openLinkedPortal()) {

                    markLinkedPairPulseConsumed();
                }
            }
        }
        lastRedstonePowered = powered;
    }

    private LeverControl getAttachedLeverControl(EnumFacing facing) {
        EnumFacing left = facing.rotateY();
        EnumFacing right = facing.rotateYCCW();
        LeverControl leftControl = getLeverAt(pos.offset(left));
        LeverControl rightControl = getLeverAt(pos.offset(right));
        if (!leftControl.present) return rightControl;
        if (!rightControl.present) return leftControl;
        return new LeverControl(true, leftControl.powered || rightControl.powered);
    }

    private LeverControl getLeverAt(BlockPos leverPos) {
        IBlockState state = world.getBlockState(leverPos);
        if (state.getBlock() != Blocks.LEVER) return LeverControl.NONE;
        return new LeverControl(true, state.getValue(BlockLever.POWERED));
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

    private void markLinkedPairPulseConsumed() {
        redstoneStateKnown = true;
        lastRedstonePowered = true;
        if (world == null || linkedTwinDimension == null || linkedTwinPos == null) return;
        World twinWorld = TeleportationTools.resolveDestinationWorld(linkedTwinDimension);
        if (twinWorld == null) return;
        TileEntity te = twinWorld.getTileEntity(linkedTwinPos);
        if (te instanceof PortalSpawnerTileEntity) {
            PortalSpawnerTileEntity twin = (PortalSpawnerTileEntity) te;
            twin.redstoneStateKnown = true;
            twin.lastRedstonePowered = true;
            twin.markDirty();
        }
        markDirty();
    }

    private void closePortal() {
        PortalTileEntity portal = getPortalTile();
        if (portal != null && !portal.isManualCloseRequested()) {
            portal.forceManualClose();
        }
    }

    private boolean isDestinationAllowed() {
        if (destination == null || world == null) return false;
        TransportSolutionType type = getTransportSolutionType();
        return !type.isIntradimensional() || destination.getDimension() == world.provider.getDimension();
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

    public boolean setDestination(@Nullable TeleportDestination destination) {
        if (isLinked()) return false;
        if (destination == null || destination.getPos() == null || destination.getSide() == null) return false;
        if (!DimensionManager.isDimensionRegistered(destination.getDimension())) return false;
        if (getTransportSolutionType().isIntradimensional()
                && world != null && destination.getDimension() != world.provider.getDimension()) return false;
        this.destination = destination;
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
        redstoneStateKnown = compound.getBoolean("redstoneStateKnown");
        lastRedstonePowered = compound.getBoolean("lastRedstonePowered");
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
        compound.setBoolean("redstoneStateKnown", redstoneStateKnown);
        compound.setBoolean("lastRedstonePowered", lastRedstonePowered);
        if (linkedPairId != null) compound.setUniqueId(PortalSpawnerBlock.LINKED_PAIR_ID, linkedPairId);
        if (linkedTwinDimension != null && linkedTwinPos != null) {
            compound.setInteger(PortalSpawnerBlock.LINKED_TWIN_DIM, linkedTwinDimension);
            compound.setLong(PortalSpawnerBlock.LINKED_TWIN_POS, linkedTwinPos.toLong());
        }
        return super.writeToNBT(compound);
    }

    private static class LeverControl {
        private static final LeverControl NONE = new LeverControl(false, false);
        private final boolean present;
        private final boolean powered;

        private LeverControl(boolean present, boolean powered) {
            this.present = present;
            this.powered = powered;
        }
    }
}
