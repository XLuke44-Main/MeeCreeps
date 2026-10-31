package mcjty.meecreeps.entities;

import mcjty.meecreeps.config.DimensionGroups;
import mcjty.meecreeps.blocks.PortalSpawnerBlock;
import mcjty.meecreeps.blocks.PortalSpawnerTileEntity;
import mcjty.meecreeps.items.TransportSolutionType;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.teleport.TeleportDestination;
import mcjty.meecreeps.teleport.TeleportationTools;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.util.UUID;

public class EntityProjectile extends EntityThrowable {
    private TeleportDestination destination;
    private UUID playerId;
    private UUID gunId;
    private boolean autoClose = true;
    private boolean dualLinked;
    private static final DataParameter<Integer> PORTAL_SOLUTION = EntityDataManager.createKey(EntityProjectile.class, DataSerializers.VARINT);
    private TransportSolutionType transportSolutionType = TransportSolutionType.INTERDIMENSIONAL;
    private boolean airPlacementEnabled;
    private double originX;
    private double originZ;
    private boolean originInitialized;
    private boolean attemptedSpawnerCapture;

    public EntityProjectile(World worldIn) { super(worldIn); }
    public EntityProjectile(World worldIn, EntityLivingBase throwerIn) { super(worldIn, throwerIn); }
    public EntityProjectile(World worldIn, double x, double y, double z) { super(worldIn, x, y, z); }
    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(PORTAL_SOLUTION, TransportSolutionType.INTERDIMENSIONAL.getId());
    }

    public void setDestination(TeleportDestination destination) { this.destination = destination; }
    public void setDualLinked(boolean dualLinked) { this.dualLinked = dualLinked; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }
    public void setGunId(UUID gunId) { this.gunId = gunId; }
    public void setAutoClose(boolean autoClose) { this.autoClose = autoClose; }
    public void setTransportSolutionType(TransportSolutionType transportSolutionType) {
        this.transportSolutionType = transportSolutionType == null ? TransportSolutionType.INTERDIMENSIONAL : transportSolutionType;
        if (dataManager != null) dataManager.set(PORTAL_SOLUTION, this.transportSolutionType.getId());
    }
    public TransportSolutionType getTransportSolutionType() {
        return dataManager != null ? TransportSolutionType.fromId(dataManager.get(PORTAL_SOLUTION)) : transportSolutionType;
    }
    public void setAirPlacementEnabled(boolean enabled) { this.airPlacementEnabled = enabled; }
    public void setOrigin(double x, double z) {
        this.originX = x;
        this.originZ = z;
        this.originInitialized = true;
    }

    @Override public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (destination != null) compound.setTag("destination", destination.getCompound());
        if (playerId != null) compound.setUniqueId("playerId", playerId);
        if (gunId != null) compound.setUniqueId("gunId", gunId);
        compound.setBoolean("autoClose", autoClose);
        compound.setBoolean("dualLinked", dualLinked);
        TransportSolutionType.writeToNBT(compound, transportSolutionType);
        compound.setBoolean("airPlacement", airPlacementEnabled);
        compound.setDouble("originX", originX);
        compound.setDouble("originZ", originZ);
        compound.setBoolean("originInitialized", originInitialized);
    }

    @Override public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        destination = compound.hasKey("destination") ? new TeleportDestination(compound.getCompoundTag("destination")) : null;
        playerId = compound.hasUniqueId("playerId") ? compound.getUniqueId("playerId") : null;
        gunId = compound.hasUniqueId("gunId") ? compound.getUniqueId("gunId") : null;
        autoClose = !compound.hasKey("autoClose") || compound.getBoolean("autoClose");
        dualLinked = compound.getBoolean("dualLinked");
        transportSolutionType = TransportSolutionType.fromNBT(compound);
        if (dataManager != null) dataManager.set(PORTAL_SOLUTION, transportSolutionType.getId());
        airPlacementEnabled = compound.getBoolean("airPlacement");
        originX = compound.getDouble("originX");
        originZ = compound.getDouble("originZ");
        originInitialized = compound.getBoolean("originInitialized");
    }

    @Override
    public void onUpdate() {
        if (world == null) {
            setDead();
            return;
        }

        if (world.isRemote) {
            super.onUpdate();
            return;
        }

        if (isDead) return;

        try {
            transportSolutionType = getTransportSolutionType();

            if (!originInitialized) {
                originX = posX;
                originZ = posZ;
                originInitialized = true;
            }

            Vec3d start = new Vec3d(posX, posY, posZ);
            Vec3d end = new Vec3d(posX + motionX, posY + motionY, posZ + motionZ);

            RayTraceResult hit = world.rayTraceBlocks(start, end, false, true, false);
            Vec3d travelledEnd = hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                    ? hit.hitVec : end;
            if (tryCaptureBySpawner(start, travelledEnd)) {
                return;
            }

            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
                setPosition(hit.hitVec.x, hit.hitVec.y, hit.hitVec.z);
                onImpact(hit);
                return;
            }

            setPosition(end.x, end.y, end.z);

            if (airPlacementEnabled && hasReachedAirPlacementDistance()) {
                BlockPos airPosition = new BlockPos(posX, posY, posZ);
                if (airPosition.getY() >= 1 && airPosition.getY() <= 254
                        && world.isAirBlock(airPosition)) {
                    EnumFacing airSide = getAirPortalSide();
                    EntityPlayer player = getThrowerPlayer();
                    if (player == null && playerId != null) {
                        MinecraftServer server = DimensionManager.getWorld(0) == null ? null : DimensionManager.getWorld(0).getMinecraftServer();
                        player = server == null ? null : server.getPlayerList().getPlayerByUUID(playerId);
                    }
                    if (player != null) {
                        boolean placed = dualLinked
                                ? PortalGunItem.handleDualLinkedEndpointShot(player, world, gunId, airPosition, airSide,
                                    true, autoClose, transportSolutionType)
                                : TeleportationTools.makeAirPortalPair(player, airPosition, airSide,
                                    destination, gunId, autoClose, transportSolutionType);
                        if (placed) {
                            setDead();
                            return;
                        }
                    }
                }
            }

            motionX *= 0.99D;
            motionY *= 0.99D;
            motionZ *= 0.99D;
            motionY -= getGravityVelocity();

            if (motionX * motionX + motionZ * motionZ > 1.0E-7D) {
                rotationYaw = (float) (Math.atan2(motionX, motionZ) * 180.0D / Math.PI);
                double horizontal = Math.sqrt(motionX * motionX + motionZ * motionZ);
                rotationPitch = (float) (Math.atan2(motionY, horizontal) * 180.0D / Math.PI);
            }
        } catch (Throwable t) {
            setDead();
        }
    }
    private boolean tryCaptureBySpawner(Vec3d start, Vec3d end) {
        if (attemptedSpawnerCapture || destination == null || destination.isBlenderTrap()
                || world == null || world.isRemote || isDead) {
            return false;
        }

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        final double radiusSquared = 16.0D;

        int minX = MathHelper.floor(Math.min(start.x, end.x) - 5.0D);
        int maxX = MathHelper.floor(Math.max(start.x, end.x) + 5.0D);
        int minY = MathHelper.floor(Math.min(start.y, end.y) - 5.0D);
        int maxY = MathHelper.floor(Math.max(start.y, end.y) + 5.0D);
        int minZ = MathHelper.floor(Math.min(start.z, end.z) - 5.0D);
        int maxZ = MathHelper.floor(Math.max(start.z, end.z) + 5.0D);

        PortalSpawnerTileEntity nearestSpawner = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        double nearestCaptureT = 0.0D;
        boolean selectedAtCurrentPosition = false;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos upperPos = new BlockPos(x, y, z);
                    IBlockState state = world.getBlockState(upperPos);
                    if (!(state.getBlock() instanceof PortalSpawnerBlock)
                            || state.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.UPPER) {
                        continue;
                    }

                    TileEntity tile = world.getTileEntity(upperPos);
                    if (!(tile instanceof PortalSpawnerTileEntity)) continue;
                    PortalSpawnerTileEntity spawner = (PortalSpawnerTileEntity) tile;
                    if (!spawner.canCaptureProjectile()) continue;

                    double centerX = x + 0.5D;
                    double centerY = y;
                    double centerZ = z + 0.5D;

                    double t = lengthSquared > 1.0E-12D
                            ? ((centerX - start.x) * dx + (centerY - start.y) * dy
                            + (centerZ - start.z) * dz) / lengthSquared : 0.0D;
                    t = Math.max(0.0D, Math.min(1.0D, t));
                    double closestX = start.x + t * dx;
                    double closestY = start.y + t * dy;
                    double closestZ = start.z + t * dz;
                    double segmentX = closestX - centerX;
                    double segmentY = closestY - centerY;
                    double segmentZ = closestZ - centerZ;
                    double segmentDistanceSquared = segmentX * segmentX
                            + segmentY * segmentY + segmentZ * segmentZ;
                    if (segmentDistanceSquared > radiusSquared) continue;

                    double currentX = end.x - centerX;
                    double currentY = end.y - centerY;
                    double currentZ = end.z - centerZ;
                    double currentDistanceSquared = currentX * currentX
                            + currentY * currentY + currentZ * currentZ;
                    boolean containsCurrentPosition = currentDistanceSquared <= radiusSquared;
                    double candidateDistanceSquared = containsCurrentPosition
                            ? currentDistanceSquared : segmentDistanceSquared;

                    boolean closer = nearestSpawner == null
                            || (containsCurrentPosition && !selectedAtCurrentPosition)
                            || (containsCurrentPosition == selectedAtCurrentPosition
                            && candidateDistanceSquared < nearestDistanceSquared);
                    if (closer) {
                        nearestSpawner = spawner;
                        nearestDistanceSquared = candidateDistanceSquared;
                        nearestCaptureT = containsCurrentPosition ? 1.0D : t;
                        selectedAtCurrentPosition = containsCurrentPosition;
                    }
                }
            }
        }

        if (nearestSpawner == null) return false;
        attemptedSpawnerCapture = true;
        setPosition(start.x + nearestCaptureT * dx,
                start.y + nearestCaptureT * dy, start.z + nearestCaptureT * dz);
        if (nearestSpawner.captureProjectileDestination(destination, gunId, transportSolutionType)) {
            clearUsedOneShotDestination();
        }

        setDead();
        return true;
    }

    private void clearUsedOneShotDestination() {
        EntityPlayer player = getThrowerPlayer();
        if (player == null && playerId != null) {
            World overworld = DimensionManager.getWorld(0);
            MinecraftServer server = overworld == null ? null : overworld.getMinecraftServer();
            player = server == null ? null : server.getPlayerList().getPlayerByUUID(playerId);
        }
        if (player == null) return;

        ItemStack heldGun = PortalGunItem.getGun(player);
        if (!heldGun.isEmpty() && gunId != null && gunId.equals(PortalGunItem.getGunId(heldGun))) {
            PortalGunItem.clearOneShotDestination(heldGun);
        }
    }

    private boolean hasReachedAirPlacementDistance() {
        double dx = posX - originX;
        double dz = posZ - originZ;
        return dx * dx + dz * dz >= 9.0D;
    }

    private EnumFacing getAirPortalSide() {
        if (Math.abs(motionX) >= Math.abs(motionZ)) {
            return motionX >= 0.0D ? EnumFacing.WEST : EnumFacing.EAST;
        }
        return motionZ >= 0.0D ? EnumFacing.NORTH : EnumFacing.SOUTH;
    }

    private EntityPlayer getThrowerPlayer() {
        return getThrower() instanceof EntityPlayer ? (EntityPlayer) getThrower() : null;
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        if (world.isRemote) return;
        try {
            if (result.typeOfHit == RayTraceResult.Type.BLOCK) {
                EntityPlayer player = getThrowerPlayer();
                if (player == null && playerId != null) {
                    World overworld = DimensionManager.getWorld(0);
                    MinecraftServer server = overworld == null ? null : overworld.getMinecraftServer();
                    player = server == null ? null : server.getPlayerList().getPlayerByUUID(playerId);
                }

                if (dualLinked) {
                    if (player != null) {
                        PortalGunItem.handleDualLinkedEndpointShot(player, world, gunId, result.getBlockPos(),
                                result.sideHit, false, autoClose, transportSolutionType);
                    }
                } else if (destination != null
                        && (destination.isBlenderTrap()
                        || (destination.getDimension() != null
                        && DimensionGroups.isDimensionAvailable(destination.getDimension())))) {
                    if (player != null) {
                        TeleportationTools.makePortalPair(player, result.getBlockPos(), result.sideHit,
                                destination, gunId, autoClose, transportSolutionType);
                    } else {
                        TeleportationTools.makePortalPair(world, result.getBlockPos(), result.sideHit,
                                destination, transportSolutionType);
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            setDead();
        }
    }
}
