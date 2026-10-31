package mcjty.meecreeps.items;

import io.netty.buffer.ByteBuf;
import mcjty.lib.thirteen.Context;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.function.Supplier;

public class PacketTogglePortalGunMode implements IMessage {

    public PacketTogglePortalGunMode() {
    }

    public PacketTogglePortalGunMode(ByteBuf buf) {
        fromBytes(buf);
    }

    @Override
    public void fromBytes(ByteBuf buf) {

    }

    @Override
    public void toBytes(ByteBuf buf) {

    }

    public void handle(Supplier<Context> supplier) {
        Context context = supplier.get();
        EntityPlayerMP player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null || player.openContainer != player.inventoryContainer) return;
            ItemStack gun = PortalGunItem.getGun(player);
            if (gun.isEmpty()) return;

            boolean dualLinked = PortalGunItem.toggleDualLinkedMode(gun);
            player.inventory.markDirty();
            PortalGunItem.sendPortalGunTranslation(player, gun, dualLinked
                    ? "message.meecreeps.portal_mode_dual_linked"
                    : "message.meecreeps.portal_mode_normal");
        });
        context.setPacketHandled(true);
    }
}
