package mcjty.meecreeps.blocks;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import mcjty.meecreeps.MeeCreeps;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.List;

public final class PortalChunkLoadingCallback implements ForgeChunkManager.LoadingCallback {
    public static final PortalChunkLoadingCallback INSTANCE = new PortalChunkLoadingCallback();

    private static final String TAG_TYPE = "meecreepsTicketType";
    private static final String TYPE_PORTAL = "portal";
    private static final String TYPE_SPAWNER = "portalSpawner";

    private static final String TAG_PORTAL = "meecreepsPortal";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";

    private PortalChunkLoadingCallback() { }

    public static void prepareTicket(ForgeChunkManager.Ticket ticket, BlockPos pos) {
        prepare(ticket, pos, TYPE_PORTAL);
        ticket.getModData().setBoolean(TAG_PORTAL, true);
    }

    public static void prepareSpawnerTicket(ForgeChunkManager.Ticket ticket, BlockPos pos) {
        prepare(ticket, pos, TYPE_SPAWNER);
    }

    private static void prepare(ForgeChunkManager.Ticket ticket, BlockPos pos, String type) {
        if (ticket == null || pos == null) return;
        NBTTagCompound data = ticket.getModData();
        data.setString(TAG_TYPE, type);
        data.setInteger(TAG_X, pos.getX());
        data.setInteger(TAG_Y, pos.getY());
        data.setInteger(TAG_Z, pos.getZ());
    }

    @Override
    public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
        if (tickets == null || world == null || world.isRemote) return;

        for (ForgeChunkManager.Ticket ticket : tickets) {
            if (ticket == null) continue;
            NBTTagCompound data = ticket.getModData();
            if (!data.hasKey(TAG_X) || !data.hasKey(TAG_Y) || !data.hasKey(TAG_Z)) {
                ForgeChunkManager.releaseTicket(ticket);
                continue;
            }

            String type = data.getString(TAG_TYPE);

            if (type.isEmpty() && data.getBoolean(TAG_PORTAL)) type = TYPE_PORTAL;

            BlockPos pos = new BlockPos(data.getInteger(TAG_X), data.getInteger(TAG_Y), data.getInteger(TAG_Z));
            try {
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(pos));
                world.getChunkFromBlockCoords(pos);
                TileEntity te = world.getTileEntity(pos);

                if (TYPE_SPAWNER.equals(type)) {
                    if (te instanceof PortalSpawnerTileEntity) {
                        ((PortalSpawnerTileEntity) te).restoreChunkTicket(ticket);
                    } else {
                        ForgeChunkManager.releaseTicket(ticket);
                    }
                } else if (TYPE_PORTAL.equals(type)) {
                    if (te instanceof PortalTileEntity) {
                        ((PortalTileEntity) te).restoreChunkTicket(ticket);
                    } else {
                        ForgeChunkManager.releaseTicket(ticket);
                    }
                } else {
                    ForgeChunkManager.releaseTicket(ticket);
                }
            } catch (Exception e) {
                MeeCreeps.setup.getLogger().warn("Failed to restore a MeeCreeps chunk ticket at " + pos, e);
                try {
                    ForgeChunkManager.releaseTicket(ticket);
                } catch (Exception releaseException) {
                    MeeCreeps.setup.getLogger().debug("Failed to release an invalid MeeCreeps chunk ticket", releaseException);
                }
            }
        }
    }
}
