package mcjty.meecreeps.teleport;

import io.netty.buffer.ByteBuf;
import mcjty.lib.thirteen.Context;
import mcjty.meecreeps.items.PortalGunItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.function.Supplier;

public class PacketToggleFavouriteDestination implements IMessage {
    private int destinationIndex;
    private boolean validPacket;

    public PacketToggleFavouriteDestination() {
        validPacket = false;
    }

    public PacketToggleFavouriteDestination(ByteBuf buf) {
        fromBytes(buf);
    }

    public PacketToggleFavouriteDestination(int destinationIndex) {
        this.destinationIndex = destinationIndex;
        validPacket = destinationIndex >= 0 && destinationIndex < 24;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        destinationIndex = buf.readInt();
        validPacket = destinationIndex >= 0 && destinationIndex < 24;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(destinationIndex);
    }

    public void handle(Supplier<Context> supplier) {
        Context ctx = supplier.get();
        EntityPlayerMP sender = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (!validPacket || sender == null) return;
            ItemStack gun = PortalGunItem.getGun(sender);
            if (!gun.isEmpty()) {
                PortalGunItem.toggleFavouriteDestination(gun, destinationIndex);
            }
        });
        ctx.setPacketHandled(true);
    }
}
