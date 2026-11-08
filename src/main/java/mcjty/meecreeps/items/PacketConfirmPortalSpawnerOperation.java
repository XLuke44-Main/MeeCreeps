package mcjty.meecreeps.items;

import io.netty.buffer.ByteBuf;
import mcjty.lib.thirteen.Context;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketConfirmPortalSpawnerOperation implements IMessage {
    private UUID token;
    private boolean confirmed;

    public PacketConfirmPortalSpawnerOperation() {
    }

    public PacketConfirmPortalSpawnerOperation(ByteBuf buf) {
        fromBytes(buf);
    }

    public PacketConfirmPortalSpawnerOperation(UUID token, boolean confirmed) {
        this.token = token;
        this.confirmed = confirmed;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        token = new UUID(buf.readLong(), buf.readLong());
        confirmed = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(token == null ? 0L : token.getMostSignificantBits());
        buf.writeLong(token == null ? 0L : token.getLeastSignificantBits());
        buf.writeBoolean(confirmed);
    }

    public void handle(Supplier<Context> supplier) {
        Context ctx = supplier.get();
        EntityPlayerMP sender = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (sender != null && token != null) {
                PortalGunItem.handleSpawnerOperationConfirmation(sender, token, confirmed);
            }
        });
        ctx.setPacketHandled(true);
    }
}
