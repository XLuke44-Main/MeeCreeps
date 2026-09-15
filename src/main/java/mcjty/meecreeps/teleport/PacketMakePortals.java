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

    @Override public void fromBytes(ByteBuf buf) {
        try {
            selectedBlock = BlockPos.fromLong(buf.readLong());
            int selectedSideIndex = buf.readByte();
            String name = NetworkTools.readStringUTF8(buf);
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
                if (heldItem.isEmpty() || destination == null || !DimensionManager.isDimensionRegistered(destination.getDimension())) return;
                if (!PortalGunItem.isDestinationAllowed(heldItem, player.getEntityWorld(), destination)) {
                    PortalGunItem.sendBlueDimensionError(player);
                    return;
                }
                TeleportationTools.makePortalPair(player, selectedBlock, selectedSide, destination, PortalGunItem.getGunId(heldItem), PortalGunItem.isAutoCloseEnabled(heldItem));
            } catch (Throwable ignored) { }
        });
        ctx.setPacketHandled(true);
    }
}
