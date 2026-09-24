package mcjty.meecreeps.actions;

import io.netty.buffer.ByteBuf;
import mcjty.lib.network.NetworkTools;
import mcjty.lib.thirteen.Context;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.function.Supplier;

public class PacketPerformAction implements IMessage {

    private int id;
    private MeeCreepActionType type;
    private String furtherQuestionId;

    private static final int MAX_ACTION_ID_LENGTH = 128;
    private static final int MAX_QUESTION_ID_LENGTH = 128;

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

    @Override
    public void fromBytes(ByteBuf buf) {
        id = buf.readInt();
        String actionId = readBoundedString(buf, MAX_ACTION_ID_LENGTH);
        type = actionId == null ? null : new MeeCreepActionType(actionId);
        furtherQuestionId = readBoundedString(buf, MAX_QUESTION_ID_LENGTH);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(id);
        NetworkTools.writeStringUTF8(buf, type.getId());
        NetworkTools.writeStringUTF8(buf, furtherQuestionId);
    }

    public PacketPerformAction() {
    }

    public PacketPerformAction(ByteBuf buf) {
        fromBytes(buf);
    }

    public PacketPerformAction(ActionOptions options, MeeCreepActionType type, String furtherQuestionId) {
        this.id = options.getActionId();
        this.type = type;
        this.furtherQuestionId = furtherQuestionId;
    }

    public void handle(Supplier<Context> supplier) {
        Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            if (type != null) {
                ServerActionManager.getManager().performPlayerAction(ctx.getSender(), id, type, furtherQuestionId);
            }
        });
        ctx.setPacketHandled(true);
    }
}
