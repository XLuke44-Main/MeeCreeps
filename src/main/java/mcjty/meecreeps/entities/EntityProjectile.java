package mcjty.meecreeps.entities;

import mcjty.meecreeps.teleport.TeleportDestination;
import mcjty.meecreeps.teleport.TeleportationTools;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.util.UUID;

public class EntityProjectile extends EntityThrowable {
    private TeleportDestination destination;
    private UUID playerId;
    private UUID gunId;
    private boolean autoClose = true;

    public EntityProjectile(World worldIn) { super(worldIn); }
    public EntityProjectile(World worldIn, EntityLivingBase throwerIn) { super(worldIn, throwerIn); }
    public EntityProjectile(World worldIn, double x, double y, double z) { super(worldIn, x, y, z); }
    public void setDestination(TeleportDestination destination) { this.destination = destination; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }
    public void setGunId(UUID gunId) { this.gunId = gunId; }
    public void setAutoClose(boolean autoClose) { this.autoClose = autoClose; }

    @Override public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (destination != null) compound.setTag("destination", destination.getCompound());
        if (playerId != null) compound.setUniqueId("playerId", playerId);
        if (gunId != null) compound.setUniqueId("gunId", gunId);
        compound.setBoolean("autoClose", autoClose);
    }

    @Override public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        destination = compound.hasKey("destination") ? new TeleportDestination(compound.getCompoundTag("destination")) : null;
        playerId = compound.hasUniqueId("playerId") ? compound.getUniqueId("playerId") : null;
        gunId = compound.hasUniqueId("gunId") ? compound.getUniqueId("gunId") : null;
        autoClose = !compound.hasKey("autoClose") || compound.getBoolean("autoClose");
    }

    /**
     * EntityThrowable's vanilla update performs an entity raycast and invokes onImpact when
     * another entity is hit. Portal projectiles are intentionally non-colliding with entities:
     * only blocks should stop the projectile and create a portal.
     */
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
            Vec3d start = new Vec3d(posX, posY, posZ);
            Vec3d end = new Vec3d(posX + motionX, posY + motionY, posZ + motionZ);

            // Deliberately ray trace blocks only. Entity hitboxes are ignored completely.
            RayTraceResult hit = world.rayTraceBlocks(start, end, false, true, false);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
                setPosition(hit.hitVec.x, hit.hitVec.y, hit.hitVec.z);
                onImpact(hit);
                return;
            }

            setPosition(end.x, end.y, end.z);

            // Match the normal throwable motion damping/gravity closely enough to preserve
            // the original projectile trajectory while removing only entity collision.
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
                if (player != null) TeleportationTools.makePortalPair(player, result.getBlockPos(), result.sideHit, destination, gunId, autoClose);
                else TeleportationTools.makePortalPair(world, result.getBlockPos(), result.sideHit, destination);
            }
        } catch (Throwable ignored) {
        } finally {
            setDead();
        }
    }
}
