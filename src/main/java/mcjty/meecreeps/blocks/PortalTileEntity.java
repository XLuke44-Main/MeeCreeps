package mcjty.meecreeps.blocks;

import mcjty.lib.varia.SoundTools;
import mcjty.meecreeps.teleport.TeleportationTools;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.config.ConfigSetup;
import mcjty.meecreeps.items.TransportSolutionType;
import mcjty.meecreeps.teleport.TeleportDestination;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

import javax.annotation.Nullable;
import java.util.*;

public class PortalTileEntity extends TileEntity implements ITickable {

    private int timeout;
    private boolean soundStart = false;
    private boolean soundEnd = false;
    private int start;
    private ForgeChunkManager.Ticket chunkTicket;
    private TeleportDestination other;
    private EnumFacing portalSide;
    private AxisAlignedBB box = null;
    private Set<UUID> blackListed = new HashSet<>();
    private boolean autoClose = true;
    private boolean manualCloseRequested = false;
    private UUID gunId;
    private TransportSolutionType transportSolutionType = TransportSolutionType.INTERDIMENSIONAL;
    private BlockPos spawnerPos;

    @Override
    public void update() {
        if (world.isRemote) return;
        try {
            ensureChunkLoaded();
            if (other == null || other.getPos() == null || other.getSide() == null || !DimensionManager.isDimensionRegistered(other.getDimension())) {
                killPortal();
                return;
            }
            if (transportSolutionType.isIntradimensional() && other.getDimension() != world.provider.getDimension()) {
                killPortal();
                return;
            }

            if (start < 10) {
                start++;
                markDirtyClient();
            }

            if (autoClose || timeout != Integer.MAX_VALUE) {
                tickTime();
                if (timeout <= 0) {
                    killPortal();
                    getOther().ifPresent(PortalTileEntity::killPortal);
                    return;
                }
                if ((!soundStart) && start >= 1) {
                    soundStart = true;
                    SoundEvent sound = SoundEvent.REGISTRY.getObject(new ResourceLocation(MeeCreeps.MODID, "portal"));
                    if (sound != null) SoundTools.playSound(world, sound, pos.getX(), pos.getY(), pos.getZ(), 1, 1);
                }
                if ((!soundEnd) && timeout < 10) {
                    soundEnd = true;
                    if (ConfigSetup.teleportVolume.get() > 0.01f) {
                        SoundEvent sound = SoundEvent.REGISTRY.getObject(new ResourceLocation(MeeCreeps.MODID, "portal"));
                        if (sound != null) SoundTools.playSound(world, sound, pos.getX(), pos.getY(), pos.getZ(), ConfigSetup.teleportVolume.get(), 1);
                    }
                }
            }

            Optional<PortalTileEntity> otherPortalOptional = getOther();
            if (!otherPortalOptional.isPresent()) return;
            PortalTileEntity otherPortal = otherPortalOptional.get();
            final double defaultOtherX = otherPortal.getPos().getX() + .5;
            final double defaultOtherY = otherPortal.getPos().getY() + .5;
            final double defaultOtherZ = otherPortal.getPos().getZ() + .5;

            blackListed.removeIf(uuid -> {
                Entity e = findEntity(uuid);
                return e == null || !getTeleportBox().intersects(e.getEntityBoundingBox());
            });

            List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, getTeleportBox());
            for (Entity entity : entities) {
                UUID uuid = entity.getUniqueID();
                if (blackListed.contains(uuid)) continue;
                if (entity instanceof EntityPlayer && mcjty.meecreeps.teleport.TeleportationTools.isTeleportSuppressed((EntityPlayer) entity, this)) continue;

                otherPortal.addBlackList(uuid);
                if (entity instanceof EntityPlayer) {
                    mcjty.meecreeps.teleport.TeleportationTools.rememberOrigin((EntityPlayer) entity, this);
                }
                double otherX = defaultOtherX;
                double otherY = defaultOtherY;
                double otherZ = defaultOtherZ;
                double oy = otherY;
                if (otherPortal.getPortalSide() == EnumFacing.DOWN) {
                    oy -= entity.height + .7;
                }
                TeleportationTools.teleportEntity(entity, otherPortal.getWorld(), otherX, oy, otherZ, portalSide, otherPortal.getPortalSide());
                if (entity instanceof EntityPlayer) {
                    mcjty.meecreeps.teleport.TeleportationTools.suppressTeleport((EntityPlayer) entity, otherPortal);
                }

                if (autoClose && !manualCloseRequested && !otherPortal.manualCloseRequested) {
                    setTimeout(ConfigSetup.portalTimeoutAfterEntry.get());
                    otherPortal.setTimeout(ConfigSetup.portalTimeoutAfterEntry.get());
                }
                if (entity instanceof EntityPlayer && ConfigSetup.teleportVolume.get() > 0.01f) {
                    SoundEvent sound = SoundEvent.REGISTRY.getObject(new ResourceLocation(MeeCreeps.MODID, "teleport"));
                    if (sound != null) SoundTools.playSound(otherPortal.getWorld(), sound, otherX, otherY, otherZ, ConfigSetup.teleportVolume.get(), 1);
                }
            }
        } catch (Exception e) {
            MeeCreeps.setup.getLogger().error("Portal tick failed; closing portal at " + pos, e);
            try {
                killPortal();
            } catch (Exception closeException) {
                MeeCreeps.setup.getLogger().error("Failed to close a portal after a portal tick error at " + pos, closeException);
            }
        }
    }

    @Nullable
    private Entity findEntity(UUID uuid) {
        Entity player = world.getPlayerEntityByUUID(uuid);
        if (player != null) return player;
        for (Entity candidate : world.loadedEntityList) {
            if (uuid.equals(candidate.getUniqueID())) return candidate;
        }
        return null;
    }

    private void ensureChunkLoaded() {
        if (world == null || world.isRemote) return;
        if (chunkTicket == null) {
            try {
                chunkTicket = ForgeChunkManager.requestTicket(MeeCreeps.instance, world, ForgeChunkManager.Type.NORMAL);
                if (chunkTicket != null) {
                    PortalChunkLoadingCallback.prepareTicket(chunkTicket, pos);
                    ForgeChunkManager.forceChunk(chunkTicket, new ChunkPos(pos));
                }
            } catch (Exception e) {
                MeeCreeps.setup.getLogger().warn("Failed to force the portal chunk at " + pos, e);
                chunkTicket = null;
            }
        }
        if (chunkTicket == null) {
            world.getChunkFromBlockCoords(pos);
        }
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }

    @Nullable
    @Override public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound nbtTag = new NBTTagCompound();
        nbtTag.setInteger("timeout", timeout);
        nbtTag.setInteger("start", Math.min(10, Math.max(0, start)));
        nbtTag.setByte("portalSide", portalSide == null ? 127 : (byte) portalSide.ordinal());
        nbtTag.setBoolean("autoClose", autoClose);
        TransportSolutionType.writeToNBT(nbtTag, transportSolutionType);
        if (spawnerPos != null) nbtTag.setLong("spawnerPos", spawnerPos.toLong());
        return new SPacketUpdateTileEntity(getPos(), 1, nbtTag);
    }

    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        timeout = packet.getNbtCompound().getInteger("timeout");
        start = packet.getNbtCompound().getInteger("start");
        byte side = packet.getNbtCompound().getByte("portalSide");
        portalSide = side == 127 ? null : (side >= 0 && side < EnumFacing.VALUES.length ? EnumFacing.VALUES[side] : EnumFacing.UP);
        autoClose = !packet.getNbtCompound().hasKey("autoClose") || packet.getNbtCompound().getBoolean("autoClose");
        transportSolutionType = TransportSolutionType.fromNBT(packet.getNbtCompound());
        spawnerPos = packet.getNbtCompound().hasKey("spawnerPos")
                ? BlockPos.fromLong(packet.getNbtCompound().getLong("spawnerPos"))
                : null;
    }

    private AxisAlignedBB getTeleportBox() {
        if (box == null) {
            if (portalSide == null) portalSide = EnumFacing.UP;

            final double minX = pos.getX() - 0.50;
            final double maxX = pos.getX() + 1.50;
            final double minY = pos.getY();
            final double maxY = pos.getY() + 2.00;
            final double minZ = pos.getZ() - 0.50;
            final double maxZ = pos.getZ() + 1.50;
            final double planeHalfThickness = 0.18;

            switch (portalSide) {
                case DOWN:
                    box = new AxisAlignedBB(minX, pos.getY() + 0.90 - planeHalfThickness, minZ,
                            maxX, pos.getY() + 0.90 + planeHalfThickness, maxZ);
                    break;
                case UP:
                    box = new AxisAlignedBB(minX, pos.getY() + 0.10 - planeHalfThickness, minZ,
                            maxX, pos.getY() + 0.10 + planeHalfThickness, maxZ);
                    break;
                case SOUTH:
                    box = new AxisAlignedBB(minX, minY, pos.getZ() + 0.10 - planeHalfThickness,
                            maxX, maxY, pos.getZ() + 0.10 + planeHalfThickness);
                    break;
                case NORTH:
                    box = new AxisAlignedBB(minX, minY, pos.getZ() + 0.90 - planeHalfThickness,
                            maxX, maxY, pos.getZ() + 0.90 + planeHalfThickness);
                    break;
                case EAST:
                    box = new AxisAlignedBB(pos.getX() + 0.10 - planeHalfThickness, minY, minZ,
                            pos.getX() + 0.10 + planeHalfThickness, maxY, maxZ);
                    break;
                case WEST:
                    box = new AxisAlignedBB(pos.getX() + 0.90 - planeHalfThickness, minY, minZ,
                            pos.getX() + 0.90 + planeHalfThickness, maxY, maxZ);
                    break;
            }
        }
        return box;
    }

    public void addBlackList(UUID uuid) { if (uuid != null) blackListed.add(uuid); }
    public int getTimeout() { return timeout; }
    public int getStart() { return start; }
    private void markDirtyClient() { markDirty(); if (getWorld() != null) { IBlockState state = getWorld().getBlockState(getPos()); getWorld().notifyBlockUpdate(getPos(), state, state, 3); } }
    private void markDirtyQuick() { if (getWorld() != null) getWorld().markChunkDirty(this.pos, this); }
    public void restoreChunkTicket(ForgeChunkManager.Ticket ticket) {
        if (world == null || world.isRemote || ticket == null) return;
        chunkTicket = ticket;
        ForgeChunkManager.forceChunk(ticket, new ChunkPos(pos));
    }
    public EnumFacing getPortalSide() { return portalSide; }
    public void setPortalSide(EnumFacing side) { portalSide = side == null ? EnumFacing.UP : side; box = null; markDirtyClient(); }
    public void tickTime() { timeout--; getOther().ifPresent(otherPortal -> { if (timeout > otherPortal.getTimeout()) timeout = otherPortal.getTimeout(); }); markDirtyClient(); }
    public void setTimeout(int timeout) { this.timeout = timeout; markDirtyClient(); }
    public void forceResetManualClose() { manualCloseRequested = false; }
    public boolean isManualCloseRequested() { return manualCloseRequested; }
    public void forceManualClose() {

        boolean propagate = !manualCloseRequested;
        manualCloseRequested = true;
        timeout = 10;
        markDirtyClient();

        if (propagate) {
            getOther().ifPresent(otherPortal -> otherPortal.forceManualClose());
        }
    }
    public void setOther(TeleportDestination other) { this.other = other; markDirtyQuick(); }
    public Optional<PortalTileEntity> getOtherPortal() { return getOther(); }
    public UUID getGunId() { return gunId; }
    public void setSpawnerPos(BlockPos spawnerPos) { this.spawnerPos = spawnerPos; markDirtyQuick(); }
    @Nullable public BlockPos getSpawnerPos() { return spawnerPos; }
    public void setGunId(UUID gunId) { this.gunId = gunId; markDirtyQuick(); }
    public boolean isAutoClose() { return autoClose; }
    public TransportSolutionType getTransportSolutionType() { return transportSolutionType; }
    public void setTransportSolutionType(TransportSolutionType transportSolutionType) { this.transportSolutionType = transportSolutionType == null ? TransportSolutionType.INTERDIMENSIONAL : transportSolutionType; markDirtyClient(); }
    public void setAutoClose(boolean autoClose) { this.autoClose = autoClose; markDirtyClient(); }
    public void killPortal() {
        if (world != null && !world.isRemote) {
            if (chunkTicket != null) {
                try {
                    ForgeChunkManager.releaseTicket(chunkTicket);
                } catch (Exception e) {
                    MeeCreeps.setup.getLogger().warn("Failed to release the portal chunk ticket at " + pos, e);
                }
                chunkTicket = null;
            }
            if (spawnerPos != null) {
                BlockPos savedSpawnerPos = spawnerPos;

                if (!PortalSpawnerBlock.restoreLowerSection(world, savedSpawnerPos)) {
                    if (world.getBlockState(getPos()).getBlock() == ModBlocks.portalBlock) {
                        world.setBlockToAir(getPos());
                    }
                }

                PortalSpawnerBlock.setPortalActive(world, savedSpawnerPos, false);
                world.notifyLightSet(savedSpawnerPos);
                spawnerPos = null;
            } else if (world.getBlockState(getPos()).getBlock() == ModBlocks.portalBlock) {
                world.setBlockToAir(getPos());
            }
        }
    }

    private Optional<PortalTileEntity> getOther() {
        if (other == null || !DimensionManager.isDimensionRegistered(other.getDimension())) return Optional.empty();
        try {
            World otherWorld = mcjty.meecreeps.teleport.TeleportationTools.resolveDestinationWorld(other.getDimension());
            if (otherWorld == null) return Optional.empty();
            TileEntity te = otherWorld.getTileEntity(other.getPos());
            return te instanceof PortalTileEntity ? Optional.of((PortalTileEntity) te) : Optional.empty();
        } catch (Exception e) {
            MeeCreeps.setup.getLogger().debug("Unable to resolve the other portal for " + pos, e);
            return Optional.empty();
        }
    }

    @Override public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        timeout = compound.getInteger("timeout");
        start = compound.hasKey("start") ? compound.getInteger("start") : 10;
        byte pside = compound.getByte("portalSide");
        portalSide = pside == 127 ? null : (pside >= 0 && pside < EnumFacing.VALUES.length ? EnumFacing.VALUES[pside] : EnumFacing.UP);
        if (compound.hasKey("pos") && compound.hasKey("dim") && compound.hasKey("side")) {
            int side = compound.getByte("side");
            if (side >= 0 && side < EnumFacing.VALUES.length) other = new TeleportDestination("", compound.getInteger("dim"), BlockPos.fromLong(compound.getLong("pos")), EnumFacing.VALUES[side]);
        }
        autoClose = !compound.hasKey("autoClose") || compound.getBoolean("autoClose");
        transportSolutionType = TransportSolutionType.fromNBT(compound);
        manualCloseRequested = compound.getBoolean("manualCloseRequested");
        gunId = compound.hasUniqueId("gunId") ? compound.getUniqueId("gunId") : null;
        spawnerPos = compound.hasKey("spawnerPos") ? BlockPos.fromLong(compound.getLong("spawnerPos")) : null;

        blackListed.clear();
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger("timeout", timeout);
        compound.setInteger("start", start);
        compound.setByte("portalSide", portalSide == null ? 127 : (byte) portalSide.ordinal());
        if (other != null) {
            compound.setLong("pos", other.getPos().toLong());
            compound.setInteger("dim", other.getDimension());
            compound.setByte("side", (byte) other.getSide().ordinal());
        }
        compound.setBoolean("autoClose", autoClose);
        TransportSolutionType.writeToNBT(compound, transportSolutionType);
        compound.setBoolean("manualCloseRequested", manualCloseRequested);
        if (gunId != null) compound.setUniqueId("gunId", gunId);
        if (spawnerPos != null) compound.setLong("spawnerPos", spawnerPos.toLong());

        return super.writeToNBT(compound);
    }
}
