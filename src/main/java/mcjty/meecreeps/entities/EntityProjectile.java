package mcjty.meecreeps.entities;

import mcjty.meecreeps.items.TransportSolutionType;
import mcjty.meecreeps.teleport.TeleportDestination;
import mcjty.meecreeps.teleport.TeleportationTools;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.util.UUID;

public class EntityProjectile extends EntityThrowable {
    private TeleportDestination destination;
    private UUID playerId;
    private UUID gunId;
    private boolean autoClose = true;
    private static final DataParameter<Integer> PORTAL_SOLUTION = EntityDataManager.createKey(EntityProjectile.class, DataSerializers.VARINT);
    private TransportSolutionType transportSolutionType = TransportSolutionType.INTERDIMENSIONAL;
    private boolean airPlacementEnabled;
    private double originX;
    private double originZ;
    private boolean originInitialized;

    public EntityProjectile(World worldIn) { super(worldIn); }
    public EntityProjectile(World worldIn, EntityLivingBase throwerIn) { super(worldIn, throwerIn); }
    public EntityProjectile(World worldIn, double x, double y, double z) { super(worldIn, x, y, z); }
    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(PORTAL_SOLUTION, TransportSolutionType.INTERDIMENSIONAL.getId());
    }

    public void setDestination(TeleportDestination destination) { this.destination = destination; }
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
                    if (player != null && TeleportationTools.makeAirPortalPair(player, airPosition, airSide, destination, gunId, autoClose, transportSolutionType)) {
                        setDead();
                        return;
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
            if (result.typeOfHit == RayTraceResult.Type.BLOCK && destination != null && DimensionManager.isDimensionRegistered(destination.getDimension())) {
                EntityPlayer player = null;
                if (playerId != null) {
                    MinecraftServer server = DimensionManager.getWorld(0).getMinecraftServer();
                    player = server == null ? null : server.getPlayerList().getPlayerByUUID(playerId);
                }
                if (player != null) {
                    TeleportationTools.makePortalPair(player, result.getBlockPos(), result.sideHit, destination, gunId, autoClose, transportSolutionType);
                } else if (!transportSolutionType.isIntradimensional() || destination.getDimension() == world.provider.getDimension()) {
                    TeleportationTools.makePortalPair(world, result.getBlockPos(), result.sideHit, destination, transportSolutionType);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            setDead();
        }
    }
}
