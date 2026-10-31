package mcjty.meecreeps.teleport;

import io.netty.buffer.ByteBuf;
import mcjty.lib.thirteen.Context;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.function.Supplier;

public class PacketBlenderDeath implements IMessage {

    public PacketBlenderDeath() {
    }

    public PacketBlenderDeath(ByteBuf buf) {
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
        ctx.enqueueWork(BlenderDeathState::markPending);
        ctx.setPacketHandled(true);
    }
}
