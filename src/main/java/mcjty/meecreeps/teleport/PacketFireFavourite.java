package mcjty.meecreeps.teleport;

import io.netty.buffer.ByteBuf;
import mcjty.lib.thirteen.Context;
import mcjty.meecreeps.items.PortalGunItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.function.Supplier;

public class PacketFireFavourite implements IMessage {

    public PacketFireFavourite() {
    }

    public PacketFireFavourite(ByteBuf buf) {
        fromBytes(buf);
    }

    @Override
    public void fromBytes(ByteBuf buf) {

    }

    @Override
    public void toBytes(ByteBuf buf) {

    }

    public void handle(Supplier<Context> supplier) {
        Context ctx = supplier.get();
        EntityPlayerMP sender = ctx.getSender();
        ctx.enqueueWork(() -> PortalGunItem.fireFavourite(sender));
        ctx.setPacketHandled(true);
    }
}
