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

    @Override public void fromBytes(ByteBuf buf) {
        try {
            String name = NetworkTools.readStringUTF8(buf);
            int dimension = buf.readInt();
            BlockPos pos = BlockPos.fromLong(buf.readLong());
            int sideIndex = buf.readByte();
            destinationIndex = buf.readInt();
            if (sideIndex < 0 || sideIndex >= EnumFacing.VALUES.length) { validPacket = false; return; }
            destination = new TeleportDestination(name, dimension, pos, EnumFacing.VALUES[sideIndex]);
            validPacket = true;
        } catch (Throwable t) {
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
                if (!DimensionManager.isDimensionRegistered(destination.getDimension())
                        || TeleportationTools.resolveDestinationWorld(destination.getDimension()) == null) {
                    player.sendMessage(new TextComponentString(DestinationParser.INVALID_DIMENSION).setStyle(new Style().setColor(TextFormatting.RED)));
                    return;
                }
                PortalGunItem.addDestinationEncoded(heldItem, destination, destinationIndex);
            } catch (Throwable ignored) {
            }
        });
        ctx.setPacketHandled(true);
    }
}
