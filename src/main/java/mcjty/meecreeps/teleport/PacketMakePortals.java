package mcjty.meecreeps.teleport;

import io.netty.buffer.ByteBuf;
import mcjty.lib.network.NetworkTools;
import mcjty.lib.thirteen.Context;
import mcjty.meecreeps.items.PortalGunItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.function.Supplier;

public class PacketMakePortals implements IMessage {
    private BlockPos selectedBlock;
    private TeleportDestination destination;
    private EnumFacing selectedSide;
    private boolean validPacket;

    private static final int MAX_DESTINATION_NAME_LENGTH = 256;

    private static String readBoundedString(ByteBuf buf, int maxBytes) {
        int length = buf.readInt();
        if (length == -1) return null;
        if (length < 0 || length > maxBytes || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid network string length: " + length);
        }
        if (length == 0) return "";
        byte[] data = new byte[length];
        buf.readBytes(data);
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override public void fromBytes(ByteBuf buf) {
        try {
            selectedBlock = BlockPos.fromLong(buf.readLong());
            int selectedSideIndex = buf.readByte();
            String name = readBoundedString(buf, MAX_DESTINATION_NAME_LENGTH);
            int dimension = buf.readInt();
            BlockPos pos = BlockPos.fromLong(buf.readLong());
            int sideIndex = buf.readByte();
            if (selectedSideIndex < 0 || selectedSideIndex >= EnumFacing.VALUES.length || sideIndex < 0 || sideIndex >= EnumFacing.VALUES.length) { validPacket = false; return; }
            selectedSide = EnumFacing.VALUES[selectedSideIndex];
            destination = new TeleportDestination(name, dimension, pos, EnumFacing.VALUES[sideIndex]);
            validPacket = true;
        } catch (Throwable t) { validPacket = false; }
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(selectedBlock.toLong());
        buf.writeByte(selectedSide.ordinal());
        NetworkTools.writeStringUTF8(buf, destination.getName());
        buf.writeInt(destination.getDimension());
        buf.writeLong(destination.getPos().toLong());
        buf.writeByte(destination.getSide().ordinal());
    }

    public PacketMakePortals() { validPacket = false; }
    public PacketMakePortals(ByteBuf buf) { fromBytes(buf); }
    public PacketMakePortals(BlockPos selectedBlock, EnumFacing selectedSide, TeleportDestination destination) { this.selectedBlock = selectedBlock; this.selectedSide = selectedSide; this.destination = destination; validPacket = true; }

    public void handle(Supplier<Context> supplier) {
        Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            if (!validPacket) return;
            EntityPlayerMP player = ctx.getSender();
            try {
                ItemStack heldItem = PortalGunItem.getGun(player);
                if (heldItem.isEmpty() || destination == null || selectedBlock == null || selectedSide == null
                        || !DimensionManager.isDimensionRegistered(destination.getDimension())) return;

                // The client supplies the clicked block through this packet, so the server must
                // enforce the same basic reach/edit constraints as a normal block interaction.
                double dx = player.posX - (selectedBlock.getX() + 0.5D);
                double dy = player.posY - (selectedBlock.getY() + 0.5D);
                double dz = player.posZ - (selectedBlock.getZ() + 0.5D);
                if (dx * dx + dy * dy + dz * dz > 64.0D || !player.canPlayerEdit(selectedBlock, selectedSide, heldItem)) return;

                if (!PortalGunItem.isDestinationAllowed(heldItem, player.getEntityWorld(), destination)) {
                    PortalGunItem.sendIntradimensionalDimensionError(player);
                    return;
                }
                TeleportationTools.makePortalPair(player, selectedBlock, selectedSide, destination, PortalGunItem.getGunId(heldItem), PortalGunItem.isAutoCloseEnabled(heldItem));
            } catch (Throwable ignored) { }
        });
        ctx.setPacketHandled(true);
    }
}
