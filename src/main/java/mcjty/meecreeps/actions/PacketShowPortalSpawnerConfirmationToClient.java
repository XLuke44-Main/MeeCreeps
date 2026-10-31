package mcjty.meecreeps.actions;

import io.netty.buffer.ByteBuf;
import mcjty.lib.network.NetworkTools;
import mcjty.lib.thirteen.Context;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketShowPortalSpawnerConfirmationToClient implements IMessage {
    private String message;
    private String[] parameters;
    private UUID token;

    public PacketShowPortalSpawnerConfirmationToClient() {
    }

    public PacketShowPortalSpawnerConfirmationToClient(ByteBuf buf) {
        fromBytes(buf);
    }

    public PacketShowPortalSpawnerConfirmationToClient(String message, UUID token, String... parameters) {
        this.message = message;
        this.token = token;
        this.parameters = parameters;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        message = NetworkTools.readStringUTF8(buf);
        token = new UUID(buf.readLong(), buf.readLong());
        int size = buf.readInt();
        if (size < 0 || size > 16) throw new IllegalArgumentException("Invalid confirmation parameter count");
        parameters = new String[size];
        for (int i = 0; i < size; i++) parameters[i] = NetworkTools.readStringUTF8(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkTools.writeStringUTF8(buf, message == null ? "" : message);
        buf.writeLong(token == null ? 0L : token.getMostSignificantBits());
        buf.writeLong(token == null ? 0L : token.getLeastSignificantBits());
        int size = parameters == null ? 0 : Math.min(parameters.length, 16);
        buf.writeInt(size);
        for (int i = 0; i < size; i++) NetworkTools.writeStringUTF8(buf, parameters[i] == null ? "" : parameters[i]);
    }

    public void handle(Supplier<Context> supplier) {
        Context ctx = supplier.get();
        ctx.enqueueWork(() -> ClientActionManager.showConfirmation(message, token, parameters));
        ctx.setPacketHandled(true);
    }
}
