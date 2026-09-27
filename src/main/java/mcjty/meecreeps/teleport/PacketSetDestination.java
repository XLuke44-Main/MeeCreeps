package mcjty.meecreeps.teleport;

import io.netty.buffer.ByteBuf;
import mcjty.lib.network.NetworkTools;
import mcjty.lib.thirteen.Context;
import mcjty.meecreeps.items.PortalGunItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.function.Supplier;

public class PacketSetDestination implements IMessage {
    private TeleportDestination destination;
    private int destinationIndex;
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
            String name = readBoundedString(buf, MAX_DESTINATION_NAME_LENGTH);
            int dimension = buf.readInt();
            BlockPos pos = BlockPos.fromLong(buf.readLong());
            int sideIndex = buf.readByte();
            destinationIndex = buf.readInt();
            if (sideIndex < 0 || sideIndex >= EnumFacing.VALUES.length) { validPacket = false; return; }
            destination = new TeleportDestination(name, dimension, pos, EnumFacing.VALUES[sideIndex]);
            validPacket = true;
        } catch (Exception t) {
            validPacket = false;
        }
    }

    @Override public void toBytes(ByteBuf buf) {
        NetworkTools.writeStringUTF8(buf, destination.getName());
        buf.writeInt(destination.getDimension());
        buf.writeLong(destination.getPos().toLong());
        buf.writeByte(destination.getSide().ordinal());
        buf.writeInt(destinationIndex);
    }

    public PacketSetDestination() { validPacket = false; }
    public PacketSetDestination(ByteBuf buf) { fromBytes(buf); }
    public PacketSetDestination(TeleportDestination destination, int destinationIndex) { this.destination = destination; this.destinationIndex = destinationIndex; validPacket = destination != null; }

    public void handle(Supplier<Context> supplier) {
        Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            if (!validPacket) return;
            EntityPlayerMP player = ctx.getSender();
            try {
                ItemStack heldItem = PortalGunItem.getGun(player);
                if (heldItem.isEmpty()) return;
                if (destinationIndex < 0 || destinationIndex >= 24) return;
                if (!PortalGunItem.isDestinationAllowed(heldItem, player.getEntityWorld(), destination)) {
                    PortalGunItem.sendIntradimensionalDimensionError(player);
                    return;
                }
                if (!DimensionManager.isDimensionRegistered(destination.getDimension())
                        || TeleportationTools.resolveDestinationWorld(destination.getDimension()) == null) {
                    player.sendMessage(new TextComponentString(DestinationParser.INVALID_DIMENSION).setStyle(new Style().setColor(TextFormatting.RED)));
                    return;
                }
                PortalGunItem.addDestinationEncoded(heldItem, destination, destinationIndex);
            } catch (Exception ignored) {
            }
        });
        ctx.setPacketHandled(true);
    }
}
