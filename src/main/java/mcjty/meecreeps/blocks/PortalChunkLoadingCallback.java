package mcjty.meecreeps.blocks;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.List;

/**
 * Restores MeeCreeps portal chunk tickets when Forge loads persistent chunkloading
 * state. Forge 1.12.2 requires every mod which requests NORMAL tickets to register
 * a LoadingCallback before requesting a ticket.
 */
public final class PortalChunkLoadingCallback implements ForgeChunkManager.LoadingCallback {
    public static final PortalChunkLoadingCallback INSTANCE = new PortalChunkLoadingCallback();
    private static final String TAG_PORTAL = "meecreepsPortal";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";

    private PortalChunkLoadingCallback() { }

    public static void prepareTicket(ForgeChunkManager.Ticket ticket, BlockPos pos) {
        if (ticket == null || pos == null) return;
        NBTTagCompound data = ticket.getModData();
        data.setBoolean(TAG_PORTAL, true);
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
            if (!data.getBoolean(TAG_PORTAL) || !data.hasKey(TAG_X) || !data.hasKey(TAG_Y) || !data.hasKey(TAG_Z)) {
                ForgeChunkManager.releaseTicket(ticket);
                continue;
            }

            BlockPos pos = new BlockPos(data.getInteger(TAG_X), data.getInteger(TAG_Y), data.getInteger(TAG_Z));
            try {
                // Force the saved chunk before looking for the portal tile entity. This also
                // makes the ticket useful for a portal whose chunk was not otherwise loaded.
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(pos));
                world.getChunkFromBlockCoords(pos);
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof PortalTileEntity) {
                    ((PortalTileEntity) te).restoreChunkTicket(ticket);
                }
            } catch (Throwable t) {
                try { ForgeChunkManager.releaseTicket(ticket); } catch (Throwable ignored) { }
            }
        }
    }
}
